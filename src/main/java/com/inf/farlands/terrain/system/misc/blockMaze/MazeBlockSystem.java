package com.inf.farlands.terrain.system.misc.blockMaze;

import com.inf.farlands.util.HashUtil;
import com.inf.farlands.util.WorldBounds;
import com.inf.farlands.terrain.BlockSystem;
import com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3.LegacyPerlinNoise;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 3D 确定性单元迷宫逐块几何系统。
 *
 * <p>几何：迷宫格 = 3×3×5 通道块（XZ 3、Y 5），格间 1 块厚隔断，周期 XZ=4 / Y=6。
 * 可玩范围 [-MAX_BLOCK, MAX_BLOCK-1] 内铺满迷宫；外壳只在两对角开一个口：
 * 入口 = 格 (-P,-P,-P) 腔（x=y=z=-2.14B），出口 = 格 (P,P,P) 腔（x=y=z=2.14B）。
 *
 * <p>确定性（相对世界种子）：单元 = 8³ 格，单元种子 = hashPos(seed, ux,uy,uz)，
 * 单元内迭代 DFS 在"有效格"（格块范围完全在可玩范围）上生成完美迷宫（树）；
 * 单元间门 = C1 稀疏规则（3D 网格模拟验证连通）：X 面恒开（主干）、Y 面 ⟺ ux 偶、
 * Z 面 ⟺ ux 偶 && uy 偶——门数 6 → ~3.4/单元，死路显著增多（单元边界 Y/Z 多为墙）；
 * 墙材质 = LegacyPerlinNoise 低频分区（黑曜石/哭泣黑曜石）；
 * 通道顶部每 4 格 1 盏海晶灯；垂直打通井壁挂藤蔓（可攀爬，CLIMBABLE tag）。
 *
 * <p>壳封口（保证外部仅两口）：
 *   R2 格部分超界（块范围跨出可玩范围）→ 该格界内块全墙；
 *   R3 块在最外层（任一坐标 == ±可玩边界）→ 墙（除腔）。
 * 优先级 R1 腔 > R2 > R3 > R4 隔断 > R5 通道。
 *
 * <p>流体：空气 fluidPicker + 空气 aquifer（仿 VOID）——迷宫不生成水。
 * 有效格判定用 WorldBounds 固定常量，不用 Config（防配置漂移迷宫形状）。
 */
@SuppressWarnings("null")
public final class MazeBlockSystem implements BlockSystem {

    // ---- 几何常量 ----

    private static final int CELL_XZ = 3; // 通道水平宽度（块）
    private static final int CELL_Y = 5; // 通道垂直高度（块）
    private static final int WALL = 1; // 隔断墙厚（块）
    private static final int PERIOD_XZ = CELL_XZ + WALL; // 4
    private static final int PERIOD_Y = CELL_Y + WALL; // 6
    private static final int UNIT = 8; // 单元边长（格）
    private static final int UNIT_MASK = UNIT - 1;
    private static final int UNIT_BITS = 3; // log2(UNIT)

    private static final int MIN_B = WorldBounds.MIN_PLAYABLE_BLOCK; // -2147483632
    private static final int MAX_B = WorldBounds.MAX_PLAYABLE_BLOCK; // 2147483631

    private static final double MAT_FREQ = 1.0 / 32.0; // 材质噪声低频
    private static final long MAT_SALT = 0x9E3779B97F4A7C15L;
    /** 悬空藤蔓链向上扫描上限（格数）：找链上第一个有壁格的方向。 */
    private static final int MAX_VINE_SCAN = 64;
    /** 门面方向盐，code：0=+X/-X、1=+Y/-Y、2=+Z/-Z。 */
    private static final long[] DIR_SALT = { 0x1111111111111111L, 0x2222222222222222L, 0x3333333333333333L };

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState CRYING_OBSIDIAN = Blocks.CRYING_OBSIDIAN.defaultBlockState();
    private static final BlockState SEA_LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    /** 藤蔓状态：墙在 -Z/-X/+X/+Z 侧分别 = NORTH/WEST/EAST/SOUTH（附着面朝外）。 */
    private static final BlockState VINE_NORTH = Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true);
    private static final BlockState VINE_WEST = Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true);
    private static final BlockState VINE_EAST = Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true);
    private static final BlockState VINE_SOUTH = Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true);

    // ---- 单元数据 ----

    /** 一个单元 8³ 格的迷宫数据：有效格 + 三方向隔断（true = 墙，树边/门 = false）。 */
    private static final class MazeUnit {
        final boolean[] valid = new boolean[512];
        final boolean[] wallX = new boolean[512]; // 格 +X 隔断
        final boolean[] wallY = new boolean[512]; // 格 +Y 隔断
        final boolean[] wallZ = new boolean[512]; // 格 +Z 隔断

        {
            // 默认全墙
            java.util.Arrays.fill(wallX, true);
            java.util.Arrays.fill(wallY, true);
            java.util.Arrays.fill(wallZ, true);
        }
    }

    private static final class UnitState {
        int ux = Integer.MIN_VALUE;
        int uy;
        int uz;
        MazeUnit unit;
    }

    /** fillBlock 多线程（genPool）逐格调用，ThreadLocal 缓存当前单元，命中 O(1)。 */
    private static final ThreadLocal<UnitState> TL = ThreadLocal.withInitial(UnitState::new);

    private static int idx(int lx, int ly, int lz) {
        return (lx << (UNIT_BITS * 2)) | (ly << UNIT_BITS) | lz;
    }

    /** 格是否完全在可玩范围（块范围 [g*period, g*period+cell] 全在界）。WorldBounds 常量，防配置漂移。 */
    private static boolean fullyInWorld(int gx, int gy, int gz) {
        return (long) gx * PERIOD_XZ >= MIN_B && (long) gx * PERIOD_XZ + CELL_XZ <= MAX_B
                && (long) gy * PERIOD_Y >= MIN_B && (long) gy * PERIOD_Y + CELL_Y <= MAX_B
                && (long) gz * PERIOD_XZ >= MIN_B && (long) gz * PERIOD_XZ + CELL_XZ <= MAX_B;
    }

    // ---- 入口/出口腔（R1）：唯一两处壳开口 ----

    /** 入口腔：x∈[MIN,MIN+2], y∈[MIN+1,MIN+3], z∈[MIN,MIN+2]。
     *  y=MIN 为地板（R3 墙）；y=MIN+3 行为固定打通（格 -357913939 的 ry==5 隔断，
     *  通向有效树节点格 (gx,-357913938,gz)）。洞口（-X 面）= 2 高 3 宽，玩家可进。 */
    private static boolean inEntrance(int x, int y, int z) {
        return x >= MIN_B && x <= MIN_B + 2
                && y >= MIN_B + 1 && y <= MIN_B + 3
                && z >= MIN_B && z <= MIN_B + 2;
    }

    /** 出口腔：x∈[MAX-3,MAX], y∈[MAX-4,MAX], z∈[MAX-2,MAX]。
     *  y=MAX-4 行为固定打通（格 357913937 的 ry==5 隔断，通向有效树节点格 (gx,357913937,gz)）；
     *  洞 4×5×3，玩家从迷宫经固定打通进腔 → +X 出世界。 */
    private static boolean inExit(int x, int y, int z) {
        return x >= MAX_B - 3 && x <= MAX_B
                && y >= MAX_B - 4 && y <= MAX_B
                && z >= MAX_B - 2 && z <= MAX_B;
    }

    // ---- 状态 ----

    private long worldSeed;
    private volatile LegacyPerlinNoise matNoise;

    // ---- NoiseSystem ----

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 空气 picker：恒 AIR，防低 Y 迷宫通道被水淹没
        return (x, y, z) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void onLevelLoad(long seed) {
        this.worldSeed = seed;
        this.matNoise = null; // 种子变化 → 材质噪声重建（懒）
    }

    // ---- fillBlock：逐格判定 ----

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        // R1 腔：最高优先
        if (inEntrance(x, y, z) || inExit(x, y, z)) {
            return AIR;
        }
        int gx = Math.floorDiv(x, PERIOD_XZ);
        int gy = Math.floorDiv(y, PERIOD_Y);
        int gz = Math.floorDiv(z, PERIOD_XZ);
        int rx = Math.floorMod(x, PERIOD_XZ);
        int ry = Math.floorMod(y, PERIOD_Y);
        int rz = Math.floorMod(z, PERIOD_XZ);

        // R2 格部分超界 → 墙（该格界内块全墙）
        if (!fullyInWorld(gx, gy, gz)) {
            return wall(x, y, z);
        }
        // R3 最外层块 → 墙（壳封口）
        if (x == MIN_B || x == MAX_B || y == MIN_B || y == MAX_B || z == MIN_B || z == MAX_B) {
            return wall(x, y, z);
        }
        int ux = Math.floorDiv(gx, UNIT);
        int uy = Math.floorDiv(gy, UNIT);
        int uz = Math.floorDiv(gz, UNIT);
        MazeUnit u = getUnit(ux, uy, uz);
        int lx = gx & UNIT_MASK; // floorMod(gx, 8)（gx 负时 &7 = floorMod ✓）
        int ly = gy & UNIT_MASK;
        int lz = gz & UNIT_MASK;
        int id = idx(lx, ly, lz);

        // R4 隔断层（格间 1 块厚）：树边打通 / 门打通 → 空气，否则墙
        if (rx == PERIOD_XZ - 1) {
            if (ry <= CELL_Y - 1 && rz <= CELL_XZ - 1) { // X 隔断面 3×5
                return isOpen(gx, gy, gz, 1, 0, 0) ? AIR : wall(x, y, z);
            }
            return wall(x, y, z); // 角块
        }
        if (rz == PERIOD_XZ - 1) {
            if (rx <= CELL_XZ - 1 && ry <= CELL_Y - 1) { // Z 隔断面 3×5
                return isOpen(gx, gy, gz, 0, 0, 1) ? AIR : wall(x, y, z);
            }
            return wall(x, y, z);
        }
        if (ry == PERIOD_Y - 1) {
            if (rx <= CELL_XZ - 1 && rz <= CELL_XZ - 1) { // Y 隔断面 3×3
                if (isOpen(gx, gy, gz, 0, 1, 0)) {
                    // 垂直井口开口：贴壁列挂藤蔓（衔接隔断层 1 格，玩家可全高攀爬）
                    BlockState vine = vineAt(gx, gy, gz, rx, rz);
                    return vine != null ? vine : AIR;
                }
                // 天花板灯：嵌在未打通的 Y 隔断层中央（通道净高保持 5），每 2 格 1 盏。
                // 垂直井口（约 1/3 树边为 Y 向）处该层开口 → 灯被吞，实际间距 ~12 块仍无暗区。
                if (rx == 1 && rz == 1 && (gx & 1) == 0 && (gz & 1) == 0) {
                    return SEA_LANTERN;
                }
                return wall(x, y, z);
            }
            return wall(x, y, z);
        }

        // R5 通道空间（rx∈[0,2], ry∈[0,4], rz∈[0,2]）
        if (!u.valid[id]) {
            return wall(x, y, z); // 无效格（非树节点）
        }
        // 藤蔓：垂直井（与 gy±1 打通）内多条贴壁链（每个实心壁方向 1 条，中央列），
        // 从通道底到通道顶全高连续；四叉（全无壁）时悬空链兜底（向上扫描链方向）。
        if (ry <= CELL_Y - 1 && rx <= CELL_XZ - 1
                && (isOpen(gx, gy, gz, 0, 1, 0) || isOpen(gx, gy, gz, 0, -1, 0))) {
            BlockState vine = vineAt(gx, gy, gz, rx, rz);
            if (vine != null) {
                return vine;
            }
        }
        return AIR;
    }

    /** 垂直井口内该通道块 (rx,rz) 的藤蔓方块：每个实心壁方向各 1 条贴壁链（中央列）；
     *  全无壁（四叉）→ 悬空链（向上扫描链上第一个有壁格的方向）。非藤蔓返回 null。 */
    private BlockState vineAt(int gx, int gy, int gz, int rx, int rz) {
        boolean wallN = !isOpen(gx, gy, gz, 0, 0, -1);
        boolean wallW = !isOpen(gx, gy, gz, -1, 0, 0);
        boolean wallE = !isOpen(gx, gy, gz, 1, 0, 0);
        boolean wallS = !isOpen(gx, gy, gz, 0, 0, 1);
        if (wallN && rz == 0 && rx == 1) return VINE_NORTH;
        if (wallW && rx == 0 && rz == 1) return VINE_WEST;
        if (wallE && rx == 2 && rz == 1) return VINE_EAST;
        if (wallS && rz == 2 && rx == 1) return VINE_SOUTH;
        if (!wallN && !wallW && !wallE && !wallS) {
            // 四叉：悬空链——链由上方第一个有壁格垂下，穿过无壁段（每格由上方藤蔓支撑）
            int dir = vineHangDir(gx, gy, gz);
            if (dir == 0 && rz == 0 && rx == 1) return VINE_NORTH;
            if (dir == 1 && rx == 0 && rz == 1) return VINE_WEST;
            if (dir == 2 && rx == 2 && rz == 1) return VINE_EAST;
            if (dir == 3 && rz == 2 && rx == 1) return VINE_SOUTH;
        }
        return null;
    }

    /** 悬空链方向：当前格无壁时向上沿垂直井链扫描，找链上第一个有壁格的方向；
     *  链由该格向下延伸，无壁段由上方藤蔓支撑 = 悬空。整条链无壁或超上限 → -1。 */
    private int vineHangDir(int gx, int gy, int gz) {
        int gy2 = gy + 1;
        int scan = 0;
        while (scan < MAX_VINE_SCAN) {
            if (!fullyInWorld(gx, gy2, gz)) {
                return -1; // 世界顶
            }
            int dir = vineWallDirLocal(gx, gy2, gz);
            if (dir != -1) {
                return dir;
            }
            if (!isOpen(gx, gy2, gz, 0, 1, 0)) {
                return -1; // 链顶无壁：整条链无可依附方向
            }
            gy2++;
            scan++;
        }
        return -1;
    }

    /** 当前格的贴壁方向：确定性顺序 -Z, -X, +X, +Z 选第一个实心水平隔断；无壁返回 -1。 */
    private int vineWallDirLocal(int gx, int gy, int gz) {
        if (!isOpen(gx, gy, gz, 0, 0, -1)) return 0; // -Z 壁
        if (!isOpen(gx, gy, gz, -1, 0, 0)) return 1; // -X 壁
        if (!isOpen(gx, gy, gz, 1, 0, 0)) return 2; // +X 壁
        if (!isOpen(gx, gy, gz, 0, 0, 1)) return 3; // +Z 壁
        return -1;
    }

    // ---- 隔断判定 ----

    /**
     * 格 (gx,gy,gz) 与 (gx+dx,gy+dy,gz+dz) 之间的隔断是否打通（空气）。
     * 同单元 → 树边；跨单元 → 门（面 hash 选中格对）；任一格无效 → 墙。
     * 负方向 = 邻居格的正向隔断。
     */
    private boolean isOpen(int gx, int gy, int gz, int dx, int dy, int dz) {
        if (!fullyInWorld(gx, gy, gz) || !fullyInWorld(gx + dx, gy + dy, gz + dz)) {
            return false;
        }
        int uAx = Math.floorDiv(gx, UNIT), uAy = Math.floorDiv(gy, UNIT), uAz = Math.floorDiv(gz, UNIT);
        int uBx = Math.floorDiv(gx + dx, UNIT), uBy = Math.floorDiv(gy + dy, UNIT), uBz = Math.floorDiv(gz + dz, UNIT);
        if (uAx == uBx && uAy == uBy && uAz == uBz) {
            MazeUnit u = getUnit(uAx, uAy, uAz);
            int lx = gx & UNIT_MASK, ly = gy & UNIT_MASK, lz = gz & UNIT_MASK;
            int id = idx(lx, ly, lz);
            if (dx == 1) return !u.wallX[id];
            if (dy == 1) return !u.wallY[id];
            if (dz == 1) return !u.wallZ[id];
            if (dx == -1) return !u.wallX[idx(lx - 1, ly, lz)];
            if (dy == -1) return !u.wallY[idx(lx, ly - 1, lz)];
            return !u.wallZ[idx(lx, ly, lz - 1)];
        }
        // 跨单元 → 门：C1 稀疏门规则（3D 网格模拟验证：严格连通、无孤岛，最小度 2）。
        // 门开条件：X 面恒开（连通主干）；Y 面 ⟺ ux 偶；Z 面 ⟺ ux 偶 && uy 偶。
        // 门数 6 → ~3.4/单元（死路显著增多：单元边界 Y/Z 方向多为墙）。
        int fx, fy, fz, code;
        if (uAx != uBx) {
            if (uAx < uBx) { fx = uAx; fy = uAy; fz = uAz; } else { fx = uBx; fy = uBy; fz = uBz; }
            code = 0;
        } else if (uAy != uBy) {
            if (uAy < uBy) { fx = uAx; fy = uAy; fz = uAz; } else { fx = uBx; fy = uBy; fz = uBz; }
            code = 1;
        } else {
            if (uAz < uBz) { fx = uAx; fy = uAy; fz = uAz; } else { fx = uBx; fy = uBy; fz = uBz; }
            code = 2;
        }
        if (code == 1 && (fx & 1) != 0) {
            return false; // Y 门：ux 奇 → 不开（死路）
        }
        if (code == 2 && ((fx & 1) != 0 || (fy & 1) != 0)) {
            return false; // Z 门：ux 或 uy 奇 → 不开（死路）
        }
        // 门位置：faceSeed hash 选起点，行优先探测面内第一个"门格对两格都完全在界"的格对。
        // 角落单元（XZ/Y 上界 + Y 边界组合）部分格超界，hash 可能选中无效格对 → 门名义开、
        // 实际格不生成 → 出口/入口区域孤岛（几十格死路）。探测保证每个有效单元对面
        // 必有 1 个有效门 → C1 连通性在角落成立。探测 ≤64 次纯算术，低频路径。
        long faceSeed = HashUtil.hashPos(fx, fy, fz) ^ worldSeed ^ DIR_SALT[code];
        int s1 = (int) (faceSeed >>> 32) & UNIT_MASK;
        int s2 = (int) faceSeed & UNIT_MASK;
        int sel1 = -1, sel2 = -1;
        for (int i = 0; i < 64; i++) {
            int c1 = (s1 + (i >> 3)) & UNIT_MASK; // 行优先：c1 慢变（行），c2 快变（列）
            int c2 = (s2 + (i & 7)) & UNIT_MASK;
            if (doorPairValid(fx, fy, fz, code, c1, c2)) {
                sel1 = c1;
                sel2 = c2;
                break;
            }
        }
        if (sel1 < 0) {
            return false; // 理论不发生：有效单元对的接触面必有公共有效格对
        }
        // 当前隔断是否门：面内坐标按方向（X 面 = (ly,lz)、Y 面 = (lx,lz)、Z 面 = (lx,ly)）。
        if (code == 0) return (gy & UNIT_MASK) == sel1 && (gz & UNIT_MASK) == sel2;
        if (code == 1) return (gx & UNIT_MASK) == sel1 && (gz & UNIT_MASK) == sel2;
        return (gx & UNIT_MASK) == sel1 && (gy & UNIT_MASK) == sel2;
    }

    /** 门格对（较小单元侧边界层格 + 邻居侧对应格）两格都完全在界才可作为门。 */
    private static boolean doorPairValid(int fx, int fy, int fz, int code, int c1, int c2) {
        if (code == 0) { // X 面：门格 = (fx*8+7, fy*8+c1, fz*8+c2) 与 (fx*8+8, 同)
            return fullyInWorld(fx * UNIT + UNIT - 1, fy * UNIT + c1, fz * UNIT + c2)
                    && fullyInWorld(fx * UNIT + UNIT, fy * UNIT + c1, fz * UNIT + c2);
        }
        if (code == 1) { // Y 面：门格 = (fx*8+c1, fy*8+7, fz*8+c2) 与 (fx*8+c1, fy*8+8, 同)
            return fullyInWorld(fx * UNIT + c1, fy * UNIT + UNIT - 1, fz * UNIT + c2)
                    && fullyInWorld(fx * UNIT + c1, fy * UNIT + UNIT, fz * UNIT + c2);
        }
        // Z 面：门格 = (fx*8+c1, fy*8+c2, fz*8+7) 与 (fx*8+c1, fy*8+c2, fz*8+8)
        return fullyInWorld(fx * UNIT + c1, fy * UNIT + c2, fz * UNIT + UNIT - 1)
                && fullyInWorld(fx * UNIT + c1, fy * UNIT + c2, fz * UNIT + UNIT);
    }

    // ---- 单元生成 ----

    private MazeUnit getUnit(int ux, int uy, int uz) {
        UnitState s = TL.get();
        if (s.ux != ux || s.uy != uy || s.uz != uz) {
            s.ux = ux;
            s.uy = uy;
            s.uz = uz;
            s.unit = generateUnit(ux, uy, uz);
        }
        return s.unit;
    }

    /** 确定性生成一个单元：有效格集合上迭代 DFS 完美迷宫（生成树），树边 = 隔断打通。 */
    private MazeUnit generateUnit(int ux, int uy, int uz) {
        long seed = HashUtil.hashPos(worldSeed ^ ux, uy, uz);
        RandomSource rand = RandomSource.create(seed);
        MazeUnit u = new MazeUnit();
        // 有效格（完全在界）
        for (int lx = 0; lx < UNIT; lx++) {
            int gx = ux * UNIT + lx;
            for (int ly = 0; ly < UNIT; ly++) {
                int gy = uy * UNIT + ly;
                for (int lz = 0; lz < UNIT; lz++) {
                    int gz = uz * UNIT + lz;
                    u.valid[idx(lx, ly, lz)] = fullyInWorld(gx, gy, gz);
                }
            }
        }
        // DFS：起点 = 第一个有效格（确定性扫描）
        int start = -1;
        for (int i = 0; i < 512; i++) {
            if (u.valid[i]) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            return u; // 全无效（理论不可能：单元必与界内相交）
        }
        boolean[] visited = new boolean[512];
        for (int i = 0; i < 512; i++) {
            visited[i] = !u.valid[i];
        }
        visited[start] = true;
        int[] stack = new int[512];
        int top = 0;
        stack[0] = start;
        int[] cand = new int[6];
        while (top >= 0) {
            int cur = stack[top];
            int lx = cur >>> (UNIT_BITS * 2), ly = (cur >>> UNIT_BITS) & UNIT_MASK, lz = cur & UNIT_MASK;
            int n = 0;
            if (lx > 0 && !visited[cur - 64]) cand[n++] = cur - 64;
            if (lx < UNIT - 1 && !visited[cur + 64]) cand[n++] = cur + 64;
            if (ly > 0 && !visited[cur - 8]) cand[n++] = cur - 8;
            if (ly < UNIT - 1 && !visited[cur + 8]) cand[n++] = cur + 8;
            if (lz > 0 && !visited[cur - 1]) cand[n++] = cur - 1;
            if (lz < UNIT - 1 && !visited[cur + 1]) cand[n++] = cur + 1;
            if (n == 0) {
                top--;
                continue;
            }
            int pick = cand[rand.nextInt(n)];
            if (pick == cur - 64) u.wallX[idx(lx - 1, ly, lz)] = false;
            else if (pick == cur + 64) u.wallX[idx(lx, ly, lz)] = false;
            else if (pick == cur - 8) u.wallY[idx(lx, ly - 1, lz)] = false;
            else if (pick == cur + 8) u.wallY[idx(lx, ly, lz)] = false;
            else if (pick == cur - 1) u.wallZ[idx(lx, ly, lz - 1)] = false;
            else u.wallZ[idx(lx, ly, lz)] = false;
            visited[pick] = true;
            stack[++top] = pick;
        }
        return u;
    }

    // ---- 墙材质：LegacyPerlinNoise 低频分区 ----

    private BlockState wall(int x, int y, int z) {
        LegacyPerlinNoise n = matNoise;
        if (n == null) {
            synchronized (this) {
                n = matNoise;
                if (n == null) {
                    n = new LegacyPerlinNoise(
                            RandomSource.create(HashUtil.hashPos(worldSeed ^ MAT_SALT, 0, 0)));
                    matNoise = n;
                }
            }
        }
        // 采样坐标 = 块坐标 × 1/32 ≈ ±67M << 2^31 → LegacyPerlinNoise (int) 强转永不溢出
        return n.generateNoise(x * MAT_FREQ, y * MAT_FREQ, z * MAT_FREQ) > 0.0 ? OBSIDIAN : CRYING_OBSIDIAN;
    }
}
