package com.inf.farlands.window;

/**
 * 服务端实体 section 存储标记。
 *
 * EntitySectionStorage 双端共享——服务端 PersistentEntitySectionManager 和
 * 客户端 TransientEntitySectionManager 都用它；createSection 的窗口过滤
 * 只应作用于服务端：客户端实体必须持续 tick 插值，降级会导致瞬移/鬼畜。
 * 由 PersistentEntitySectionManagerMixin 构造 @Inject 标记；
 * 客户端 Transient 不标记 → 默认不过滤；漏标记只损失冻结、不误伤客户端，属安全默认。
 */
public interface ServerEntitySectionStorage {
    void markServerSide();
}
