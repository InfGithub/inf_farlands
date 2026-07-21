# Inf's Farlands — 项目状态

**Last updated:** 2026-07-21

## 概述

Inf's Farlands 是一个 NeoForge 1.21.1 mod，在现代 Minecraft 中恢复 beta 1.7.3 风格的边境之地（Far Lands）地形异常。通过 3int 改造，`BlockPos.asLong()` 和 `SectionPos.asLong()` 的 26+12+26 位截断已被消除，33M 分界线不再存在。

**版本：** 1.0.2 | **协议：** MIT | **包名：** `com.inf.farlands`

## 有效范围

| 范围 | 地形 | 实体 | 光照 | 方块操作 | 渲染 | 声音 |
| --- | --- | --- | --- | --- | --- | --- |
| 0–33.5M | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 33.5M–21.4B | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

- 33.5M 分界线已通过 3int 改造消除
- chunk ≥ 134,217,727 跳过地物/结构/地表（`getMinBlockX()` int 溢出硬限制）
- `FarLandsConstants.MAX_CHUNK = 134_217_726` 是最后一个完全可用的 chunk
- 声音支持全范围（OpenAL listener-relative + 实体包 precision fix）
- 碰撞检测支持全范围（自适应浮点 epsilon）

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enableFarLands` | true | 完全启用/禁用 Far Lands |
| `octaveAccelThreshold` | 12 | 噪声加速起始八度 (0–30) |
| `accelMultiplier` | 4.0 | 加速倍率 (1.0–16.0) |
| `borderAbsoluteMax` | 2147483647 | 世界边界最大值 |
| `verticalViewDistance` | 8 | 垂直加载半径（cube，1 cube = 16 格） |

## 代码清单

### 3int 改造核心

| 文件 | 类型 | 作用 |
|------|------|------|
| `FarLandsConstants.java` | 常量 | `MAX_CHUNK` / `MAX_BLOCK` |
| `PosKey.java` | record | Side-channel block key |
| `SectionKey.java` | record | Side-channel section key |
| `IntBlockPos.java` | 载体 | int block 坐标 |
| `IntSectionPos.java` | 载体 | int section 坐标 |
| `HashUtil.java` | 工具 | hash + side-channel maps |
| `FarLandsSectionBlocksUpdatePacket.java` | 网络 | 自定义批量方块更新包 |

### Mixin (44)

#### 3int 核心改造

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `BlockPosMixin` | `BlockPos` | `@Overwrite` ×8 | asLong hash + 反解 + offset + of + flatIndex |
| `SectionPosMixin` | `SectionPos` | `@Overwrite` ×14 | asLong hash + 反解 + offset + blockToSection + getZeroNode + relativeToBlock |
| `MutableBlockPosMixin` | `MutableBlockPos` | `@Overwrite` | asLong 实例 side-channel |
| `ChunkPosMixin` | `ChunkPos` | `@Overwrite` ×2 | getMinBlockX/Z long 防溢出 |
| `EntitySectionStorageMixin` | `EntitySectionStorage` | `@Overwrite` | forEach 用 side-channel |
| `LayerLightSectionStorageMixin` | `LayerLightSectionStorage` | `@Overwrite` ×2 | getStoredLevel/setStoredLevel side-channel |
| `LightEngineMixin` | `LightEngine` | `@Overwrite` ×4 | checkBlock/checkNode + runLightUpdates 反射 |
| `FriendlyByteBufMixin` | `FriendlyByteBuf` | `@Overwrite` ×4 | 3 ints BlockPos/SectionPos |
| `ChunkHolderMixin` | `ChunkHolder` | `@Redirect` | 批量更新替换 + 极端坐标跳过 |
| `ClientPacketListenerMixin` | `ClientPacketListener` | `@Inject` | 极端坐标 block update 过滤 |
| `InfFarlands.java` | Mod 入口 | Payload handler | 自定义包注册 + 客户端处理 |

#### Far Lands 地形

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `PerlinNoiseMixin` | `PerlinNoise` | `@Overwrite` ×2 + `@Inject` | 边境之地噪声 |
| `WorldBorderMixin` | `WorldBorder` | `@Inject` | 边界扩展到 INT_MAX |
| `MinecraftServerMixin` | `MinecraftServer` | `@Overwrite` | `getAbsoluteMaxWorldSize` → 配置 |

#### 坐标/移动

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `PlayerMixin` | `Player` | `@Redirect` ×3 | 移除 `tick()` 中 X/Z clamp + 攻击音效改为实体包 |
| `EntityMixin` | `Entity` | `@Overwrite` + `@Inject` + `@Redirect` ×3 | absMoveTo 去钳制 + checkInsideBlocks skip + load 去钳制 + 声音改实体包 |
| `ServerGamePacketListenerImplMixin` | `ServerGamePacketListenerImpl` | `@Inject cancellable` | 绕过 clampHorizontal |
| `BoundingBoxMixin` | `BoundingBox` | `@Inject cancellable` | moved() 溢出返回 self |
| `LevelMixin` | `Level` | `@ModifyVariable` ×2 | 环面拓扑：溢出 chunk 坐标映射 |
| `LevelBoundsMixin` | `Level` | `@Overwrite` | isInWorldBoundsHorizontal 30M→配置 |

#### 碰撞检测

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `VoxelShapeMixin` | `VoxelShape` | `@ModifyArg` ×6 | `collideX` 自适应浮点 epsilon（修复高坐标墙碰撞抖动） |

#### 音频

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `ListenerMixin` | `Listener` | `@ModifyVariable` | listener 置原点（配合相对坐标） |
| `SoundEngineMixin` | `SoundEngine` | `@ModifyVariable` ×4 | 音源坐标转为 listener-relative |
| `EntityBoundSoundInstanceMixin` | `EntityBoundSoundInstance` | `@Inject` ×2 | 消除 `(double)((float)entity.getX())` 精度损失 |
| `BeeSoundInstanceMixin` | `BeeSoundInstance` | `@Inject` ×2 | 同上 |
| `MinecartSoundInstanceMixin` | `MinecartSoundInstance` | `@Inject` ×2 | 同上 |
| `ElytraOnPlayerSoundInstanceMixin` | `ElytraOnPlayerSoundInstance` | `@Inject` | 同上 |
| `GuardianAttackSoundInstanceMixin` | `GuardianAttackSoundInstance` | `@Inject` | 同上 |
| `SnifferSoundInstanceMixin` | `SnifferSoundInstance` | `@Inject` | 同上 |

#### 世界生成保护

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `WorldGenRegionMixin` | `WorldGenRegion` | `@Overwrite` ×2 | getChunk fallback + getCurrentDifficultyAt |
| `ChunkGeneratorMixin` | `ChunkGenerator` | `@Inject cancellable` | 跳过溢出 FEATURES |
| `NoiseBasedChunkGeneratorBuildSurfaceMixin` | `NoiseBasedChunkGenerator` | `@Inject cancellable` | 跳过溢出 SURFACE |
| `NoiseBasedChunkGeneratorMixin` | `NoiseBasedChunkGenerator` | `@Inject cancellable` | 阈值跳过 SPAWN |
| `OreFeatureMixin` | `OreFeature` | `@Inject cancellable` | 跳过溢出矿石 |
| `TreeFeatureMixin` | `TreeFeature` | `@Redirect` ×2 | updateLeaves safeFill + safeIsFull |
| `MineshaftPiecesMixin` | `MineshaftPieces$MineShaftPiece` | `@Overwrite` | BoundingBox 溢出检查 |
| `MineshaftPiecesStaticMixin` | `MineshaftPieces` | `@Inject` | 递归深度限制 |
| `MineshaftStructureMixin` | `MineshaftStructure` | `@Inject cancellable` | 跳过溢出矿井 |
| `AquiferMixin` | `Aquifer.NoiseBasedAquifer` | `@Overwrite` | long 索引防溢出 |

#### 杂项

| 文件 | 目标 | 方式 | 作用 |
|------|------|------|------|
| `PathNavigationMixin` | `GroundPathNavigation` | `@Inject cancellable` | 跳过溢出坐标寻路 |
| `ForceLoadCommandMixin` | `ForceLoadCommand` | `@ModifyConstant` ×2 | /forceload 30M→配置 |

### Agent (1)

| 文件 | 目标 | 方式 | 作用 |
| --- | --- | --- | --- |
| `WorldGenAgent` → `BoundingBoxTransformer` | `BoundingBox.<init>` | ASM visitCode 交换 | 交换颠倒的 min/max 坐标 |

### 其他

| 文件 | 作用 |
|------|------|
| `InfFarlands.java` | Mod 入口，注册 Config + Payload + HashUtil trim |
| `Config.java` | 5 个配置项 |
| `FarlandsCommand.java` | `/farlands` 命令 |
| `BiomeNoiseFlag.java` | ThreadLocal 标记，生物群系查询时跳过 Far Lands 噪声 |

## 命令

| 命令 | 作用 |
|------|------|
| `/farlands` | 显示配置和边界信息 |
| `/farlands border set <size>` | 设置世界边界大小 |
| `/farlands border get` | 查看当前边界 |
| `/farlands tp <x> <y> <z>` | 无限制传送 |
| `/farlands dump` | 显示玩家坐标和边界详情 |

## 兼容性

| Mod | 状态 | 备注 |
| --- | --- | --- |
| Noisiumed 3.0.2-neoforge | ✅ 兼容 | 已测试 |
| C2ME 0.4.0-alpha.0.112 | ❌ 不可调和 | 11 种方案全败 |
| spark 1.10.124 | ✅ 兼容 | localRuntime 依赖 |

## 已知问题

**日期：** 2026-07-21

### 已修复 ✅

1. **重进世界回弹** — `EntityMixin` 跳过 `Mth.clamp` in `load()`
2. **生物寻路崩溃** — `PathNavigationMixin` 阈值跳过
3. **光照全暗** — 3int 光照引擎改造，null DataLayer 返回 15
4. **OpenAL >268M 静音** — `SoundEngineMixin` + `ListenerMixin` 相对坐标
5. **高坐标实体声音静音** — `EntityMixin` 实体包 + 6 个 `*SoundInstanceMixin` precision 修复
6. **高坐标攻击音效静音** — `PlayerMixin` 实体包
7. **高坐标墙碰撞客户端抖动** — `VoxelShapeMixin` 自适应浮点 epsilon
8. **振幅八度反写** — `BetaTerrainNoise.sample()` `/ freq` 替代 `* freq`
9. **3int side-channel 随机清理导致光照崩溃** — TTL tick 访问标记替代随机删一半
10. **tFactor 缺温度噪声调制和基线** — `BetaTerrainFormula` 补全

### 未修复 ❌

1. **高空陆地停止渲染** — 飞到 Y=190 后下方陆地（Y≈146）不渲染。触发阈值不固定。疑与 `CacheAllInCell` 的 Y cell 填充有关
2. **地下群系生成在地表** — 滴水石锥洞穴、繁茂洞穴等地表可见
3. **预加载世界时首批 chunk 是原版地形** — `LevelEvent.Load` 添加后待验证
4. **边境之地地形与 beta 1.7.3 仍有差异** — 公式已对齐，待全面验证

### 已知不修 ⚠️

1. **边界区块外圈透明** — 原版 bug，非本 mod 引入
2. **C2ME 不兼容** — 11 种方案全败，详见 `doc/C2ME_COMPATIBILITY_REPORT.md`
