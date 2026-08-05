# ExcellentEnchants 双语 README 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 `README.md` 改写为符合 `chinese-documentation` 的完整中文主页，并创建带 Fork 声明、保留上游内容的 `README_EN.md`。

**架构：** 中文主页负责当前 `mcxqk` Fork 的事实、迁移状态、功能、环境、构建、依赖和归属信息；英文文档在顶部声明当前 Fork 状态，正文保留上游英文 README。两个文件通过相对链接互相切换，所有图片具有非空 `alt` 文本，且均不声称迁移已经完成。

**技术栈：** GitHub Flavored Markdown、HTML 图片标签、Shields.io、PowerShell、`curl.exe`、Git。

---

## 文件结构

- 修改：`README.md`
  - 完整中文主页。
  - 当前 Fork 和迁移状态的权威说明。
  - 中文排版、术语、链接和图片可访问性符合 `chinese-documentation`。
- 创建：`README_EN.md`
  - 顶部提供简体中文入口和 Fork notice。
  - Notice 以下保留上游英文 README 内容。
  - 上游兼容表明确只作历史参考。
- 参考：`docs/superpowers/specs/2026-08-06-luminol-26-2-migration-design.md`
  - 已批准的内容边界、目标版本、状态声明和验证要求。

## 任务 1：建立 README 失败基线

**文件：**
- 检查：`README.md`
- 检查：`README_EN.md`
- 参考：`docs/superpowers/specs/2026-08-06-luminol-26-2-migration-design.md`

- [ ] **步骤 1：运行结构检查并确认当前状态不满足规格**

运行：

```powershell
$errors = [System.Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath 'README_EN.md')) {
    $errors.Add('README_EN.md is missing')
}

$readme = Get-Content -Raw -Encoding UTF8 -LiteralPath 'README.md'

foreach ($required in @(
    '迁移状态',
    'Luminol 26.2',
    'NightCore 2.16.4',
    'cloudfl4re',
    './README_EN.md'
)) {
    if (-not $readme.Contains($required)) {
        $errors.Add("README.md is missing: $required")
    }
}

$missingAlt = [regex]::Matches($readme, '<img(?![^>]*\balt="[^"]+")[^>]*>')
if ($missingAlt.Count -gt 0) {
    $errors.Add("README.md images without alt: $($missingAlt.Count)")
}

$errors
if ($errors.Count -gt 0) {
    exit 1
}
```

预期：退出码为 `1`，至少报告 `README_EN.md is missing`、中文章节缺失和图片 `alt` 缺失。失败原因必须是目标文档尚未实现，而不是 PowerShell 语法错误。

- [ ] **步骤 2：记录现有上游英文 README 的完整快照**

运行：

```powershell
git show upstream/master:README.md | Set-Content -Encoding UTF8 -LiteralPath "$env:TEMP\ExcellentEnchants-upstream-README.md"
$upstream = Get-Content -Raw -Encoding UTF8 -LiteralPath "$env:TEMP\ExcellentEnchants-upstream-README.md"
$current = Get-Content -Raw -Encoding UTF8 -LiteralPath 'README.md'

Write-Output "UPSTREAM_LENGTH=$($upstream.Length)"
Write-Output "CURRENT_LENGTH=$($current.Length)"

if ($upstream -ne $current) {
    exit 1
}
```

预期：退出码为 `0`，证明修改前的 `README.md` 与 `upstream/master` 内容一致。临时快照仅用于核对英文保留内容，不加入 Git。

## 任务 2：改写完整中文主页

**文件：**
- 修改：`README.md`
- 参考：`docs/superpowers/specs/2026-08-06-luminol-26-2-migration-design.md`

- [ ] **步骤 1：用批准的完整中文内容替换 `README.md`**

写入以下完整内容：

````markdown
<p align="center">
  <img src="https://nightexpressdev.com/excellentenchants/banner.png" alt="ExcellentEnchants 项目横幅">
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
- **NightCore：** 2.16.4
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
````

- [ ] **步骤 2：运行中文主页结构检查**

运行：

```powershell
$readme = Get-Content -Raw -Encoding UTF8 -LiteralPath 'README.md'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($required in @(
    '# ExcellentEnchants',
    '## 迁移状态',
    '## 功能特性',
    '## 效果展示',
    '## 环境要求',
    '## 安装说明',
    '## 从源码构建',
    '## 可选依赖',
    '## 文档与链接',
    '## 作者与致谢',
    '## 许可证',
    'Luminol `26.2.build.726-stable`',
    'NightCore 2.16.4',
    'cloudfl4re',
    './README_EN.md'
)) {
    if (-not $readme.Contains($required)) {
        $errors.Add("README.md is missing: $required")
    }
}

foreach ($forbidden in @(
    '26.1.2',
    '迁移已完成',
    '已完全兼容',
    'Build Passing',
    'no errors, bugs, or crashes'
)) {
    if ($readme.Contains($forbidden)) {
        $errors.Add("README.md contains forbidden claim: $forbidden")
    }
}

$missingAlt = [regex]::Matches($readme, '<img(?![^>]*\balt="[^"]+")[^>]*>')
if ($missingAlt.Count -gt 0) {
    $errors.Add("README.md images without alt: $($missingAlt.Count)")
}

$errors
if ($errors.Count -gt 0) {
    exit 1
}

Write-Output 'README_CN_STRUCTURE=PASS'
```

预期：退出码为 `0`，输出 `README_CN_STRUCTURE=PASS`。

- [ ] **步骤 3：按 `chinese-documentation` 逐段人工审校中文**

逐项读取 `README.md` 并核对：中英文之间有空格；中文与数字之间有空格；中文语境使用全角标点；英文、路径和命令使用半角字符；PlaceholderAPI、PacketEvents、ProtocolLib、MythicMobs、GUI、ItemStack、Folia、Luminol、NightCore 等术语保持一致；没有机翻式长句；链接前后排版自然；标题不跳级；代码块标注 `shell` 或 `text`。

预期：清单全部满足；发现问题时只修改 `README.md` 文案，不改变批准的事实和章节边界。

## 任务 3：创建英文上游参考文档

**文件：**
- 创建：`README_EN.md`
- 参考：`README.md`
- 读取：`upstream/master:README.md`

- [ ] **步骤 1：通过 `apply_patch` 创建 `README_EN.md`**

文件必须先写入以下完整前置声明：

```markdown
<p align="center">
  <a href="./README.md">简体中文</a>
</p>

# ExcellentEnchants — Upstream README Reference

> [!WARNING]
> This `mcxqk` fork targets Minecraft 26.2 on Luminol/Folia and is still being migrated and verified. The current `master` branch is not a production-ready Luminol 26.2 release.
>
> The content below is preserved from the upstream README for historical and feature reference. Its compatibility table describes upstream releases and does not represent the compatibility status of this fork.

- **Fork:** [mcxqk/ExcellentEnchants-spigot](https://github.com/mcxqk/ExcellentEnchants-spigot)
- **Upstream:** [nulli0n/ExcellentEnchants-spigot](https://github.com/nulli0n/ExcellentEnchants-spigot)
- **Current target:** Minecraft 26.2, Luminol `26.2.build.726-stable`, Java 25, NightCore 2.16.4

---
```

分隔线后按 `git show upstream/master:README.md` 的顺序保留全部上游正文。只允许以下确定性修改：

1. 为横幅添加 `alt="ExcellentEnchants banner"`。
2. 为 `enchants_ghast.gif` 添加 `alt="Ghast enchantment"`。
3. 为 `enchants_flamewalker.gif` 添加 `alt="Flame Walker enchantment"`。
4. 为 `enchants_thunder.gif` 添加 `alt="Thunder enchantment"`。
5. 为 `enchants_tunnel.gif` 添加 `alt="Tunnel enchantment"`。
6. 为 `anvil.gif` 添加 `alt="Anvil integration"`。
7. 为 `books.gif` 添加 `alt="Enchanted books"`。
8. 为 `enchanting.gif` 添加 `alt="Enchanting table integration"`。
9. 为 `loot.gif` 添加 `alt="Random loot integration"`。
10. 将 `The following versions and platforms are supported:` 改为 `The following versions and platforms are supported by the upstream releases represented by this preserved content:`。
11. 将 `Anything not listed in the compatibility table is **NOT** supported.` 改为 `Anything not listed in the compatibility table is **NOT** supported by those upstream releases.`。
12. 将链接标题 `Github` 规范为 `GitHub`，链接地址仍指向上游。

除了以上 12 项，不改写上游英文功能说明、旧兼容表或原始链接。

- [ ] **步骤 2：验证英文 notice、图片和上游章节**

运行：

```powershell
$english = Get-Content -Raw -Encoding UTF8 -LiteralPath 'README_EN.md'
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($required in @(
    './README.md',
    'still being migrated and verified',
    'does not represent the compatibility status of this fork',
    'mcxqk/ExcellentEnchants-spigot',
    'nulli0n/ExcellentEnchants-spigot',
    '## Features',
    '### Integration & Compatibility',
    '### Enchantment Distribution',
    '### Customization & Control',
    '### Item & Equipment Support',
    '### User Interface & Presentation',
    '### Advanced Mechanics',
    '## Requirements',
    '|       26.1.2',
    '## Links'
)) {
    if (-not $english.Contains($required)) {
        $errors.Add("README_EN.md is missing: $required")
    }
}

foreach ($forbidden in @(
    'migration is complete',
    'fully Folia-compatible',
    'is a production-ready Luminol 26.2 release'
)) {
    if ($english.Contains($forbidden)) {
        $errors.Add("README_EN.md contains forbidden claim: $forbidden")
    }
}

$missingAlt = [regex]::Matches($english, '<img(?![^>]*\balt="[^"]+")[^>]*>')
if ($missingAlt.Count -gt 0) {
    $errors.Add("README_EN.md images without alt: $($missingAlt.Count)")
}

$upstream = git show upstream/master:README.md
$headings = [regex]::Matches(($upstream -join "`n"), '(?m)^#{2,3} .+$') |
    ForEach-Object { $_.Value }
$missingHeadings = $headings | Where-Object { -not $english.Contains($_) }
foreach ($heading in $missingHeadings) {
    $errors.Add("README_EN.md is missing upstream heading: $heading")
}

$errors
if ($errors.Count -gt 0) {
    exit 1
}

Write-Output "README_EN_STRUCTURE=PASS"
Write-Output "UPSTREAM_HEADINGS_PRESERVED=$($headings.Count)"
```

预期：退出码为 `0`，输出 `README_EN_STRUCTURE=PASS`，并报告全部上游二级和三级标题均已保留。

## 任务 4：审校、验证、提交并推送

**文件：**
- 修改：`README.md`
- 创建：`README_EN.md`

- [ ] **步骤 1：运行两个 README 的本地结构检查**

重新运行任务 2 步骤 2 与任务 3 步骤 2 的完整 PowerShell 命令。

预期：分别输出 `README_CN_STRUCTURE=PASS` 与 `README_EN_STRUCTURE=PASS`。

- [ ] **步骤 2：验证图片 `alt`、相对链接和标题层级**

运行：

```powershell
$errors = [System.Collections.Generic.List[string]]::new()

foreach ($file in @('README.md', 'README_EN.md')) {
    $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $file

    $missingAlt = [regex]::Matches($content, '<img(?![^>]*\balt="[^"]+")[^>]*>')
    if ($missingAlt.Count -gt 0) {
        $errors.Add("$file images without alt: $($missingAlt.Count)")
    }

    $relativeLinks = [regex]::Matches($content, '\]\((\./[^)#]+)(?:#[^)]+)?\)') |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object -Unique

    foreach ($link in $relativeLinks) {
        if (-not (Test-Path -LiteralPath $link)) {
            $errors.Add("$file missing relative link target: $link")
        }
    }

    $headings = [regex]::Matches($content, '(?m)^(#{1,6})\s+.+$')
    $previous = 0
    foreach ($heading in $headings) {
        $level = $heading.Groups[1].Value.Length
        if ($previous -gt 0 -and $level -gt ($previous + 1)) {
            $errors.Add("$file heading level jumps from $previous to $level")
        }
        $previous = $level
    }
}

$errors
if ($errors.Count -gt 0) {
    exit 1
}

Write-Output 'README_LOCAL_VALIDATION=PASS'
```

预期：退出码为 `0`，输出 `README_LOCAL_VALIDATION=PASS`。

- [ ] **步骤 3：通过代理检查全部外部链接**

运行：

```powershell
$urls = foreach ($file in @('README.md', 'README_EN.md')) {
    $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $file
    [regex]::Matches($content, 'https://[^\s)"<>]+') | ForEach-Object {
        $_.Value.TrimEnd('.', ',', '。', '，')
    }
}

$urls = $urls | Sort-Object -Unique
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($url in $urls) {
    $code = curl.exe -x http://localhost:7897 -L --max-time 30 -sS -o NUL -w '%{http_code}' $url
    if ($LASTEXITCODE -ne 0 -or $code -eq '000' -or $code -eq '404') {
        $failures.Add("$code $url")
    }
    else {
        Write-Output "$code $url"
    }
}

if ($failures.Count -gt 0) {
    Write-Output 'LINK_FAILURES'
    $failures
    exit 1
}
```

预期：所有链接返回非 `000`、非 `404` 状态。站点若对自动请求返回 `403`，在交付报告中记录“服务端拒绝自动检查”，不得描述为已经成功访问页面内容。

- [ ] **步骤 4：运行 Git 差异检查**

运行：

```powershell
git diff --check
git diff -- README.md README_EN.md
git status --short
```

预期：`git diff --check` 退出码为 `0`；差异只包含 `README.md` 和新建的 `README_EN.md`；没有临时快照或无关文件。

- [ ] **步骤 5：提交 README 变更**

运行：

```powershell
git add -- README.md README_EN.md
git diff --cached --check
git commit -m "docs: add Chinese README and upstream English reference"
```

预期：提交成功，提交只包含 `README.md` 和 `README_EN.md`。

- [ ] **步骤 6：推送前核对目标并推送到 `mcxqk` Fork**

运行：

```powershell
$origin = git remote get-url origin
$status = git status --porcelain

if ($origin -ne 'https://github.com/mcxqk/ExcellentEnchants-spigot.git') {
    throw "Unexpected origin: $origin"
}

if ($status) {
    throw 'Working tree is not clean'
}

git -c http.proxy=http://localhost:7897 -c https.proxy=http://localhost:7897 push origin master
```

预期：推送目标为 `https://github.com/mcxqk/ExcellentEnchants-spigot.git`，`master` 更新成功。

- [ ] **步骤 7：验证远端提交**

运行：

```powershell
$local = git rev-parse HEAD
$remoteLine = git -c http.proxy=http://localhost:7897 -c https.proxy=http://localhost:7897 ls-remote origin refs/heads/master

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$remote = ($remoteLine -split '\s+')[0]

Write-Output "LOCAL_HEAD=$local"
Write-Output "REMOTE_HEAD=$remote"

if ($local -ne $remote) {
    exit 1
}

git status --short --branch
```

预期：`LOCAL_HEAD` 与 `REMOTE_HEAD` 完全相同，状态为 `master...origin/master` 且工作树干净。
