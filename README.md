<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/logo.png" alt="ExcellentEnchants 项目横幅">
</p>

<p align="center">
  <a href="./README_EN.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.2-3C8527?style=flat-square" alt="Minecraft 26.2">
  <img src="https://img.shields.io/badge/Server-Luminol-8A2BE2?style=flat-square" alt="Luminol">
  <img src="https://img.shields.io/badge/Java-25-E76F00?style=flat-square" alt="Java 25">
  <img src="https://img.shields.io/badge/Scheduler-Folia-6A5ACD?style=flat-square" alt="Folia Region Scheduler">
  <img src="https://img.shields.io/github/license/mcxqk/ExcellentEnchants-spigot?style=flat-square" alt="GNU GPL v3">
  <img src="https://img.shields.io/badge/Status-Migration%20in%20progress-orange?style=flat-square" alt="迁移进行中">
</p>

# ExcellentEnchants

> [!WARNING]
> 这是由 `mcxqk` 维护的 Luminol 26.2 迁移分支。目前仍在迁移和验证，当前 `master` 分支不能作为已经完成 Folia 兼容的生产版本使用。目标核心为 Luminol `26.2.build.726-stable`。

ExcellentEnchants 是一款现代化的 Minecraft 服务端附魔插件，提供 80 多种接近原版体验、可高度自定义的附魔。插件通过服务端注册表接入附魔台、村民交易、随机战利品、钓鱼和生物装备等原版机制。

本仓库基于 [nulli0n/ExcellentEnchants-spigot](https://github.com/nulli0n/ExcellentEnchants-spigot) 迁移，目标是适配 Luminol 26.2 的 Folia Region 调度模型。

## 迁移状态

- **目标版本：** Minecraft 26.2
- **目标服务端：** Luminol 26.2
- **参考核心：** `26.2.build.726-stable`
- **Java：** 25
- **依赖版本：** NightCore 2.16.4
- **当前状态：** 迁移与验证进行中

`folia-supported: true` 只表示服务端允许加载插件，不代表线程安全或功能已经完整验证。编译成功、静态检查和单元测试也不能替代真实多玩家、多 Region 环境下的运行测试。

## 功能特性

### 原版式集成

- 自定义附魔通过服务端注册表接入原版机制。
- 支持铁砧、砂轮、附魔书和创造模式物品。
- 支持附魔台、村民交易、随机战利品、钓鱼和生物装备分布。
- 插件卸载后不会主动向物品写入私有替代附魔格式。

### 高度自定义

- 可调整附魔等级、权重、消耗、触发概率和效果参数。
- 可按世界禁用附魔。
- 可定义附魔冲突关系。
- 可配置附魔适用的物品集合和装备槽。
- 支持超过默认最高等级的附魔配置。

### 物品与装备

- 支持剑、斧、弓、弩、三叉戟、工具、盔甲和鞘翅。
- 支持自定义主物品集合与可用物品集合。
- 支持附魔充能、充能燃料和剩余充能 Placeholder。
- 支持可配置的附魔描述与彩色 Tooltip。

### 可选集成

- PlaceholderAPI
- PacketEvents
- ProtocolLib
- MythicMobs

## 效果展示

<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/enchants_ghast.gif" alt="Ghast 附魔效果">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/enchants_flamewalker.gif" alt="Flame Walker 附魔效果">
</p>

<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/enchants_thunder.gif" alt="Thunder 附魔效果">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/enchants_tunnel.gif" alt="Tunnel 附魔效果">
</p>

<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/anvil.gif" alt="铁砧附魔合并">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/books.gif" alt="创造模式附魔书">
</p>

<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/enchanting.gif" alt="附魔台集成">
  <img src="https://nightexpressdev.com/excellentenchants/img/gif/loot.gif" alt="随机战利品集成">
</p>

## 环境要求

| 项目 | 要求 |
|---|---|
| Minecraft | 26.2 |
| 服务端 | Luminol 26.2 |
| 调度模型 | Folia Region Scheduler |
| Java | 25 |
| NightCore | 2.16.4 |
| 当前状态 | 迁移中 |

本分支不维护旧版 Minecraft、Spigot 或普通 Paper 兼容性。

## 安装说明

> [!CAUTION]
> 当前仓库还没有经过完整验证的 Luminol 26.2 发布构建。请勿将当前 `master` 分支直接用于生产服务器。

迁移完成并发布后，需要将 ExcellentEnchants 与 NightCore 一同放入 Luminol 服务端的 `plugins` 目录，然后重新启动服务端。不要使用 `/reload` 代替完整重启。

## 从源码构建

目标构建环境：

- JDK 25
- Apache Maven 3.9+
- Git

目标构建命令：

```shell
git clone https://github.com/mcxqk/ExcellentEnchants-spigot.git
cd ExcellentEnchants-spigot
mvn clean package
```

计划输出：

```text
target/ExcellentEnchants-5.4.3.jar
```

构建配置仍在迁移中。只有 Luminol 26.2 迁移实现完成并通过测试后，上述命令才代表受支持的构建流程。

## 可选依赖

| 插件 | 用途 |
|---|---|
| [PacketEvents](https://spigotmc.org/resources/80279/) | 为物品 Tooltip 添加附魔描述，与 ProtocolLib 二选一 |
| [ProtocolLib](https://ci.dmulloy2.net/job/ProtocolLib/) | 为物品 Tooltip 添加附魔描述，与 PacketEvents 二选一 |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | 提供附魔相关 Placeholder |
| [MythicMobs](https://mythiccraft.io/index.php?resources/mythicmobs.1/) | 提供 MythicMobs 兼容逻辑 |

## 文档与链接

- [mcxqk Fork](https://github.com/mcxqk/ExcellentEnchants-spigot)
- [上游源码](https://github.com/nulli0n/ExcellentEnchants-spigot)
- [上游 Wiki](https://nightexpressdev.com/excellentenchants/)：内容可能尚未覆盖当前 Luminol 26.2 Fork。
- [迁移设计](./docs/superpowers/specs/2026-08-06-luminol-26-2-migration-design.md)
- [问题反馈](https://github.com/mcxqk/ExcellentEnchants-spigot/issues)
- [Discord](https://discord.gg/EwNFGsnGaW)
- [原作者网站](https://nightexpressdev.com/)

## 作者与致谢

- 原作者：NightExpress
- Luminol 26.2 迁移：cloudfl4re
- 上游项目：[nulli0n/ExcellentEnchants-spigot](https://github.com/nulli0n/ExcellentEnchants-spigot)

感谢原作者与所有上游贡献者维护 ExcellentEnchants、NightCore 及相关文档。

## 许可证

本项目沿用 [GNU General Public License v3.0](./LICENSE)。分发或修改本项目时，请遵守许可证条款并保留原项目归属信息。
