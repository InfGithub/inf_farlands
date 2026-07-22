# root3Int — 消除 side-channel，BlockPos/SectionPos 纯 int 改造

## 改造模式（唯一规则）

对每一处 `long` 返回值和调用方，执行两条操作：

```
规则 1：method2（返回 long）→ 复制一份 method3（返回 BlockPos/SectionPos/ChunkPos）
规则 2：method1（调用 method2）→ @Overwrite，method2() 换成 method3()
```

method2 永远不动。当所有 method1 都换成了 method3，method2 无人调用，安全删除。

### 示例

```java
// 原版：
int method1() {
    long value = method2();       // method2 返回 long
    int x = BlockPos.getX(value); // 消费 long
    // ...
}

long method2() {
    return someLong;
}

// 改造后：
// 规则 1 — 复制 method3，返回 BlockPos：
BlockPos method3() {
    return someBlockPos;  // 与 method2 语义等价
}

// 规则 2 — @Overwrite method1，method2() → method3()：
@Overwrite
int method1() {
    BlockPos value = method3();  // 只改这一行
    int x = value.getX();        // 现在直接从对象读字段
    // ...
}
```

### 类别一览

| 场景 | method2 | method3 | method1 在哪 |
|------|---------|---------|-------------|
| BlockPos 自身方法 | `BlockPos.asLong(x,y,z)` | `new BlockPos(x,y,z)` | `offset(long,dir)` 等方法体 |
| MutableBlockPos | `MutableBlockPos.asLong()` | `this`（自身就是 BlockPos） | MutableBlockPos 内部方法 |
| 序列化读 | `FriendlyByteBuf.readLong()` | `readInt()×3 → new BlockPos(...)` | `readBlockPos()` |
| 序列化写 | `FriendlyByteBuf.writeLong()` | `writeInt(x); writeInt(y); writeInt(z)` | `writeBlockPos(BlockPos)` |
| NBT 读 | `CompoundTag.getLong()` | `getInt("X/Y/Z") → new BlockPos(...)` | `NbtUtils.readBlockPos()` |
| NBT 写 | `CompoundTag.putLong()` | `putInt("X/Y/Z")` | `NbtUtils.writeBlockPos()` |
| Map 迭代（光照/实体） | `LongAVLTreeSet.nextLong()` | 查 sectionLookup → `SectionPos.of(...)`（改造结束后删） | 迭代消费方法 |
| 结构生成 | `BlockPos.offset(long,dir)` | `new BlockPos(this.x+dx, this.y+dy, this.z+dz)` | 结构生成调用方 |
| ChunkPos | `ChunkPos.toLong()` | `new ChunkPos(x,z)` | `getMinBlockX(long)` 等方法 |

方法2 是 `LongAVLTreeSet.nextLong()` 时，方法3 需查 side-channel。这是唯一需要 side-channel 的边界——改造结束后方法3 随 side-channel 一起删除。

---

## 阶段 1：序列化层

### 1.1 FriendlyByteBuf（4 个方法，不可拆分）

**文件**：`FriendlyByteBufMixin.java`

```java
// 规则 1 — method3（3int 版本）：
void writeBlockPos3int(BlockPos pos) {
    writeInt(pos.getX()); writeInt(pos.getY()); writeInt(pos.getZ());
}
BlockPos readBlockPos3int() {
    return new BlockPos(readInt(), readInt(), readInt());
}
void writeSectionPos3int(SectionPos pos) {
    writeInt(pos.x()); writeInt(pos.y()); writeInt(pos.z());
}
SectionPos readSectionPos3int() {
    return SectionPos.of(readInt(), readInt(), readInt());
}

// 规则 2 — method1 @Overwrite：
@Overwrite void writeBlockPos(BlockPos pos) { writeBlockPos3int(pos); }
@Overwrite BlockPos readBlockPos() { return readBlockPos3int(); }
@Overwrite void writeSectionPos(SectionPos pos) { writeSectionPos3int(pos); }
@Overwrite SectionPos readSectionPos() { return readSectionPos3int(); }
```

**验收**：网络包中 BlockPos/SectionPos 使用 3int。客户端-服务端正常。

### 1.2 NBT 序列化

**文件**：NbtUtils Mixin

```java
@Overwrite
void writeBlockPos(CompoundTag tag, BlockPos pos) {
    tag.putInt("X", pos.getX()); tag.putInt("Y", pos.getY()); tag.putInt("Z", pos.getZ());
}
@Overwrite
BlockPos readBlockPos(CompoundTag tag) {
    return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
}
```

### 1.3 STREAM_CODEC

`BlockPos.STREAM_CODEC` / `SectionPos.STREAM_CODEC` → 3int codec。

**验收**：新存档读写正常。老存档不兼容（可接受）。

---

## 阶段 2：BlockPos 调用点（32 个）

对每个调用点执行规则 1+2。

| 优先级 | 目标 | 估计 | method2 | method3 | 验证 |
|--------|------|------|---------|---------|------|
| 1 | BlockPos 自身方法（offset 等） | ~8 | `BlockPos.asLong()` | `this`（实例自身） | 正常坐标功能不变 |
| 2 | MutableBlockPos | ~3 | `MutableBlockPos.asLong()` | `this` | 同上 |
| 3 | 序列化消费者 | ~10 | `FriendlyByteBuf.readBlockPos()` | 阶段 1 已改，直接有 BlockPos | 阶段 1 完成后自动覆盖 |
| 4 | Map 迭代 + getX(long) | ~5 | `LongIterator.nextLong()` | 查 blockLookup | 正常坐标功能不变 |
| 5 | 结构生成 offset 链 | ~6 | `BlockPos.offset(long, dir)` | `new BlockPos(x+dx, y+dy, z+dz)` | 结构生成不崩 |

同优先级可独立验证、独立 commit。

**验收**：32 处全部覆盖。`BlockPos.getX/getY/getZ(long)` 不再被原版调用。

---

## 阶段 3：SectionPos 调用点（19 个）

高优先级目标：

| 文件 | method1 | method2 | method3 |
|------|---------|---------|---------|
| `LayerLightSectionStorage` | `updateSectionStatus` | `LongAVLTreeSet.nextLong()` | 查 sectionLookup → `SectionPos.of(x,y,z)` |
| `LayerLightSectionStorage` | `markSectionAndNeighborsAsAffected` | 同上 | 同上 |
| `EntitySectionStorage` | `forEachAccessibleNonEmptySection` | `LongIterator.nextLong()` | 同上（同时提供 X-index 坐标） |
| `EntitySectionStorage` | `getChunkSections` | 同上 | 同上 |
| `SkyLightSectionStorage` | 数处 | 同上 | 同上 |
| `SectionStorage` | `readColumn` | `readLong()` | 同上 |

**EntitySectionStorage 特殊处理**：X-index 需要 X 坐标。method3 中从 sectionLookup 获取 IntSectionPos，同时传给 X-index 和下游 method1。

**验收**：19 处全部覆盖。`SectionPos.x/y/z(long)` 不再被原版调用。

---

## 阶段 4：ChunkPos 调用点（12 个）

`ChunkPos.getX/getZ(long)` → 改为 `chunk.x / chunk.z`。method2 = `ChunkPos.toLong()`。

**验收**：12 处全部覆盖。

---

## 阶段 5：清理

所有原版调用方不再经过 method2 时：

### 5.1 删文件

- `IntBlockPos.java`
- `IntSectionPos.java`
- `PosKey.java`
- `SectionKey.java`

### 5.2 HashUtil 清理

- 删 `blockLookup`
- 删 `sectionLookup`
- 删 `trimLookups`
- 删 `callGetDataLayer` / `callGetDataLayerToWrite`（如果光照不再需要）
- 保留 `hashPos` / `hashSection`（`asLong()` 仍需 hash）

### 5.3 InfFarlands 清理

- 删 `tickCounter`、`getServerTickCount()`、`TRIM_INTERVAL`、trimLookups 调用

### 5.4 Mixin @Overwrite 删除

| Mixin | 删 |
|-------|-----|
| `BlockPosMixin` | `getX(long)`, `getY(long)`, `getZ(long)`, `offset(long,dir)`, `offset(long,dx,dy,dz)` |
| `SectionPosMixin` | `x(long)`, `y(long)`, `z(long)`, `offset(long)` |
| `ChunkPosMixin` | `getX(long)`, `getZ(long)` |
| `MutableBlockPosMixin` | `asLong()` 中 blockLookup.put 逻辑 |

### 5.5 光照 Mixin 简化

| 文件 | 变更 |
|------|------|
| `LightEngineMixin` | B 策略可删（TTL 消失）。`checkBlock` 不再 `blockLookup.put` |
| `LayerLightSectionStorageMixin` | 不需 side-channel 反查 |

### 5.6 `mixins.json`

移除已删条目。

---

## 阶段 6：验收

### 正常坐标
- [ ] 构建通过
- [ ] 地形正常、光照正常、实体正常

### 极端坐标
- [ ] 33.5M+ 不崩
- [ ] 12.5M Far Lands 正常
- [ ] 2.1B 正常移动

### 性能
- [ ] 启动/稳态内存下降
- [ ] spark profile 中 CHM / IntBlockPos 消失
- [ ] 无 TTL race crash

### 存档
- [ ] 新存档 3int NBT
- [ ] 重启后坐标不丢失

不要求兼容老存档。
