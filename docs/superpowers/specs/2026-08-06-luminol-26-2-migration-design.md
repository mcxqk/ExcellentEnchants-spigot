# ExcellentEnchants Luminol 26.2 迁移设计

## 目标

将 ExcellentEnchants 5.4.3 迁移为只支持 Minecraft 26.2、Luminol/Folia 和 Java 25 的单一服务端插件构建，同时保持插件名称、主类、命令、权限、配置键、公开 API 和既有附魔行为不变。迁移必须消除旧 Bukkit 主线程假设、同步传送、跨 Region 实时状态访问、共享集合并发修改、任务泄漏、重载残留和阻塞等待。

## 已确认的目标核心

- 本地参考文件：`E:\down\luminolpaperclip\luminol-26.2-paperclip.jar`
- Minecraft：`26.2`
- Luminol：`26.2.build.726-stable`
- Build：`726`
- Git branch：`dev/26.2`
- Git commit：`81b92e0`
- Java：`25`
- 本地 API：`luminol-api-26.2.build.726-stable.jar`
- 公开编译 API：`me.earthme.luminol:luminol-api:26.2.build.711-stable`
- 公开 Maven 仓库：`https://repo.menthamc.org/repository/maven-public/`
- NightCore：`su.nightexpress.nightcore:main:2.16.4`

公开仓库当前没有 build 726 API，因此项目默认依赖同一 26.2 稳定 API线的 build 711。验证阶段还会把本地 build 726 API 临时安装到本地 Maven 缓存，并通过 Maven 属性覆盖版本后重新运行完整测试和打包。项目文件不得包含本机绝对依赖路径。

## 范围

### 包含

- 将 Java 编译版本升级为 25。
- 将 Core 编译依赖切换到 Luminol 26.2 API。
- 将 NightCore 升级到 2.16.4。
- 删除旧 Minecraft、Spigot 和版本化 NMS 构建模块。
- 保留 Paper Registry Lifecycle 注册流程。
- 增加一次性 Folia 检测和统一调度门面。
- 迁移箭矢粒子、被动附魔和临时方块三个周期任务系统。
- 修复延迟调用、同步传送、共享集合、Packet 回调、Placeholder 回调和禁用清理。
- 增加单元测试、并发测试、恢复存储测试和静态红线检查。
- 更新插件描述中的目标版本、Folia 声明和作者信息。

### 不包含

- 保留或恢复旧版 Minecraft、Spigot 或普通 Paper 支持。
- 改名插件、主类、命令、权限或配置键。
- 修改附魔数值、概率、描述或非线程相关行为。
- 重写 NightCore、PacketEvents、ProtocolLib、PlaceholderAPI 或 MythicMobs。
- 在真实多玩家、多 Region 生产服务器上进行压力测试。
- 对无关代码进行格式化或重构。

## 方案比较

### 采用：Luminol 26.2 原生精简迁移

删除旧 NMS 模块，使用 Luminol API 和 Paper Bootstrap，完整迁移线程模型、并发状态和生命周期。该方案与“仅 26.2”一致，并避免保留不可编译、不可测试的死代码。

### 未采用：保留旧源码但禁用旧模块

该方案便于未来恢复旧版本，但仓库会长期保留未编译和未测试的 NMS 源码，增加误用和维护风险。

### 未采用：最小编译迁移

只升级依赖和描述文件无法解决异步箭矢、Global 实体扫描、跨 Region 方块访问、同步传送和生命周期泄漏，不能满足 Folia 兼容要求。

## 构建与文件边界

根 Maven 工程只保留以下模块：

- `API`
- `Core`
- `tooltip-packetevents`
- `tooltip-protocollib`
- `compat-mythicmobs`

删除以下旧模块：

- `MC_1_21_8`
- `MC_1_21_10`
- `spigot-1.21.11`
- `spigot-26.1.2`

Core 不再 Shade 旧 NMS 模块。最终产物保持 `ExcellentEnchants-5.4.3.jar`，并只携带本项目模块，不打包 Luminol、NightCore 或可选插件依赖。

`paper-plugin.yml` 成为唯一服务端描述文件，包含：

- 原主类 `su.nightexpress.excellentenchants.EnchantsPlugin`
- 原 Bootstrap `su.nightexpress.excellentenchants.bridge.paper.PaperEnchantsBootstrap`
- API版本 26.2
- Folia 支持声明
- 原依赖和可选依赖关系
- 作者 `NightExpress` 与 `cloudfl4re`

删除只用于 Spigot 的 `plugin.yml`、`SpigotEnchantsBootstrap`、`SpigotItemTagLookup` 和版本化 `RegistryHack` 实现。API层现有的公共 bridge 类型全部保留，以维持源码和二进制 API；不再使用的类型不进入 Luminol 运行链。

## 运行组件

### PaperEnchantsBootstrap

继续通过 Luminol/Paper Registry Lifecycle API 创建 ItemSet 标签、注册自定义附魔并更新附魔分布标签。注册过程发生在服务端 Bootstrap 和注册表生命周期上下文，不进入 Region 运行期，不使用旧 NMS 反射注册逻辑。

### SchedulerUtil

新增实例化调度门面，并由 `EnchantsPlugin` 持有。门面包含一个静态缓存的 Folia 检测结果，启动早期只初始化一次。除检测外不得通过反射调度。

门面提供：

- `runAtEntity`
- `runAtEntityDelayed`
- `runAtEntityTimer`
- `runAtRegion`
- `runAtRegionDelayed`
- `runAtRegionTimer`
- `runGlobal`
- `runAsync`
- `teleportAsync`
- `isOwnedByCurrentRegion`
- `cancelAll`
- `close`

普通 Entity、Region、Global 和 Async 调度优先复用 NightCore 2.16.4。NightCore 没有提供的 Entity/Region 延迟任务、周期任务、退休回调和传送 Future 处理使用 Luminol 官方 API。调度门面保留基于缓存检测结果的非 Folia 回退分支，但启动检查会在业务 Manager 创建前拒绝非 Folia 运行环境，因此该分支不构成 Paper 支持承诺。非 Global 的延迟和周期任务初始延迟强制不小于 1 tick。

已经处于正确所有者上下文且不要求下一 tick 的逻辑直接执行。要求下一 tick 的逻辑必须通过对应所有者调度。Async 任务只接收 UUID、整数坐标、Material、字符串、数值和复制后的 ItemStack 等快照。

门面跟踪所有由 ExcellentEnchants 拥有的 ScheduledTask。任务完成、取消或实体退休时从任务集合移除。`close` 后拒绝创建新任务，取消剩余任务并清除插件引用。

### EnchantManager

EnchantManager 保留附魔分发职责，但不再从 Global 或 Async 任务直接读取实体、世界或方块。箭矢、被动实体、临时方块和爆炸状态分别使用专用并发状态对象。

### Tooltip 和 Placeholder 快照

PacketEvents、ProtocolLib 和 PlaceholderAPI 回调不得同步读取 Player 的 GameMode、Inventory、Equipment 或其他 Region 实时状态。玩家 Entity 事件负责生成不可变快照，外部回调只读取快照和数据包中的 ItemStack 副本。

## 修改前问题清单

下表以目标 Luminol `26.2.build.726-stable` 为版本边界。`当前上下文`描述现有代码在 NightCore 2.15.3 Folia 调度下的实际或可证明上下文；第三方回调契约不足时标记为 `uncertain`。

| 分类 | 入口与完整调用链 | 当前上下文 | 实际所有者 | 跨边界值 | 终止与清理 | 命中规则 |
|---|---|---|---|---|---|---|
| `must_fix` | `EnchantManager.onLoad -> addAsyncTask -> tickArrowEffects -> AbstractArrow.isValid/isDead/getLocation` | Async | 每支箭的 Entity | 实时 AbstractArrow、粒子 Set | 仅命中或异步扫描删除，可能保留实体引用 | 1、2、6、8 |
| `must_fix` | `EnchantManager.onLoad -> addTask -> tickPassiveEnchants -> getPassiveEnchantEntities -> Bukkit worlds/living entities -> handleInSlots` | Folia Global | 每个 LivingEntity 的 Entity/Region | Player、LivingEntity、World、装备和 ItemStack | Manager shutdown 取消 Global 任务，但运行期间跨 Region | 1、6、7、8 |
| `must_fix` | `EnchantManager.onLoad -> addTask -> tickBlocks -> TickedBlock.tick/restore/sendDamageInfo` | Folia Global | 方块 Region及每个 Player Entity | Location、World、Block、Player | shutdown 从 Global 调用 restore，Region 不安全 | 1、2、6、7、8 |
| `must_fix` | `TooltipListener.onGameModeChange -> plugin.runTask -> Player.updateInventory` | Folia Global | Player Entity | Player 实时对象 | 一次任务，无目标退休回调 | 1、3、6、7、8 |
| `must_fix` | `GenericListener.onChargesFillOnEnchant -> plugin.runTask -> event inventory read/write` | Folia Global | 附魔玩家 Entity及界面 Region | EnchantItemEvent、Inventory、ItemStack | 一次任务捕获事件和 Inventory | 1、3、6、7、8 |
| `must_fix` | `AnvilListener.handleRecharge -> plugin.runTask -> event view repair cost` | Folia Global | 查看铁砧的 Player Entity | PrepareAnvilEvent、InventoryView | 一次任务捕获事件视图 | 1、3、6、7、8 |
| `must_fix` | `EnchantListener.onProjectileHit -> plugin.runTask -> removeArrowEffects` | Folia Global | 箭矢 Entity或只按 UUID 的并发状态 | AbstractArrow 作为 Map 键 | 一次任务；箭矢退休时无清理回调 | 1、2、3、6、7、8 |
| `must_fix` | `StoppingForceEnchant.onProtect -> plugin.runTask -> victim.getVelocity/setVelocity` | Folia Global | victim Entity | LivingEntity 实时对象 | 一次任务，无退休清理 | 1、3、6、7、8 |
| `must_fix` | `AutoReelEnchant.onFishing -> plugin.runTask -> event/hook/player/item operations` | Folia Global | Player 和 FishHook Entity | PlayerFishEvent、Player、FishHook、ItemStack | 一次任务捕获整条事件状态 | 1、3、6、7、8 |
| `must_fix` | `ReplanterEnchant.onBreak -> plugin.runTask -> Block.setType/setBlockData` | Folia Global | 作物方块 Region | Block、Ageable BlockData | 一次任务，无 Region 所有者 | 1、3、6、7、8 |
| `must_fix` | `EnchantManager.handleEnchantExplosion -> plugin.runTask -> explosions.remove(entity.getUniqueId())` | Folia Global | 实体 UUID状态；UUID应在原上下文取快照 | LivingEntity 被闭包捕获 | 延迟删除前 Map 可被多个 Region 访问 | 1、2、3、6、7、8 |
| `must_fix` | `LingeringEnchant.onHit -> createCloud -> shooter.launchProjectile -> potion.teleport` | 命中箭矢 Region | shooter Entity 与命中 Region可能不同 | ProjectileSource、ThrownPotion、Location、Cloud | 方法尾部删除 potion，取消时删除 cloud | 4、6、8 |
| `must_fix` | `DragonfireArrowsEnchant.onHit/onDamage -> createCloud -> shooter.launchProjectile -> potion.teleport` | 命中或 victim Region | shooter Entity 与目标 Region可能不同 | ProjectileSource、ThrownPotion、Location、Cloud | 方法尾部删除 potion，取消时删除 cloud | 4、6、8 |
| `must_fix` | `SlotListener/EnchantManager -> EnchantHolder.updateCache/removeCache/clearCache/getCached` | 多个 Entity Region并行 | 各实体 Entity上下文，但 Holder为全局共享 | UUID、EquipmentSlot、ItemStack引用 | quit 只清单个实体，Holder.clear 不清 cachedEnchants | 2、6、8 |
| `must_fix` | `EnchantManager.addArrowEffect/tickArrowEffects/removeArrowEffects` | Entity事件与 Async任务并行 | 箭矢 Entity | CHM 外层中的 HashSet 内层和实体键 | 命中或扫描删除，退休路径缺失 | 2、6、8 |
| `must_fix` | `EnchantManager.add/remove/tickTickedBlock` | 多个 Region事件与 Global任务并行 | 每个方块 Region | HashMap、可变 Location、TickedBlock实时状态 | shutdown 清 Map，但恢复路径跨 Region | 2、6、8 |
| `must_fix` | `createExplosion -> explosions.put -> explosion/damage listeners -> delayed remove` | 来源与受影响实体所在 Region可能并行 | 各事件目标 Region；共享状态只存快照/并发对象 | HashMap、Explosion、LivingEntity owner | 延迟删除；shutdown 清理 | 2、6、8 |
| `must_fix` | `Tooltip Bukkit listeners -> updateStopList` 与 `Packet handlers -> isReadyForTooltipUpdate` | Region事件与第三方 Packet回调并行 | Player Entity；Packet只应读快照 | HashSet、UUID、Player GameMode | quit和shutdown删除，但集合非并发 | 2、6、8 |
| `uncertain` | `PacketEvents.onPacketSend/ProtocolLib.onPacketSending -> TooltipController.isReadyForTooltipUpdate(Player)` | 第三方发送线程契约未在当前源码中证明 | Player Entity | Player、GameMode、UUID | 监听器可注销，可能存在在途回调 | 2、6、8；实现将改为快照以消除不确定性 |
| `uncertain` | `PlaceholderAPI -> EnchantsExpansion.onPlaceholderRequest -> player.getInventory` | 第三方调用线程契约未在当前源码中证明 | Player Entity | Player、Inventory、ItemStack | expansion 可注销 | 2、6、8；实现将改为装备快照以消除不确定性 |
| `must_fix` | `EnchantRegistry` 静态 Maps与 Holder -> manager reload/disable | 启动 Global写入，多个 Region读取，重载再次访问 | Global注册状态与Entity缓存分离 | CustomEnchantment持有 Plugin/Manager | 现有 shutdown 未清 BY_KEY、BY_ID、HOLDERS缓存 | 2、8 |
| `must_fix` | `EnchantsPlugin.disable -> EnchantManager.shutdown -> restoreBlocks` | Folia禁用/Global上下文 | 每个方块 Region | TickedBlock、Location、World、Block | 禁用时直接跨 Region恢复；完全停服前无可靠恢复记录 | 6、8 |
| `must_fix` | `EnchantsPlugin/NightCore Version -> Folia检查与业务调度` | NightCore静态检测一次，但插件没有自己的统一入口 | 检测属于启动 Global；业务目标各异 | boolean | NightCore负责自身缓存，业务调用分散 | Startup、1、3、6 |
| `suggested_fix` | `EnchantRegistry`、附魔定义、ItemSet和组件配置加载后被多个 Region只读 | 启动写入，运行期并行读取 | Global发布的不可变配置 | 字符串、枚举、不可变定义 | reload时替换或清理 | 2、8；以不可变副本发布 |
| `defer` | 每次事件内部创建的 HashMap、HashSet、ArrayList | 单个事件所有者栈内 | 当前事件所在 Entity/Region | 局部快照 | 方法返回后回收 | 不属于共享集合，不修改 |
| `defer` | 静态 Material、EntityType、DamageType 常量集合 | 类初始化后只读 | 无实时所有者 | 枚举常量 | 类卸载 | 不属于运行期共享可变状态；必要时只做不可变封装 |

### 已验证不存在的红线

- 业务源码没有直接调用 `Bukkit.getScheduler()`；现有调度均经 NightCore，但部分调用缺少所有者，仍需迁移。
- 没有 `Future.get()` 或 `CompletableFuture.join()`。
- 没有 `PlayerRespawnEvent`、`PlayerTeleportEvent`、`PlayerChangedWorldEvent`、`WorldLoadEvent` 或 `WorldUnloadEvent` 监听器。
- 目标 build 726 API JAR包含上述事件类符号，但当前证据不能证明各事件在 Luminol Region运行层的实际触发契约；本插件不依赖它们。

## 线程与数据流设计

### 箭矢粒子

每支具有轨迹效果的箭矢建立一个 Entity 周期任务。任务只在箭矢所有者上下文读取有效性、死亡状态和位置。状态表使用 `ConcurrentHashMap<UUID, ArrowTrail>`，粒子集合使用并发 Set。

ProjectileHit、实体退休、任务取消或无效检测都会取消任务并按 UUID删除状态。退休回调不读取已经退休的实体。关闭时取消剩余任务并清空所有实体引用。

### 被动附魔

删除 Global 世界扫描。玩家加入、实体加入世界和区块实体加载时，为符合配置的 LivingEntity建立一个 Entity 周期任务。同一 UUID只能存在一个任务。

玩家退出、实体死亡、实体移出世界、区块实体卸载和 Entity Scheduler退休都会取消任务。玩家始终可建立任务；非玩家任务只在配置允许生物被动附魔时建立。没有启用被动附魔时不建立该类任务。

### 临时方块

使用不可变 `BlockKey(world UUID, x, y, z)` 和 `BlockSnapshot(original, temporary, expiresAt)`。状态表使用 ConcurrentHashMap。每个方块由其 Region周期任务计时、发送破坏进度并恢复。

初次变更采用以下流水线：

1. 在方块 Region读取不可变快照。
2. Async将快照写入有界恢复存储。
3. 写入完成后调度回方块 Region。
4. 验证方块仍为预期原类型。
5. 设置临时类型并建立 Region周期任务。

恢复采用幂等规则：只有方块仍为插件临时类型时才恢复原类型；若已被其他行为改变，则不覆盖新状态并删除恢复记录。世界或区块未加载时不强制加载，等待加载事件重试。

恢复存储使用单一串行 Async写入链和原子文件替换。达到固定容量时拒绝创建新的临时方块，不允许无界增长。长期不存在的世界记录按固定保留期限清理并记录一次警告。

重载期间，由仍启用的 NightCore调度只捕获不可变 BlockSnapshot的恢复任务。完整停服前无法执行的恢复由下次启动和区块加载路径处理。

### 爆炸状态

爆炸表改为 ConcurrentHashMap，并在来源实体上下文提前提取 UUID。事件回调只修改当前事件所属 Region的事件对象。延迟删除返回来源实体上下文；来源实体退休时按 UUID清理。Explosion状态在发布后不可变或使用安全发布字段。

### 装备和附魔缓存

EnchantHolder外层 UUID Map和内层 EquipmentSlot Map均使用 ConcurrentHashMap。缓存 ItemStack必须是复制后的快照，附魔 Map以不可变副本发布。clear同时清除附魔定义和实体缓存。

EnchantRegistry运行期 Map使用并发实现，启动注册完成后不再修改；reload和disable显式清空 CustomEnchantment、Holder缓存及其 Plugin/Manager引用。

### Tooltip和Placeholder

维护按在线玩家有界的不可变 `TooltipPlayerState` 与 `EquipmentSnapshot`。状态在 Player加入、游戏模式变化、装备槽变化和退出事件中更新。第三方回调通过预先建立的身份映射读取快照，不调用 Player实时方法。

Packet处理器只修改数据包中的 ItemStack副本。Placeholder剩余充能查询只读取 EquipmentSnapshot。退出、处理器关闭和插件禁用时删除状态。

### 延迟调用映射

- Tooltip刷新：Player Entity下一 tick。
- 附魔台结果充能：附魔玩家 Entity下一 tick。
- 铁砧修理成本：查看铁砧的 Player Entity下一 tick。
- AutoReel：Player Entity下一 tick；重新验证 Hook有效性。
- StoppingForce：victim Entity下一 tick。
- Replanter：种子扣除在 Player当前上下文；方块恢复在作物 Region下一 tick。
- 箭矢状态删除：箭矢 Entity下一 tick或退休清理。
- 爆炸状态删除：来源实体下一 tick或退休清理。

### 药水云

Lingering和Dragonfire不再从远端 shooter所有者上下文发射药水后同步传送。它们在命中位置所属 Region直接生成临时 ThrownPotion与 AreaEffectCloud，设置药水数据和 ProjectileSource，调用 LingeringPotionSplashEvent，并在方法结束时删除临时药水。事件取消时删除云。

未来传送统一使用 `SchedulerUtil.teleportAsync`。Future回调不得阻塞；需要实时状态时重新调度到目标所有者。

## 生命周期

启动顺序：

1. 验证 Minecraft版本为 26.2。
2. 初始化并缓存一次 Folia检测。
3. 验证目标具备 Luminol/Folia调度能力。
4. 创建 SchedulerUtil。
5. 初始化 EnchantsAPI。
6. 加载 Tooltip、EnchantManager、外部 Hook和命令。

关闭顺序：

1. SchedulerUtil停止接收新任务。
2. 注销 PlaceholderAPI和Packet监听器。
3. 取消箭矢、实体、Region、Global和Async任务。
4. 提交只捕获不可变快照的临时方块恢复任务，保留未完成恢复记录。
5. 清空 Tooltip、爆炸、箭矢、实体任务、方块和装备缓存。
6. 清空 EnchantRegistry中的插件附魔对象和Holder缓存。
7. 关闭菜单、Manager和外部处理器。
8. 清空 EnchantsAPI与SchedulerUtil插件引用。

禁用后，除由NightCore拥有且只携带不可变方块恢复快照的终端清理外，旧Manager、Listener、任务、HTTP客户端或Future不得继续回调。

## 错误处理

- 非26.2或非Folia目标记录明确错误并停止启用。
- NightCore缺失或版本不足时停止启用。
- SchedulerUtil关闭后拒绝新任务。
- Entity退休时运行UUID清理，不访问实体。
- 重复实体任务只保留一个。
- teleportAsync失败异步记录，不阻塞Region。
- 初始方块恢复记录写入失败时不修改世界。
- 方块恢复成功而记录删除失败时保留记录，后续幂等清理。
- 第三方Packet或Placeholder回调找不到玩家快照时返回原数据或空占位结果，不回退到实时Player读取。
- 恢复文件损坏时保留原文件、记录错误并拒绝应用不可信记录，不覆盖世界方块。

## 性能边界

- 箭矢任务数不超过活跃附魔箭矢数。
- 被动任务数不超过当前加载且符合配置的LivingEntity数。
- 临时方块任务数不超过恢复存储固定容量。
- 每个实体只有一个被动任务。
- Tooltip和Equipment快照只保存在线玩家。
- 不从Global任务扫描所有世界或所有实体。
- 不在Region线程执行磁盘、数据库或网络IO。
- 不在Region线程等待其他Region、Future或锁。

## README 文档设计

### 语言与维护方式

`README.md` 作为完整中文主页，`README_EN.md` 保存带有 Fork 状态说明的上游英文内容。两份文档在顶部互相提供语言切换链接。英文文档不作为中文主页的完整镜像，也不把上游旧兼容表表述为当前 Fork 的支持范围。

### 中文主页

中文主页保留上游横幅和功能 GIF，并为所有图片添加非空中文 `alt` 文本。顶部添加 Minecraft 26.2、Luminol、Java 25、Folia、GPL-3.0 和迁移中状态徽章，不添加未经持续集成证明的 Build Passing 或 Release 徽章。

正文按以下顺序组织：

1. 项目简介。
2. Luminol 26.2 迁移状态。
3. 功能特性。
4. 效果展示。
5. 环境要求。
6. 安装说明。
7. 从源码构建。
8. 可选依赖。
9. 文档与链接。
10. 作者与致谢。
11. GNU GPL v3 许可证。

迁移状态必须明确：当前 `master` 仍在迁移和验证中，不能作为已经完成 Luminol/Folia 兼容的生产版本使用。目标核心为 Luminol `26.2.build.726-stable`，目标环境为 Minecraft 26.2、Java 25 和 NightCore 2.16.4。编译成功或 `folia-supported: true` 均不能单独证明完整运行时线程安全。

功能说明使用自然中文，保留 PlaceholderAPI、PacketEvents、ProtocolLib、MythicMobs、GUI、ItemStack、Folia 和 Luminol 等专有名词。删除“绝不会出现错误、Bug 或崩溃”等无法验证的绝对承诺。上游 Wiki 必须注明可能尚未覆盖当前 Fork。

当前没有经过验证的发布构建，因此安装章节不提供可直接用于生产环境的成品下载步骤。从源码构建章节记录目标命令 `mvn clean package`，同时说明只有迁移实现和测试完成后，该命令才代表受支持构建流程。普通用户文档不把本机 `localhost:7897` 代理写成强制要求。

作者信息保留原作者 NightExpress，并列出 Luminol 26.2 迁移作者 cloudfl4re。源码链接同时包含 `mcxqk/ExcellentEnchants-spigot` Fork 与 `nulli0n/ExcellentEnchants-spigot` 上游。

### 英文上游参考

`README_EN.md` 在顶部链接回简体中文主页，并增加醒目的 Fork notice。Notice 说明当前 Fork 面向 Luminol 26.2、迁移仍在进行中、下方内容来自上游 README，以及上游兼容表不代表当前 Fork 的兼容状态。

Notice 以下保留上游英文介绍、功能描述、GIF、旧兼容表和原始链接。所有图片补充非空英文 `alt` 文本。上游 GitHub 链接保持指向原仓库，Notice 另行提供 `mcxqk` Fork 链接。

### 中文文档审校

README 修改必须遵循 `chinese-documentation`：

- 中文与英文、数字和单位之间保留空格。
- 中文语境使用全角标点，英文和代码使用半角标点。
- 专有名词保留英文并保持前后一致。
- 句子简短自然，不使用机翻式长句和不必要的被动语态。
- 标题层级连续，代码块声明语言。
- 所有相对链接指向真实文件。
- 外部链接通过 `localhost:7897` 代理检查可访问性。
- 两份文档均不得声称迁移已经完成。
- README 变更单独提交并推送到 `mcxqk` Fork。

## 测试设计

实现遵循红灯、绿灯、重构顺序。先添加失败测试并确认因目标行为缺失而失败，再写最少实现。

### 单元与并发测试

- Folia检测只能初始化一次。
- Entity和Region延迟最小为1 tick。
- SchedulerUtil关闭后拒绝新任务并取消已跟踪任务。
- Entity退休回调只按UUID清理。
- 同一实体不会建立重复被动任务。
- 箭矢粒子Set支持并发添加、遍历和删除。
- EnchantHolder支持不同线程并发更新和清理。
- Tooltip状态可在Packet线程读取而不调用Player实时方法。
- Placeholder剩余充能读取装备快照。
- BlockKey不受Location可变性影响。
- 恢复存储支持写入、重载、幂等删除、容量拒绝和损坏文件保护。
- 当前方块不是插件临时Material时不会被恢复逻辑覆盖。
- Registry和Manager关闭后不保留Player、Entity、World、Inventory或旧Plugin引用。
- 两个药水云附魔不包含同步传送路径并保留LingeringPotionSplashEvent调用。

测试使用JUnit 5。必须模拟Bukkit接口时使用Mockito，不引入依赖真实服务端启动的测试框架。

### 构建验证

环境没有Maven时，通过 `localhost:7897` 下载临时Apache Maven并在完成后删除程序目录。构建依赖下载同样使用该代理。

执行两轮完整测试和打包：

1. 公开Luminol API `26.2.build.711-stable`。
2. 本地Luminol API `26.2.build.726-stable`，通过临时安装和Maven属性覆盖。

检查最终JAR只包含目标模块、正确的paper-plugin.yml、两个作者，且不包含旧NMS类或provided依赖。

### 静态红线扫描

以下模式必须为零：

- 业务代码直接调用BukkitScheduler。
- 业务代码调用无所有者的旧runTask、runTaskLater或runTaskTimer。
- 同步 `.teleport(...)`。
- Future.get或CompletableFuture.join。
- Async任务读取实时Player、Entity、World、Block或Inventory。
- Global任务遍历世界实体或修改Region方块。
- HashMap、HashSet或ArrayList用作跨Region共享可变状态。
- 五个受限事件的监听器。
- 未关闭的周期任务、Packet监听器、PlaceholderExpansion或静态插件引用。

## 三遍审查

### 功能与范围

只支持Luminol 26.2。插件名称、主类、公开API、命令、权限、配置键和附魔行为保持不变。无关问题只记录，不顺手重构。

### Folia与性能

所有调度经过SchedulerUtil；无旧BukkitScheduler入口；无跨Region实时状态访问；无小于1 tick的Entity/Region调度；无Region线程阻塞。

### 生命周期与资源

所有任务可取消；所有缓存有固定边界或清理路径；所有监听器和外部处理器在禁用时关闭；旧引用不能在重载后继续业务回调。

## 结论边界

- 所有结论以Luminol `26.2.build.726-stable`为目标版本。
- 本设计是静态审查和编码规范，不等同于真实多Region运行测试。
- `folia-supported: true`只允许加载，不证明线程安全或功能完整。
- 最终证据包括静态扫描、单元测试、并发测试、两套API构建和JAR核对。
- 第三方Packet和Placeholder精确线程契约在当前源码不足，原状分类为`uncertain`，实现通过快照设计消除对该契约的依赖。
- 目标API JAR包含五个受限事件类符号，但插件没有对应监听器；缺少完整核心源码和实际事件触发证据时，不推断其运行时可调用性。
