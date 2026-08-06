# ExcellentEnchants Luminol 26.2 Folia 审计

## 范围与判定基线

- 目标核心：Luminol 26.2 build 726 stable，Minecraft 26.2，Java 25，协议 776。
- 审计基线：ExcellentEnchants 5.4.3，上游提交 `4bd372e5a73c5055d935d93c55ba7717393dd0e1`。
- 本文是修改前静态审计，不等同于真实 Luminol 服务端运行测试。
- `folia-supported: true` 只允许插件加载，不能证明线程安全或功能完整。
- 本地 Luminol API JAR 和服务端 JAR 是版本事实的最终依据；升级 Luminol、Paper、NightCore 或插件依赖后必须重新审计。
- `must_fix` 表示违反本项目 Folia 硬规则；`suggested_fix` 表示已有风险但不是明确硬失败；`defer` 表示超出本次范围；`uncertain` 表示缺少第三方运行契约或日志，不能推定安全。

## 修改前调用链

| 分级 | 入口与完整调用链 | 当前上下文 | 实际所有者 | 跨边界值 | 终止与清理 | 命中规则 |
|---|---|---|---|---|---|---|
| must_fix | `EnchantManager.setup -> onLoad -> addAsyncTask -> tickArrowEffects -> AbstractArrow#isValid/isDead/getLocation -> UniParticle#play` | NightCore Async | 每个 Arrow Entity | `AbstractArrow`、粒子集合和 Location 活状态 | Manager shutdown 只清空 Map；周期任务由 NightCore Manager 取消 | 1、2、3、6、8 |
| must_fix | `EnchantManager.setup -> onLoad -> addTask -> tickPassiveEnchants -> getPassiveEnchantEntities -> Players#getOnline/Server#getWorlds/World#getLivingEntities -> handleInSlots -> PassiveEnchant#onTrigger` | NightCore Global | 每个 Player 或 LivingEntity Entity | 在线玩家、世界和实体活对象集合 | Manager shutdown 取消周期任务；实体缓存另行清理 | 1、2、3、6、7、8 |
| must_fix | `EnchantManager.setup -> onLoad -> addTask -> tickBlocks -> TickedBlock#tick/restore` | NightCore Global | 每个 Block Region | `Location`、`Block` 活状态和临时方块记录 | Manager shutdown 调用 `restoreBlocks` 后清空 Map | 1、2、3、6、7、8 |
| must_fix | `TooltipListener#onGameModeChange -> plugin.runTask -> Player#updateInventory` | Player 事件上下文切到 NightCore Global next tick | Player Entity | `Player` 活对象 | 单次任务结束；Manager shutdown 清状态 | 1、3、6、7 |
| must_fix | `GenericListener#onChargesFillOnEnchant -> plugin.runTask -> EnchantItemEvent#getInventory/getEnchantsToAdd -> Inventory#getItem/setItem` | Player/Inventory 事件上下文切到 NightCore Global next tick | Player Entity 与其 Inventory | 事件、Inventory、ItemStack 活状态 | 单次任务结束 | 1、3、6、7、8 |
| must_fix | `EnchantListener#onProjectileHit -> plugin.runTask -> EnchantManager#removeArrowEffects` | Projectile 事件上下文切到 NightCore Global next tick | Arrow Entity；共享索引为 Global/Concurrent | `AbstractArrow` 强引用作为 Map key | 命中后一 tick 删除；失效箭目前由异步扫描删除；shutdown 清空 | 1、2、3、6、7、8 |
| must_fix | `AnvilListener#handleRecharge -> plugin.runTask -> PrepareAnvilEvent#getView -> AnvilView#setRepairCost` | Inventory 事件上下文切到 NightCore Global next tick | 查看者 Player Entity 与 Inventory | 事件和 AnvilView 活对象 | 单次任务结束 | 1、3、6、7、8 |
| must_fix | `EnchantManager#handleEnchantExplosion -> plugin.runTask -> explosions.remove(entity.getUniqueId())` | 爆炸 Entity Region 切到 NightCore Global next tick | Entity UUID 的共享索引；UUID 应在原上下文快照化 | 当前闭包仍捕获 `LivingEntity` 活对象 | 一 tick 后删除；shutdown 清空 | 1、2、3、6、7、8 |
| must_fix | `StoppingForceEnchant#onProtect -> plugin.runTask -> victim#getVelocity/setVelocity` | Victim 伤害事件上下文切到 NightCore Global next tick | Victim Entity | `LivingEntity` 活对象 | 单次任务结束 | 1、3、6、7、8 |
| must_fix | `AutoReelEnchant#onFishing -> plugin.runTask -> PlayerFishEvent#isCancelled/getHook -> FishHook#retrieve -> Player#swingHand/damageItemStack` | Player/FishHook 事件上下文切到 NightCore Global next tick | Player Entity 与 FishHook Entity | 事件、Player、FishHook、ItemStack 活对象 | 单次任务结束；没有实体退休回调 | 1、3、6、7、8 |
| must_fix | `ReplanterEnchant#onBreak -> plugin.runTask -> Block#setType/setBlockData` | Player/Block 事件上下文切到 NightCore Global next tick | Block Region | Block、Ageable BlockData；种子已在事件上下文扣除 | 单次任务结束；若写回失败种子不会恢复 | 1、3、6、7、8 |
| must_fix | `LingeringEnchant#onHit -> World#spawn(ThrownPotion) -> ThrownPotion#teleport(location)` | Projectile 命中 Region | 目标 Location Region | ThrownPotion 与目标 Location 活状态 | 后续喷溅事件或实体生命周期清理 | 4、6、8 |
| must_fix | `DragonfireArrowsEnchant#onHit -> World#spawn(ThrownPotion) -> ThrownPotion#teleport(location)` | Projectile 命中 Region | 目标 Location Region | ThrownPotion 与目标 Location 活状态 | 后续喷溅事件或实体生命周期清理 | 4、6、8 |
| must_fix | `SlotListener/GenericListener/EnchantListener/周期被动任务 -> EnchantManager#updateCache/reCache/clearCache -> EnchantHolder.cachedEnchants` | 多个 Player/Entity Region | UUID 对应的 Entity | 外层普通 `HashMap`、内层普通 `HashMap`、带 ItemStack 的 `EnchantedItem` | Quit/死亡和 shutdown 路径不完整；`EnchantHolder#clear` 不清缓存 | 2、6、8 |
| must_fix | `EnchantManager` 的 `tickedBlocks` 与 `explosions` | 多个 Region、Global 任务和事件 | Block Region 或共享索引 | 两个普通 `HashMap`；键空间随方块或 UUID 增长 | tick/爆炸后一 tick/shutdown 清理，但并发访问不安全 | 2、6、8 |
| must_fix | `EnchantRegistry` 静态 `BY_KEY/BY_ID/HOLDERS -> register/get/snapshot` | Bootstrap、命令、事件、网络回调等多个上下文 | Global 注册状态 | 三个普通 `HashMap` 及其快照 | 当前 shutdown 不清注册表和 Holder 缓存 | 2、8 |
| must_fix | `TooltipManager.updateStopList -> TooltipController#isReadyForTooltipUpdate -> PacketTooltipHandler/ProtocolTooltipHandler packet callback` | Player 事件写入，第三方网络回调读取 | UUID 状态为共享索引；Player 活状态归 Entity | 普通 `HashSet<UUID>`，网络回调还读取 `Player#getGameMode` | Quit 删除、Tooltip shutdown 清空、handler unregister | 2、6、8 |
| must_fix | `BaseCommands command callback -> target Player inventory/equipment/menu/reload` | Sender Player Region 或 Console Global | Target Player Entity；reload 为 Global 生命周期 | Target `Player`、Inventory、ItemStack、菜单活状态 | 命令返回；热重载可遗留旧 Manager/Listener/回调 | 1、3、6、8 |
| must_fix | `EnchantListener#onProjectileHit -> Projectile#getShooter -> Arrow/Trident enchant#onHit` | Projectile Entity Region | Shooter Entity 可能属于另一 Region | Projectile 与 Shooter 两端活对象 | 即时事件结束；箭矢效果下一 tick清理 | 6、8 |
| must_fix | `EnchantListener#onDamageByEntity -> AbstractArrow#getShooter -> enchant#onDamage` | Victim/伤害事件 Region | Shooter Entity 可能属于另一 Region | Victim、Projectile、Shooter 多端活对象 | 即时事件结束 | 6、8 |
| must_fix | `EnchantListener#onEntityDeath -> LivingEntity#getKiller -> handleInSlot -> KillEnchant#onKill` | Dead Entity Region | Killer Player 可能属于另一 Region | Dead Entity、Killer、Inventory 活状态 | 即时事件结束；缓存清理另行处理 | 6、8 |
| must_fix | `PlaceholderAPI -> PlaceholderHook.EnchantsExpansion#onPlaceholderRequest -> Player#getInventory#getItem` | PlaceholderAPI 调用线程未由本插件约束 | Player Entity | Player、Inventory、ItemStack 活状态 | expansion shutdown unregister 并置空 | 1、6、8 |
| suggested_fix | Packet tooltip 回调把第三方提供的 `Player` 传给 `TooltipController`，并操作 packet 转换得到的 ItemStack | PacketEvents/ProtocolLib 网络回调 | Player Entity；packet ItemStack 是转换副本 | Player 活对象、ItemStack 副本 | handler shutdown unregister | 2、6、8 |
| suggested_fix | `GlassbreakerEnchant/TunnelEnchant/ReplanterEnchant/CureEnchant/BaneOfNetherspawnEnchant/ElementalProtectionEnchant` 的静态可变集合 | 类初始化后主要只读，调用来自多个 Region | 进程级只读配置 | 可变 `HashSet/HashMap` | 类卸载前常驻 | 2、8 |
| defer | ProtocolLib 与 PacketEvents 自身的 Folia 回调线程、packet clone 和事件对象契约 | 第三方定义 | 第三方定义 | 缺少目标版本完整运行契约 | 第三方负责；本插件仅 unregister | uncertain |
| uncertain | NightCore 2.16.4 Manager 的所有任务取消、reload 顺序和异常回调边界 | NightCore 定义 | Global/Async/Entity/Region 依入口而定 | 依赖源码可静态检查，真实异常与 reload 仍需运行日志 | Manager shutdown | 1、3、6、8 |

## 共享集合审计

| 集合 | 修改前实现 | 读写来源 | 结论 | 处理方向 |
|---|---|---|---|---|
| `EnchantManager.arrowEffects` | `ConcurrentHashMap<AbstractArrow, Set<UniParticle>>`，值是普通 `HashSet` | Projectile 事件写入，Async 周期遍历，命中事件删除 | must_fix，Map 并发但值集合不安全，且强持有 Arrow | 改为 Entity 自有任务与可取消句柄，不再全局异步遍历活实体 |
| `EnchantManager.tickedBlocks` | `HashMap<Location, TickedBlock>` | 多 Region 添加/删除，Global 周期遍历与 shutdown 恢复 | must_fix | 使用不可变 BlockKey、并发索引和 Region 自有任务，增加持久化恢复记录 |
| `EnchantManager.explosions` | `HashMap<UUID, Explosion>` | 多 Region 爆炸创建、伤害、删除 | must_fix | `ConcurrentHashMap`，闭包只携带 UUID 快照，明确超时和 shutdown 清理 |
| `EnchantHolder.cachedEnchants` | 外层和槽位层均为 `HashMap` | Slot、装备、事件、被动附魔等多个 Entity Region | must_fix | 并发外层索引与不可变槽位快照，不向调用者暴露可变内部 Map |
| `EnchantRegistry.BY_KEY/BY_ID/HOLDERS` | 静态 `HashMap` | Bootstrap 注册，事件、命令、网络包读取 | must_fix | 并发注册表并返回不可变快照；shutdown 清缓存但保留静态 Holder 身份 |
| `TooltipManager.updateStopList` | `HashSet<UUID>` | Player 事件写，packet 回调读 | must_fix | `ConcurrentHashMap.newKeySet()`；Creative 状态也以 UUID 快照维护 |
| `TooltipManager.factoryMap` | `LinkedHashMap` | setup 填充，shutdown 清理 | suggested_fix，生命周期内基本单上下文 | 保持加载期写入，确保 packet handler 先注销再清理 |
| 静态材料、实体类型和伤害类型集合 | 可变 `HashMap/HashSet` | 类初始化写，多个 Region 只读 | suggested_fix | 改为 `Map.ofEntries`、`Set.of` 或不可变副本 |
| 配置加载期集合与事件局部集合 | `HashMap/HashSet/ArrayList` | 单次调用或加载期内部使用 | defer，无共享证据 | 不做机会式重构；若后续跨上下文再重新分类 |

## 调度与传送扫描基线

修改前静态扫描结果：

- `plugin.runTask`：8 处。
- `addAsyncTask`：1 处。
- `addTask`：2 处。
- 调度入口合计：11 处。
- 同步 `.teleport(`：2 处。
- 直接 `Bukkit.getScheduler()`：0 处；但业务代码通过 NightCore `plugin.runTask` 进入 Global scheduler，仍需迁移到项目包装层。
- `Future#get`、同步 `join` 和显式跨 Region 锁等待：当前目标源码扫描未命中。

## 指定不可用事件

以下五种事件在当前 `Core` 和 `API` Java 源码中均无监听器，静态扫描命中数为 0：

- `PlayerRespawnEvent`
- `PlayerTeleportEvent`
- `PlayerChangedWorldEvent`
- `WorldLoadEvent`
- `WorldUnloadEvent`

本项目 Folia 规则将这五种事件判定为在 Folia 层无可调用 API，因此本次不新增任何相关监听器。本地 Luminol 26.2 build 726 API JAR 包含对应 Bukkit 事件类只说明编译类型存在，不据此声明其 Folia 运行时可用。

## 跨实体事件降级行为

目标版本：Luminol 26.2 build 726。

- `EnchantListener#onProjectileHit`：当远程 shooter 不属于当前 Projectile Region 时，跳过 Arrow/Trident 的 `onHit` 附魔调用；箭矢效果清理仍提交到箭矢 Entity context。
- `EnchantListener#onDamageByEntity`：当 shooter、直接 damager 或 causing damager 不属于当前 Victim Region 时，跳过需要同时访问两端活状态的对应攻击、箭矢或防御附魔调用；事件本身不因门禁而取消，也不跨 Region 延迟重放。
- `EnchantListener#onEntityDeath`：当 killer 不属于死亡实体当前 Region 时，仅跳过 killer 的装备读取与 KillEnchant 调用；死亡实体自身的 Inventory/Death 附魔路径继续执行。
- 原因：这些 Bukkit 事件必须在当前 tick 内完成。跨 Region 调度至少延迟 1 tick，届时事件对象已失效，不能安全重放或继续修改。

## 修改目标与边界

- 一次性检测并缓存 Folia，业务代码只使用 `SchedulerUtil`，不反射构造调度调用。
- Entity 和 Region 调度延迟最少 1 tick；已经拥有目标时允许在当前事件上下文直接执行，跨边界不尝试绕过调度延迟。
- Async 线程只处理不可变快照、文件或网络工作，不读取或写入 Player、Entity、World、Chunk、Block 或 Inventory 活状态。
- 远程 shooter 或 killer 不归当前 Region 所有时，跳过需要同时访问两端活状态的即时附魔效果，不在另一 Region 重放已结束事件。
- Luminol 上保留 reload 命令节点但拒绝热重载，要求完整重启，避免旧监听器、任务和网络回调继续存活。
- 不修改插件名、主类、公开命令节点、权限和已有配置键；Tooltip 现有 `Player` API 保留兼容入口。
- 修改后必须执行功能与范围、Folia 与性能、生命周期与资源三轮复核，并用真实 Luminol 26.2 启动日志验证加载和正常关闭。

## 修改后逐项状态

修改前调用链表保留原始 `must_fix` 分级作为审计基线，不覆盖历史问题。以下表格给出 Luminol 26.2 build 726 目标代码的当前结论，每条原 `must_fix` 均已更新为 `fixed`：

| 当前状态 | 原问题 | 修改后文件与方法 | 验证证据 |
|---|---|---|---|
| fixed | 箭矢粒子从 Async 线程访问 Arrow | `EnchantManager#startArrowEffectsTask/tickArrowEffects` 使用箭矢 EntityScheduler，索引只保存 UUID 与可取消句柄 | `SchedulerUtilTest`、`scripts/verify-folia.ps1`、Core 测试 |
| fixed | Global 扫描全部世界实体执行被动附魔 | `PassiveEntityListener` 与 `EnchantManager#startPassiveTask` 为每个实体维护 EntityScheduler 任务，不再枚举 `World#getLivingEntities` | 周期入口静态复核、Core 测试 |
| fixed | Global tick 临时方块 | `EnchantManager#addTickedBlock/startTickedBlockTask` 和 `TickedBlock#tick/restore` 绑定 Block Region；`TickedBlockJournal` 异步保存不可变快照 | `BlockKeyTest`、`TickedBlockJournalTest`、Core 测试 |
| fixed | Tooltip 下一 tick 使用 NightCore Global | `TooltipListener#onGameModeChange` 使用 `SchedulerUtil#runAtEntityDelayed`，延迟强制至少 1 tick | `SchedulerUtilTest`、旧调度入口扫描 0 命中 |
| fixed | 附魔台事件跨 tick 捕获 Inventory 与事件对象 | `GenericListener#onChargesFillOnEnchant` 在事件上下文复制普通附魔 Map，再调度到 Enchanter Entity | 旧调度入口扫描 0 命中、Core 编译 |
| fixed | 箭矢效果清理捕获 Arrow 并进入 Global | `EnchantListener#onProjectileHit` 提交 Arrow Entity 延迟任务，Manager 索引以 UUID 为键并有退休清理 | `SchedulerUtilTest`、Core 测试 |
| fixed | 铁砧视图从 Global 修改 | `AnvilListener#handleRecharge` 调度到 Viewer Entity，并验证仍为同一 View | 旧调度入口扫描 0 命中、Core 编译 |
| fixed | 爆炸清理闭包强持有 LivingEntity | `EnchantManager#handleEnchantExplosion` 在原上下文快照 UUID，使用 Global 延迟清理并取消 shutdown 任务 | Core 测试、生命周期复核 |
| fixed | StoppingForce 从 Global 写 victim velocity | `StoppingForceEnchant#onProtect` 使用 Victim Entity 延迟任务 | 旧调度入口扫描 0 命中、Core 编译 |
| fixed | AutoReel 跨事件生命周期访问 Player/FishHook | `AutoReelEnchant#onFishing` 使用 FishHook Entity 延迟任务，并在执行时验证 Player 同属当前 Region、槽位鱼竿未变化 | 所有权静态复核、Core 编译 |
| fixed | Replanter 从 Global 写 Block | `ReplanterEnchant#onBreak` 使用目标 Block Region 延迟任务，并验证方块仍为空 | 旧调度入口扫描 0 命中、Core 编译 |
| fixed | Lingering 同步传送 ThrownPotion | `LingeringEnchant#createCloud` 在命中 Location 直接生成药水和效果云；只在当前 Region 拥有 shooter 时设置来源 | 同步 `.teleport(` 扫描 0 命中、Core 测试 |
| fixed | Dragonfire 同步传送 ThrownPotion | `DragonfireArrowsEnchant#createCloud` 在命中 Location 直接生成药水和效果云；只在当前 Region 拥有 shooter 时设置来源 | 同步 `.teleport(` 扫描 0 命中、Core 测试 |
| fixed | EnchantHolder 共享 HashMap 与可变槽位 Map | `EnchantHolder.cachedEnchants/enchants` 使用 `ConcurrentHashMap`，槽位状态原子替换为不可变 Map 快照，公开 getter 返回不可变快照 | `EnchantHolderTest` 5 项通过 |
| fixed | tickedBlocks 与 explosions 使用 HashMap | 两个索引均使用 `ConcurrentHashMap`；方块键改为不可变 `BlockKey`，爆炸不再保存无用 LivingEntity 引用 | `BlockKeyTest`、Core 测试、生命周期复核 |
| fixed | EnchantRegistry 静态 HashMap | `EnchantRegistry.BY_KEY/BY_ID/HOLDERS` 使用并发 Map 并返回不可变快照 | Core 测试、共享集合静态复核 |
| fixed | Tooltip 普通 HashSet 与网络线程 GameMode 读取 | `TooltipPlayerState` 使用两个 `ConcurrentHashMap.newKeySet()` 保存 paused/creative UUID；Player Join/GameMode/Quit 在 Entity 事件上下文维护；网络回调只查询 UUID | `TooltipPlayerStateTest` 2 项通过，两个 tooltip 模块编译 |
| fixed | BaseCommands 从 sender 上下文访问目标玩家和热重载 | `BaseCommands#runAtPlayer/send` 分别路由目标 Player 与 sender；reload 节点保留但明确拒绝热重载 | `SchedulerUtilTest#routesRemotePlayerSenderToEntityScheduler`、`doReload(` 扫描 0 命中 |
| fixed | ProjectileHit 访问远程 shooter | `EnchantListener#onProjectileHit` 用 `SchedulerUtil#isOwned` 门禁，不拥有远程 shooter 时只跳过双实体即时效果 | 所有权调用链复核、Core 编译 |
| fixed | Damage 访问远程 shooter/damager | `EnchantListener#onDamageByEntity` 对 shooter、damager 和 causing damager 分别执行当前 Region 所有权门禁 | 所有权调用链复核、Core 编译 |
| fixed | Death 访问远程 killer | `EnchantListener#onEntityDeath` 在 killer 不属于死亡实体 Region 时只跳过 KillEnchant 路径 | 所有权调用链复核、Core 编译 |
| fixed | PlaceholderAPI 未知线程读取玩家背包 | `PlaceholderHook#onPlaceholderRequest` 在背包读取前调用 `SchedulerUtil#isOwned(player)`，不拥有时返回 null | Core 编译、Placeholder 调用链复核 |

## 修改后验证与限制

- `scripts/verify-folia.ps1`：退出码 0，输出 `Folia static verification passed`。直接 Bukkit/NightCore 调度入口、同步 teleport、五种指定不可用事件、阻塞 Future 等待和新增 Java 源码注释均为 0 命中。
- `mvn clean verify`：Java 25，6 个目标模块全部成功；Core 共 26 个测试，0 failures、0 errors、0 skipped；Maven 退出码 0。
- 最终 JAR：`target/ExcellentEnchants-5.4.3.jar`，447137 字节，SHA-256 `A1593FD37685DB7EE9845DE81C90E54DEFD6CCA89F78A962F28A8CB8216C58D8`。
- JAR 内容：包含 `PaperEnchantsBootstrap`、`SchedulerUtil`、`plugin.yml` 和 `paper-plugin.yml`；不包含 `SpigotEnchantsBootstrap` 或 `mc_26_1_2` 类。两个描述文件都只出现一次 `folia-supported: true` 和 `cloudfl4re`。
- NightCore 2.16.4 实际没有计划草稿引用的 `Placeholders.GENERIC_ERROR`。reload 命令直接发送完整固定拒绝文本，保持原命令节点并以实际依赖 API 为准。
- PacketEvents 2.11.1 直接从 `User#getUUID` 提供 UUID；ProtocolLib 5.4.0 的 `PacketEvent` 只公开 Player，因此 ProtocolLib 路径只调用 `Player#getUniqueId`，不读取 GameMode、Inventory 或其他活状态。ProtocolLib 自身回调线程与 packet clone 契约仍为 `uncertain`。
- 临时方块日志对未加载世界的记录会保留并告警，不监听本项目禁止新增的 `WorldLoadEvent`；未加载世界随后重新加载时的真实恢复行为在启动测试前保持 `uncertain`。
- 远程 shooter、damager、causing damager 或 killer 不属于当前 Region 时，相关双实体即时附魔效果会跳过，不跨 Region 重放已结束事件。
- 热重载被明确拒绝，必须完整重启服务器。
- `folia-supported: true` 只允许插件加载，不证明线程安全；真实 Luminol 启停测试、真实多玩家跨 Region 压力测试和真实 MythicMobs 联调在对应证据产生前不得标记为已验证。

## 构建基线

- 修改前目标：Java 21、Paper API `26.1.2.build.51-beta`、NightCore `2.15.3`。
- 修改前反应堆：10 个模块，包含 4 个旧 Spigot/NMS 版本模块。
- 基线命令：Maven 3.9.11、Java 25.0.4、隔离本地仓库、HTTP/HTTPS 均通过 `localhost:7897`。
- 基线结果：退出码 1，API 模块依赖解析失败，`su.nightexpress.nightcore:main:2.15.3` 无法从已配置仓库取得。
- 基线产物：0 个 target JAR。
- 即使旧构建成功也不满足目标，因为其核心 API、Java 级别和反应堆版本均不是 Luminol 26.2 build 726。
- Luminol API `26.2.build.726-stable` 未发布在已验证的公开 Maven 仓库；本次使用 paperclip 缓存中的同坐标 JAR，SHA-256 为 `1913620BA779E465F89144CA316F516E53C020B3B507F9F8BACC1EE3F78CAB77`。
- paperclip 只缓存 API JAR、没有 POM；构建显式补充其方法签名所需并与 build 726 清单一致的 Adventure API、MiniMessage、Adventure SLF4J Logger `5.2.0`、Guava `33.6.0-jre`、SLF4J API `2.0.18`、Log4j API `2.26.0` 和 BungeeCord Chat `1.21-R0.2-deprecated+build.21`，它们均为 `provided`。
- 迁移后反应堆：6 个模块；API、PacketEvents、ProtocolLib、MythicMobs 兼容层和 Core 全部通过 Java 25 编译与打包。
- 迁移后产物：`target/ExcellentEnchants-5.4.3.jar`，417893 字节；包含 Paper Bootstrap 和主类，不包含旧 Spigot/NMS 类。
- MythicMobs 官方 5.6.0 JAR 在代理下持续无法完成下载；本次只用不进入产物的临时签名 JAR验证现有一处 API 调用可编译。真实 MythicMobs 运行联调仍为 `uncertain`。
