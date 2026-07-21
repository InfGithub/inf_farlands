# AI 快速上手指南

读完本文档后，你应该能够立即开始工作，无需用户重复解释项目结构。

---

## 1. 强制阅读清单

按顺序，一条不落：

| 顺序 | 文件 | 内容 |
|------|------|------|
| 1 | `doc/specification.md` | 开发规范（15 条），**必须逐条遵守** |
| 2 | `doc/status.md` | 项目状态、代码清单、有效范围、已知问题 |
| 3 | `doc/bugs.md` | 已知 bug 和限制 |
| 4 | `doc/cynicism.md` | 前代 AI 的完整尸检报告 |
| 5 | `doc/cynicism2.md` | 续集：同一个模式重演 |
| 6 | `doc/cynicism2-sequel.md` | 再续：读完尸检报告后完美复刻 |
| 7 | `doc/cynicism2-sequel2.md` | 终章：精准一击毙命 |
| 8 | `doc/cynicism3.md` | **2026-07-21 你的直接前任**——一天之内犯下振幅反写、用方法名替代源码、beta 就是平的谎言、坐标级别混淆等全部错误。读完再动代码。 |

这 5 篇 `cynicism*.md` 不是消遣。它们记录了多次灾难性失败的完整因果链。跳过它们 → 你会在 30 分钟内把其中至少一章重演一遍。

不需要读 `doc/blockpos_aslong_26_12_26.md` —— BlockPos.asLong() 位布局参考，仅在分析 block 坐标截断问题时需要。

---

## 2. 反编译源码在哪里

### NeoForge 1.21.1

```
build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/transformSource/transformed/
```

Minecraft 原版类按标准包路径存放：
- `net/minecraft/server/level/WorldGenRegion.java`
- `net/minecraft/world/level/chunk/ChunkGenerator.java`
- `net/minecraft/world/level/levelgen/NoiseChunk.java`
- `net/minecraft/world/level/levelgen/DensityFunction.java`
- `net/minecraft/world/level/levelgen/DensityFunctions.java`
- `net/minecraft/world/phys/shapes/VoxelShape.java`

NeoForge 注入的类（`GenerationChunkHolder`、`ChunkStep`）在同一目录。

**重要**：`clean build` 后此目录被清空，需先 `build` 重新生成。

**读取方式**：`list_directory` 对深层路径可能返回空。直接用 `read_file` 读完整 Windows 路径。

### Beta 1.7.3 参考源码

```
repos/mc_b1.7.3_release/1.7.3-LTS/src/minecraft/net/minecraft/src/
```

平铺单包，无泛型无 lambda。关键地形文件：
- `NoiseGeneratorOctaves.java` — 八度叠加（振幅 = 1/freq，低频大振幅）
- `NoiseGeneratorPerlin.java` — 3D Perlin 原子，`(int)` 截断产生 Far Lands
- `ChunkProviderGenerate.java` — 地形入口，`func_4061_a` + `generateTerrain`

---

## 3. 日志和崩溃报告

| 文件 | 用途 |
|------|------|
| `run/client/logs/latest.log` | 启动日志、崩溃栈、诊断输出 |
| `run/client/logs/debug.log` | Mixin 注册日志、详细错误 |
| `run/client/crash-reports/crash-*.txt` | JVM 崩溃详情 |
| `stderr.txt`、`the_log.txt`（项目根目录） | Gradle 构建失败时查看 |

---

## 4. 代码结构

### 目录树

```
src/main/java/com/inf/farlands/
├── InfFarlands.java              — Mod 入口：Config + Payload + 事件 + tickCounter
├── Config.java                   — 3 配置项：betaTerrain, borderAbsoluteMax
├── FarlandsCommand.java          — /farlands 命令（border/tp/dump）
├── FarLandsConstants.java        — MAX_CHUNK=134_217_726, MAX_BLOCK
├── HashUtil.java                 — 3int side-channel + 反射 getDataLayer + TTL trimLookups
├── IntBlockPos.java              — block 坐标载体 + volatile lastAccess
├── IntSectionPos.java            — section 坐标载体 + volatile lastAccess
├── PosKey.java / SectionKey.java — side-channel key records
├── agent/
│   └── WorldGenAgent.java        — BoundingBox.<init> ASM min/max 交换
├── network/
│   └── FarLandsSectionBlocksUpdatePacket.java — 自定义批量方块更新包
├── terrain/                      — beta 地形引擎
│   ├── BetaTerrain.java          — 单例 + seed init + ThreadLocal<ChunkAccess>
│   ├── BetaTerrainNoise.java     — 5 通道 ImprovedNoise，振幅 /freq
│   ├── BetaTerrainFormula.java   — func_4061_a 移植，cell 坐标，heightScale=256/65536
│   └── BetaDensityFunction.java  — DensityFunction.SimpleFunction，接入密度函数链
└── mixin/                        — 44 Mixin（完整清单见 status.md）
    ├── BlockPosMixin             — asLong 3int hash
    ├── SectionPosMixin           — asLong hash + x/y/z 解包 + TTL lastAccess
    ├── NoiseChunkMixin           — @Redirect finalDensity → BetaDensityFunction
    ├── NoiseBasedChunkGeneratorMixin — spawnOriginalMobs skip + chunk ThreadLocal
    ├── SoundEngineMixin          — listener-relative 坐标
    ├── ListenerMixin             — listener 原点化
    ├── EntityBoundSoundInstanceMixin 等 6 个 — float→double 精度
    ├── VoxelShapeMixin           — 自适应浮点 epsilon (collideX)
    ├── ClientPacketListenerMixin — 极端坐标 block update 过滤
    ├── LevelBoundsMixin          — world bounds 扩展
    └── ... plus 30 more
```

### 资源

```
src/main/resources/
├── inf_farlands.mixins.json      — 44 Mixin 注册
├── data/inf_farlands/advancement/ — 4 成就 JSON
└── META-INF/neoforge.mods.toml
```

---

## 5. 核心系统速览

### 5.1 3int 改造

`BlockPos.asLong()` / `SectionPos.asLong()` 用自定义 hash 替代原版 26+12+26 位截断。坐标存 `ConcurrentHashMap` side-channel。

**TTL 清理**（关键）：`IntBlockPos.lastAccess` / `IntSectionPos.lastAccess` 每 `x()/y()/z()` 访问刷新为 `InfFarlands.tickCounter`。`trimLookups(tickCounter)` 每 200 tick 清 600 tick 未访问条目。**不能随机删**——活跃 section 依赖 side-channel 反查坐标。

### 5.2 Beta 地形

`NoiseChunkMixin` → `@Redirect NoiseRouter.finalDensity()` → `BetaDensityFunction` → `DensityFunctions.add(beta, BeardifierMarker)` → `cacheAllInCell` → `fillAllDirectly`。Cell 级计算，原版性能。

`BetaTerrainNoise`：5 通道 ImprovedNoise。八度叠加公式 `/ freq`（振幅 = 1/freq = 2^octave，低频大振幅）。通道 3/4 是 2D 温度/湿度噪声。

`BetaTerrainFormula`：移植 `func_4061_a`。Cell 坐标（blockX/4, blockY/8, blockZ/4）。`tFactor = biomeFactor × noiseFactor(clamp) + 0.5`。`heightScale = 256/65536`（固定 256 格）。

`BetaDensityFunction`：实现 `DensityFunction.SimpleFunction`。`compute()` 从 `BetaTerrain.getCurrentChunk()` 取 biome 温度/湿度。

### 5.3 音频

`SoundEngineMixin` + `ListenerMixin`：源坐标 listener-relative，listener 原点。实体 + 攻击声音走 `EntityMixin`/`PlayerMixin` → `ClientboundSoundEntityPacket`（避 `ClientboundSoundPacket` int 溢出）。6 个 `*SoundInstanceMixin` 修 `(double)((float)entity.getX())` 精度。

### 5.4 碰撞

`VoxelShapeMixin.collideX`：6 个 `findIndex` 的 `1.0E-7` epsilon → `Math.max(1.0E-7, Math.ulp(base))`。

---

## 6. 关键数字

| 符号 | 值 | 用途 |
|------|-----|------|
| `MAX_CHUNK` | 134,217,726 | 最后可用 chunk（maxBlockX 不溢出 int） |
| `MAX_BLOCK` | 2,147,483,616 | 最后可用 block 坐标 |
| `heightScale` | 256/65536 | beta 噪声 → block 高度缩放 |
| `TTL` | 600 tick | side-channel 条目过期时间 |
| `TRIM_INTERVAL` | 200 tick | trimLookups 调用间隔 |

---

## 7. 构建命令

```bash
# 构建（不要 clean build——会清空反编译源码）
./gradlew build --no-daemon -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897

# 运行客户端
./gradlew runClient --no-configuration-cache -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897
```

输出 jar：`build/libs/inf_farlands-1.0.2.jar`

---

## 8. Git 规则

1. **默认推内网** `origin`（`192.168.1.75`）。只有用户明确说"推到 github"才推 `github`。
2. **每次 commit 用 `git add .`**（规范 #9），不容忍精确文件名。
3. 新增 Mixin → 同时 add `.java` + `mixins.json`。Config 加字段 → add `Config.java` + 所有引用方。
4. 一行流：`git add . && GIT_EDITOR=true git commit -m "..." && git push origin master`
