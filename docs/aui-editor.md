# AUI 游戏内分组编辑器 — 设计共识

> 本文件由一轮完整追问（grill）产出，记录**已敲定的决策**与**未解决的开放问题**。
> 基线事实均来自源码核对，不是推测。

## 0. 一句话

用 ApricityUI（AUI）在游戏内做一个**点选即分组、当场看到折叠**的创造栏分组编辑器，
数据写进一个可写目录，该目录被注册成**虚拟资源包**，因此组定义与语言文件都走标准资源管线。

## 1. 事实基线

### BCT 运行链路（已核对）

| 环节 | 位置 | 事实 |
|---|---|---|
| 定义加载 | `data/CreativeGroupLoader.java:72` | 只从**客户端资源包** `assets/<ns>/bedrock_creative_tabs/creative_group/*.json` 读；F3+T 重载，`/reload` 无效 |
| 数据模型 | `data/CreativeGroup.java:22` | `entries`(≥1) + `icon`(可选，留空取首个成员) + `creative_tabs`(≥1，禁 `minecraft:search`) + `priority`(默认 0) |
| 折叠 | `runtime/CreativeGroupService.java:53` | `BuildCreativeModeTabContentsEvent` **最低优先级**：按页过滤 → priority 高者胜 → 首成员位置插图标 → 成员以 `PARENT_TAB_ONLY` 移除（搜索页仍可搜到） |
| 点击展开 | `runtime/CreativeGroupClient.java:31` | 对 `menu.items` 的**副本**就地开合；展开是"一次访问"的属性，重开即折叠 |
| 重建 | `runtime/CreativeTabRebuild.java:21` | 清 `CACHED_PARAMETERS` + `tryRebuildTabContents` 才能让新定义生效 |
| 图标命名 | `runtime/CreativeGroupService.java:100` | `Component.translatable(id.toLanguageKey("creative_group"))` —— **名字来自 lang** |

### 三个必须先知道的坑

1. **同优先级 = 顺序不确定**。`groups = Map.copyOf(prepared)`，注释里的"加载顺序兜底"实际不成立。
2. **物品宇宙必须在折叠前取**。折叠会把成员移出 parent 列表；在 `:58` 那个时点做快照，否则重载后越折越少。
3. **图标名字来自 lang**，所以可写目录必须能被当作资源包加载（见 §3）。

### AUI 1.2.7 能力面（已拆 jar 核实）

- 入口：`ApricityUI.openScreen(path)` / `ApricityUI.menu(player, path)` / `createWorldWindow(...)`
- DOM + CSS + Rhino JS：`Window` 带 `localStorage`、`fetch`、`setTimeout`、`requestAnimationFrame`、`ResizeObserver`、自定义事件
- 声明式元素：`<slot><item>minecraft:diamond</item></slot>`、`<ingredient>`、`<recipe>`、`<container bind="player">`、`canvas/svg/input/select/richtext`
- 扩展点：`AuiBindingBuilder`(SPI)、`ApricityUIRegistry.scanPackage`、`ApricityJS.evalGlobal`
- 页面解析顺序（`Loader.getResourceStream` + `ClientLoader.getResourceStream`）：
  1. AUI 自己的 dev 源码根（开发 AUI 时用）
  2. **`<gameDir>/apricity/<path>`** ← 文件系统，可写、可热重载
  3. `assets/apricityui/apricity/<path>` ← 资源包 / mod jar
- 引擎禁用语法：`color-mix()`、`:has()`、`@import`、`clip-path`

## 2. 已敲定的决策

| # | 议题 | 决定 |
|---|---|---|
| 1 | 完成形态 | 即时编辑器：点选物品 → 成组/改属性 → **当场看到折叠**；导出是收尾动作 |
| 2 | 落盘 | 改 BCT，新增可写目录 |
| 3 | 生效机制 | 该目录注册为**虚拟资源包**（`AddPackFindersEvent` + `PackResources`），组与 lang 都走标准资源管线 |
| 4 | 交互面 | 独立 AUI 工作台，物品网格与折叠预览都在 AUI 内自建 |
| 5 | 物品范围 | 「可折叠全集」= 所有**非 search 页** parent 内容并集（= BCT 真正能折的东西） |
| 6 | 条目形式 | 默认**自动收敛**到精确相等的 item_tag，否则写显式 id 列表；面板可改，并提示"该标签额外纳入 N 个未选物品" |
| 7 | 正则 | 从选中 id 自动推正则，但只作为面板里的**建议**，需人工确认 |
| 8 | 页签归属 | 从选中物品出现过的页取**并集**默认勾选，可增减；0 勾不允许保存 |
| 9 | 冲突与 priority | 实时提示 + 自动排唯一 priority（成员更少者=更具体=更高）；面板可见可改 |
| 10 | 外来组 | 只读 + 「复制为我的一份」写进可写目录覆盖；徽标区分来源 |
| 11 | 工程形态 | 同仓库；AUI 走 `compileOnly` + `ModList.isLoaded` 运行期探测；AUI 触点集中在独立类里 |
| 12 | 属性面板 | 面上：组名(中/英)、图标、页签、priority、条目形式；折叠区：正则原文、组件 id、命名空间/路径、**原始 JSON 双向编辑** |
| 13 | 进出口 | 全形态双向：散 JSON（单个/多个）+ 目录 + 资源包 zip；每个组标注来源包 |
| 14 | 内置兼容组 | BCT 自带那批 JSON 单列一类（"BCT 内置"），只读，可复制覆盖 |
| 15 | 入口 | 创造栏按钮 + 可自定义快捷键 + `/bct edit` 命令 |
| 16 | 多人 | **不做**服务端同步；纯本地客户端生效，分享靠导出 |

**首版不做**：候选自动生成、跨 mod jar 扫描、组嵌套、自定义图标贴图。
**首版要做**：正则自动推理、撤销/重做、界面中英双语。

## 3. 数据流

```
玩家点击（AUI 工作台）
      │  选中集合 + 属性
      ▼
GroupDraft  ──►  收敛(name/tag/regex/页签/priority)
      │
      ├─ 乐观路径：直接注入内存组表 + 重建创造页  → 当场看到折叠
      │
      └─ 持久化：写 <gameDir>/config/bedrock_creative_tabs/pack/
                    pack.mcmeta
                    assets/<ns>/bedrock_creative_tabs/creative_group/*.json
                    assets/<ns>/lang/{zh_cn,en_us}.json
                        │
                        ▼
                虚拟资源包（AddPackFindersEvent）→ 下次重载自然收敛
```

## 4. 开放问题

| 优先级 | 问题 | 当前倾向 |
|---|---|---|
| **P0** | 虚拟资源包的优先级通常低于普通资源包，与"我的组覆盖同名组"冲突 | 在 `CreativeGroupLoader.apply()` 后加一层约 20 行的**同 id 覆盖**（虚拟包优先），不依赖 pack 顺序 |
| **P0** | "当场生效"要等重载，延迟几百毫秒且异步 | 乐观路径：先注入内存表立即重绘，后台写盘 |
| **P1** | AUI 一页 500+ `<slot>` 的性能、点击语义、脱离 container 渲染任意 `ItemStack` | 正在实测（探针页） |
| **P1** | `<gameDir>/apricity/` 热重载是否可靠 | 正在实测 |
| **P2** | 组 id 命名空间与命名规则 | 暂定 `bct_user:<slug>`；重命名会让已导出包失联，需提示 |
| **P2** | 已丢失的 `BctExporter` 源码（编译产物仍在） | 其"主线程抓数据 + 后台写盘 + 先写 .tmp 再原子改名"值得恢复复用 |

---

## 5. 第一轮实测结论（2026-10-11，真机）

实测环境：`D:\mc\.minecraft\versions\26.1.2-模组测试26.1.2.112`（PCL 离线账号 `lying_ice`），
AUI 1.2.7 装入 `mods/`，BCT 重新构建后覆盖，`--quickPlaySingleplayer "新的世界"` 直接进世界。

### 5.1 ★ 架构级发现：没有 KubeJS 就没有页面脚本

```
[AUI JS] Rhino not present; page scripts are skipped (pages still render)
```

AUI 探测的是 `dev.latvian.mods.rhino.Context`，也就是 **KubeJS 自带的 Rhino**。没有它，页面里所有 script 标签
整段跳过（页面照样渲染）。实测确认：第一版探针页用 JS 建 500 个格子，结果是空网格、计数器停在 0。

**决定：不走 KubeJS，改为 Java 驱动 DOM。** AUI 的 DOM 本身就是 Java 对象模型 ——
`Document.createElement/createTextNode/querySelector/appendChild`、
`Element.setInnerHTML/setAttribute/getClassList/addEventListener/click()` 全是公开 Java API，
足够替代页面脚本；CSS 仍然负责全部外观。

好处：BCT 不必硬依赖 KubeJS（那是个面向整合包作者的重量级 mod），AUI 依然保持可选。
代价：页面只是壳子，内容与交互都在 Java 里写；热重载只能覆盖 CSS 与结构，覆盖不了逻辑。

### 5.2 已实测通过

| 能力 | 证据 |
|---|---|
| 从 `<gameDir>/apricity/<path>` 加载页面（可写、可热重载） | `[AUI Document] create timing path=bct_editor/index.html total=49ms` |
| Java 建 DOM：500 个 slot + item | `[AUI Layout] first commit ... elements=1015 total=332ms` |
| `<slot><item>id</item></slot>` 渲染**真实物品图标** | 截图：石 / 圆石 / 木板 / 原木 / 钻石 / 绿宝石等全部正确 |
| 事件链 click → 监听器 → classList.toggle → 计数更新 | 计数器显示「已渲染 500 格 / 已选中 10」，由 `Element.click()` 派发 |
| 大数量首帧布局耗时 | 1015 个元素 332ms（可接受；3000 格尚未测） |
| 中文文本渲染 | 标题与统计栏中文正常 |

### 5.3 已知问题 / 待修

1. **选中态样式不生效**：`classList.toggle("selected")` 返回 true（计数器从 0 变 10），
   但 `slot.selected` 的绿框与绿底没出现在画面上。怀疑类名变更后缺少样式/布局失效通知。
   下一步：改用 `setAttribute("class", …)`，或找 AUI 的显式失效入口。
2. **合成输入进不了游戏**：`SendKeys` 与 `keybd_event`（含 ALT 抢焦点、`AttachThreadInput`）
   都拿到了前台焦点，但按 T 开不了聊天栏、`/bct edit` 也执行不了。怀疑是 IMBlocker（输入法冲突修复）吃掉了注入事件。
   影响：**鼠标点击无法自动化验证**，只能用 `Element.click()` 走同一条事件派发路径做等价验证。
3. **底栏不可见**：`body{height:100%}` + `.grid{flex:1}` 的组合下 bottom bar 没有出现在窗口底部。
4. **AUI 自身报错**：每次重载 `[AUI Document] cannot create document: template is missing path=devtools/toast.html`
   （AUI 自己的资源缺失，无害，但会刷日志）。
5. **过时 API**：`ApricityUI.openScreen` 已标记 deprecated，应改用 `ApricityUI.screen`。
6. 注释里出现 script 标签的**字面文本**会让 AUI 记一条 unmatched script tag 警告 —— 写注释要避开。

### 5.4 为验证临时加的东西（收尾时要处理）

| 东西 | 位置 | 处置 |
|---|---|---|
| `BctEditorDevAutoOpen` | `editor/BctEditorDevAutoOpen.java` | 哨兵文件触发自动开屏；对开发有用，但要在文档写明，发布前考虑移除或加开关 |
| 哨兵文件 | `<gameDir>/apricity/bct_editor/autopen` | 删掉即关闭自动开屏 |
| 程序化点击演示 | `BctEditorAui` 里 `for (index += 50) built.get(index).click()` | 真正点击验证通过后删除 |
| 快捷启动脚本 | `.aui-probe/LaunchQuick.bat` | 仓库外，仅本机验证用 |

### 5.5 实例改动（可回滚）

```
mods/apricityui-1.2.7.jar            ← 新增（来自 gradle 缓存）
mods/bedrock_creative_tabs-1.0.jar   ← 被重新构建的版本覆盖
bct-backup-20261011/                 ← 原 jar 备份
apricity/bct_editor/                 ← 热重载用的页面副本
apricity/bct_editor/autopen          ← 删掉即关闭自动开屏
```

---

## 6. 第二轮实测：UI 骨架与 AUI 的真实脾气（2026-10-11）

界面对齐了，但代价是踩穿了一层 AUI 的隐性契约。以下每条都是**实测结论**，不是推测。

### 6.1 ★ 布局画布只有 1138×643 逻辑像素

这台机器：全屏 2560×1440、**GUI 缩放 5**、Windows DPI 150%。

| 量 | 值 | 怎么测出来的 |
|---|---|---|
| AUI 排版画布 | **1138 × 643** | 一个 CSS 在 `x=919` 的按钮，屏幕上落在 `x≈1385` → 比例 1.5；而 `width:100%` 的导航栏正好铺满 |
| 屏幕像素 = 画布 × 1.5 | 1.5 | 同上 |
| 截图工具输出 | 1707×961 | `GetWindowRect` 被 DPI 虚拟化的结果（2560/1.5） |

**教训**：按 1700px 去设计，整张版式会溢出画面右侧；按 1138×643 设计正好铺满。
**百分比是有效的**——我一度误判为"不支持"，实际是画布宽度判断错了。

### 6.2 AUI 的 flex 会吃掉兄弟节点

凡带 `flex: 1` 的元素，其**后续兄弟全部被推出可视区**。
所以骨架一律 `position:absolute` + `left/top/width/height`（px），不用 flex 分栏。

### 6.3 classList 的内部状态与属性不同步

```
classList.toggle("selected")     -> true（计数确实变了）
getAttribute("class")            -> "selected"
classList.contains("selected")   -> false      ← 同一个元素，同一时刻
```

用 class 驱动选中态**画面上完全不生效**。改用 `setInlineStyleProperty` 的内联样式后立刻正常
（截图里的绿框格子就是它）。**状态一律走内联样式，class 只用于静态外观。**

### 6.4 只有 ClientLoader.reloadResources() 能真正重读文件

`removeDocument` + `openScreen` 只是把**第一次读到的字节**重新播放一遍：日志里
`firstPath=false`、元素数一字不变、画面毫无变化。
`ClientLoader.reloadResources()` 才是引擎自己的重载——它清 `cachedFinalStaticResources`、
`CSS.clearCompiledStylesheets()`、`Selector.clearCompiledCache()` 再 `HTML.scan()`。
接上它之后，改页面文件 + 写一个 `reopen` 哨兵文件，**2 秒**就能看到新页面（HTML 与 CSS 都热）。

### 6.5 合成输入进不了这个实例的 GLFW

`SendKeys` 与 `keybd_event`（含 ALT 抢焦点 + `AttachThreadInput`）都能拿到前台焦点，
但按 T 开不了聊天栏、`/bct edit` 执行不了。`command_history.txt` 的 mtime 证明命令从未提交。
疑犯是 IMBlocker（输入法冲突修复）。**影响：真实鼠标点击无法自动化验证**，
只能用 `Element.click()` 走同一条事件派发路径做等价验证。

替代方案（已落地）：`<gameDir>/apricity/bct_editor/autopen` 进世界自动开屏，
`reopen` 立刻重开——文件触发器，任何输入路径都吃不掉。

### 6.6 物品全集接的是真数据

`CreativeGroupService` 在 `fold()` 里、**任何移除发生之前**，把 `event.getParentEntries()`
快照进 `UNIVERSE`；编辑器取所有非 search 页的并集。实测 **1755 个可折叠物品**，
3547 个 DOM 元素首帧布局约 1.7s（其中大部分是 1755 个物品图标的首次绘制）。

### 6.7 当前界面

```
nav     创建栏分组 | BCT 徽标 | 编辑/分组/导出 | 1755
items   可折叠物品 · 搜索框 · 重载物品 · 1755 个 30px 物品格（可滚动）
side    已选 · 大号计数 · 创建分组 · 清空选择 · 选中态图例
status  ApricityUI · Java 驱动 DOM · 显示 1755 / 1755
```

### 6.8 仍未做

- 搜索框已接线（`Input.getValue()` + `input` 事件），但**尚未在真机上用键盘验证**（同 6.5）。
- 「创建分组」目前只把选中数写进底栏；条目收敛、写盘、乐观注入折叠都还没接。
- 页面尺寸写死 1138×643；换台机器/GUI 缩放会错位。应改为 Java 在运行时读
  `ApricityViewport.resolve(path, window)` 拿到 `layoutWidth/renderScale`，再按真实画布写内联尺寸。

---

## 7. 第三轮：创造页签切换器 + 条目收敛（2026-10-11）

### 7.1 页签切换器（用户点名要的）

从左到右按 `BuiltInRegistries.CREATIVE_MODE_TAB` 注册顺序生成，跳过 `minecraft:search`、
跳过没有可折叠物品的页：

| 元素 | 做法 |
|---|---|
| 页签图标 | `CreativeModeTab.getIconItem()` 的 item id，塞进 `<slot><item>id</item></slot>` |
| 页签名 | `CreativeModeTab.getDisplayName().getString()`（客户端随语言变化） |
| 选中态 | 内联样式（`classList` 不可信，见 6.3） |
| 条带 | `overflow-x: auto` + `flex-wrap: nowrap`，实测可横向滚动 |

实测：**1755 个可折叠物品，13 个页签**（全部 + 12 个真实创造页）。

### 7.2 选中模型改成跨页签保持

`LinkedHashSet<String>` 存物品 id。切页签不清空选择——这正是"从多个页挑东西组成一个组"的用法。
选中态一律走内联样式；重绘时按 id 回填，所以跨页签、跨搜索都能保持高亮。

### 7.3 条目收敛已跑通（自检证据）

自检哨兵 `apricity/bct_editor/selftest` 存在时，程序化选中全部 `*_wool` 并生成草稿：

```
chosen=16
draft={ "entries": "#minecraft:wool",
        "creative_tabs": ["minecraft:colored_blocks", "minecraft:redstone_blocks"],
        "priority": 0 }
```

收敛规则（`inferExactTag()`）：
1. 取所有选中物品**共有的** item tag 作为候选；
2. 在全集范围内排除候选——只要该 tag 还覆盖了任何**没被选中**的物品就淘汰；
3. 活下来的就是"不多不少正好等于选中集合"的标签；一个都不剩则退回枚举 id 列表。

页签推断（`tabsContaining()`）：选中物品出现过的页取**并集**，对齐访谈决策 #8。

### 7.4 仍未接上的一环

**写盘 + 乐观注入折叠**。现在草稿只是显示出来，点「生成分组草稿」不会改变游戏里的创造页。
这需要：
1. 组的落盘位置（虚拟资源包目录 + `pack.mcmeta` + `creative_group/*.json` + `lang/*.json`）；
2. `AddPackFindersEvent` 注册该目录；
3. 把草稿注入 `CreativeGroupLoader` 的组表并触发 `CreativeTabRebuild.rebuild()`，让它当场折叠。

### 7.5 当前交付物

| 文件 | 说明 |
|---|---|
| `editor/BctEditor.java` | 入口，不引用任何 AUI 类 |
| `editor/BctEditorAui.java` | 全部 AUI 触点：页签条、网格、选中、收敛、草稿 |
| `editor/BctEditorCommands.java` | `/bct edit` |
| `editor/BctEditorDevAutoOpen.java` | 开发哨兵：`autopen` 自动开屏 / `reopen` 热重载 |
| `runtime/CreativeGroupService.java` | 新增折叠前快照（`universe()` / `universe(tab)` / `builtTabs()`） |
| `assets/apricityui/apricity/bct_editor/*` | 页面与样式（发在 `apricityui` 命名空间下——AUI 只认这个路径） |

---

## 8. 第四轮：闭环打通 + 参考 BCTtools 的数据层（2026-10-11）

### 8.1 从 BCTtools 取什么、不取什么

按要求只参考**数据层契约**，运行时逻辑自己写：

| 取 | 用在哪 |
|---|---|
| 资源包布局：`assets/<组ns>/bedrock_creative_tabs/creative_group/<path>.json` | `BctGroupStore` 的目录结构 |
| lang 写在**包自己的命名空间**，键为 `creative_group.<组ns>.<path>` | `BctGroupStore.writeLang()` |
| 校验规则：id 合法 / entries 非空 / tabs 非空且非 search / regex 可编译 / priority 是 int32 | 收敛与保存时的前置约束 |
| 冲突判定 = 同页 **且** 共享成员；簇内按成员数升序，更具体者 priority 更高 | `planPriority()` 的规则来源 |

**不取**：它的候选生成、并查集聚簇、状态机、DOM 预览那一整套运行时——本项目的编辑器是 Java 驱动 DOM，
数据流完全不同，照搬只会把两套模型搅在一起。

### 8.2 磁盘格式（`BctGroupStore`）

```
config/bedrock_creative_tabs/
  groups/bct_user/wool.json          ← 组定义，与资源包里的文件逐字节同构
  lang/zh_cn.json                    ← "creative_group.bct_user.wool": "wool"
  lang/en_us.json
```

写盘走"先写 .tmp 再原子改名"，崩溃不会留下半个 JSON。
磁盘格式刻意做成**与资源包完全一致**，所以将来"导出"就是打包这个目录，不需要转换。

### 8.3 注入与合并

`CreativeGroupLoader` 现在维护三层：`packed`（资源包）/ `user`（可写目录）/ `groups`（合并结果）。
`user` 按 id 覆盖 `packed`——这是访谈里定的 P0：**不依赖虚拟资源包的排序**，覆盖在同 id 上直接生效。
图标名字优先取用户存储（`BctGroupStore.nameOf`），没有才回退到 `translatable`。

### 8.4 闭环实测证据（不是截图，是读回来的真值）

```
BCT editor: loaded 1 group(s) from .../config/bedrock_creative_tabs/groups
Loaded 90 bedrock creative group(s)
BCT editor selftest: chosen=16
  draft={ "entries": "#minecraft:wool",
          "creative_tabs": ["minecraft:colored_blocks","minecraft:redstone_blocks"],
          "priority": 0 }
BCT editor: applied bct_user:wool priority=1 ... folded=16
BCT editor selftest: colored_blocks has 12 entries, 12 group icon(s), 0 loose wool item(s)
```

最后一行是决定性的：**16 个羊毛从创造页消失，该页只剩 12 个组图标**。
（12 = BCT 内置组把染色方块页的 12 个族全折了：羊毛/地毯/陶瓦/混凝土/混凝土粉末/染色玻璃/染色玻璃板/
带釉陶瓦/潜影盒/床/蜡烛/旗帜。这也是为什么"新建组"很容易撞上内置组。）

### 8.5 这一轮修掉的两个真 bug

1. **同等具体时判自己输** → 新建的羊毛组优先级 -1，被内置 `wools` 组抢走全部成员，`folded=0`，
   玩家点了按钮却什么都没发生。修法：同等具体视为新组胜出（否则只能靠哈希顺序决定，等于没决定）。
2. **重新应用同一个组时把自己当冲突对象** → 每次编辑 priority 自增 1。修法：`planPriority` 跳过同 id 的已有组。

### 8.6 仍未做

1. **虚拟资源包**（`AddPackFindersEvent`）：目前持久化走 config 目录，够用；但"导入别人给的 zip 资源包"
   还需要它。
2. **导出**：把 `config/bedrock_creative_tabs` 打成 zip（补一个 `pack.mcmeta`）。注意 26.1 的格式是
   `pack_format` + `min_format/max_format`；用旧的 `supported_formats` 会触发
   "missing mandatory fields min_format and max_format" 警告（实例里那个 BCT_groups.zip 就是如此）。
3. **属性面板**：组名/图标/页签增删/priority 手动覆盖还没上界面。
4. **撤销/重做**。
5. 画布尺寸仍是写死的 1138×643，应改为运行时读 `ApricityViewport.resolve()`。

---

## 9. 第五轮：物品与格子对不上（2026-10-11）

用户报"物品不跟槽位内物品不一样"。查下来是**两个独立的 bug** 叠在一起。

### 9.1 修好：物品被锚在格子左上角

`Slot.resolveSlotSizeHint()` 的取值顺序（反编译确认）：

1. `--aui-slot-size` 自定义属性
2. **计算样式的 `width/height` 取 max** ← 我设的是 30px
3. `size` / `slot-size` 属性
4. 兜底值

问题在于：**底板按这个尺寸画，物品却锚在盒子原点画**。30px 的盒子配 16px 的物品，图标就贴在左上角、
看起来属于隔壁格子。修法是把两者统一到原版槽位尺寸 **18px**，原点自然重合。
Java 侧同时设 `size="18"` 属性，保证 hint 与 CSS 不会再各说各话。

### 9.2 修好：网格和页签条会是空的

`ApricityUI.getDocument(path)` 返回的是**列表**，同一路径下可以有多份：重载或重开会留下旧的。
我原来取 `get(0)`，于是**填充了一份没人在看的文档，屏幕上那份还是空壳**。日志是铁证：

```
18:39:40  elements=3600   ← 完整
18:41:01  elements=38     ← 空壳
18:41:22  elements=38     ← 空壳
18:41:32  elements=3600
```

修法：优先用可见屏幕关联的文档（`AuiLinkedScreen.getLinkedDocument()`），取不到才回退到列表的**最后一份**。

### 9.3 没修好：骨架不适配窗口尺寸

这是本轮唯一没解决的问题，把已确认的数字记下来，避免下次重复探索：

| 量 | 实测值 |
|---|---|
| `ApricityViewport.resolve().layoutWidth/Height` | **1707 x 1015** |
| `Minecraft.getWindow()` | 1280 x 760（guiScale=3，GUI 坐标 427x254） |
| `.bct-app`（CSS `width:100%;height:100%`）的 `getBoundingClientRect()` | **1067 x 634** |
| 插入 100px 探针元素后量出的比值 | **rect 单位 / CSS 像素 = 1.0** |

也就是说 1067x634 就是 CSS 像素下的画布，但：

- 用 `setInlineStyleProperty("width","757px")` 设置卡片宽度**没有生效**（样式表里的百分比仍然赢）；
- 样式表里的百分比基准又和浏览器不一致（卡片 69%、其中网格 98%，结果网格和窗口一样宽）。

结论：**AUI 的层叠与尺寸解析规则还没吃透**。下一步应该在一个最小页面上做对照实验（一个父盒子 + 一个 50% 子盒子，
分别用内联和样式表设宽，量 rect），一次把规则钉死，再回来改骨架。在那之前骨架只能凑合：
窗口够宽（≥1000px 画布）时侧栏可见，小窗口下侧栏会被挤出画面。

### 9.4 这一轮的方法论教训

我在同一类问题上连续猜了五次（百分比 → 固定像素 → 百分比 → viewport 单位 → 探针标定），每次都要
重建 + 重启（约 2 分钟）。**正确顺序是先写一个最小对照页把规则量出来，再动主界面**。
已把这个对照实验列为下一步第一件事。

---

## 10. 第六轮：线程模型钉死 + 界面修正（2026-10-11）

### 10.1 ★ AUI 事件回调的线程模型（反编译 jar 验证，不再是猜想）

拆 `apricityui-1.2.7.jar` 逐类核对调用链，证据链完整：

| 环节 | 证据 |
|---|---|
| 鼠标按下/抬起 | `client/Client.mouseButton` 处理 NeoForge `InputEvent.MouseButton.Pre` → `render/Operation.onMouseDown` → `Window.createMouseEvent` → DOM 监听器 |
| 鼠标移动 | `Client.mouseMove` 挂在 `RenderFrameEvent.Pre`（每帧、渲染线程） |
| 键盘 | `InputEvent.Key` / `ScreenEvent.KeyPressed.Pre` / `CommonKeyboardHandlerMixin`（charTyped） |
| 屏幕生命周期 | `ApricityScreen extends Screen`，`Document.create` 发生在 `init()`（渲染线程） |
| 异步任务 | `ClientScheduler.setTimeout/setInterval` 用后台 `java.util.Timer` 执行后**反射跳回** `Minecraft.getInstance().execute`——引擎自己把异步工作搬回客户端线程 |

**结论：所有 DOM 事件监听器（click/input/key）都在 Minecraft 渲染主线程上被调用。**
编辑器在监听器里做 `CreativeTabRebuild.rebuild()`、`setScreen`、写盘都是主线程语义，无需任何线程跳转。
这也是 `ClientScheduler` 存在的原因：页面脚本的 `setTimeout` 从 Timer 线程被搬回主线程。

### 10.2 界面修正（对应第五轮截图发现的问题）

| 问题 | 修法 |
|---|---|
| 卡片头「可折叠物品/先选页签」被页签条盖住 | 根因是 CSS 重叠：head 占 `0–8%`，tabstrip 从 `top:1%` 开始。页签条移到 `top:8%`，过滤行、网格依次下移 |
| 导出页签不可见 | `1755` 计数徽标绝对定位在 `left:92%`，正好压在它上面。徽标移进卡片头（文档流内），导航不再有绝对定位元素 |
| 页签按钮文字截断换行 | 76px 固定宽改 `width:auto; min-width:60px`，label `white-space:nowrap`——按钮按文字自适应，条带本来就支持横向滚动 |
| 侧栏大计数画成绿色图形 | 44px → 32px（真机待复核，若仍异常则是 AUI 大字号字形问题，需另查） |
| 底栏文字贴边 | `line-height: 2.2 → 1.8`（截图裁切与真实裁切无法区分，一并收紧） |

### 10.3 交互语义与数据修正

1. **按钮与行为一致**：「生成分组草稿」实际是直接落盘+注入，改名「创建分组并折叠」；面板 `#draft`
   现在显示**实际写入的 JSON（含真实 priority）**——由 `draftJson(entries, tabs, priority)` 在
   优先级算完之后生成。语义分裂的 `buildDraft()`（只在 selftest 路径跑、文案还说"写盘尚未接上"）已删除。
2. **组 id 防静默覆盖**：收敛到 tag 时 id 只取 tag 的 path，`minecraft:planks` 和 `c:planks` 会撞成同一个
   `bct_user:planks`。`uniqueId()` 现在检查：同 id 已被**不同 entries** 的组占用时依次尝试
   `path-2`、`path-3`…；同 entries 则复用（那是编辑同组）。
3. **折叠数跨页签统计**：`folded` 原来只读第一个页签，被内置组抢走就误报"没折到"；现在遍历组所在的全部页签取最大者并报实际落点。
4. **搜索匹配名称**：placeholder 说"id 或名称"，实现只匹配 id。`refreshUniverse` 顺手缓存
   `getHoverName()`，`matches()` 对 id 和本地化名称都做 contains。

### 10.4 本轮验证（真机，jar 已部署到测试实例）

**布局**（1302x816 全窗口截图）：

- 卡片头「可折叠物品 [436] 先选页签，再点格子」可见，页签条在它下方，不再重叠；
- 导航 编辑/分组/导出 三个页签齐全，计数徽标移入卡片头后无遮挡；
- 页签按钮按文字自适应宽度，「X光与超级工具：重生」完整单行显示，条带可横向滚动；
- 底栏完整可读，不再贴边裁切；
- 侧栏大计数仍是一个绿色图形——**确认为 OreDisplay 字体的 "0" 字形**，不是渲染 bug，无需再查。
- **真实鼠标点击验证通过**：截图时当前页签已是被真人点过的「建筑方块」，草稿面板显示
  「先点选物品，再创建分组」（按钮被点击后的文案）。此前"合成输入进不了 GLFW"只影响自动化注入，不影响真人操作。

**闭环**（selftest 哨兵 + reopen 触发，日志读回）：

```
BCT editor: 1755 foldable item(s), 13 tab(s)
BCT editor: applied bct_user:wool priority=1 tabs=[colored_blocks, redstone_blocks] folded=16
BCT editor selftest: chosen=16 draft={ "entries": "#minecraft:wool",
  "creative_tabs": ["minecraft:colored_blocks", "minecraft:redstone_blocks"], "priority": 1 }
BCT editor selftest footer: 已写入 wool.json · 优先级 1 · 在「染色方块」折起 16 个物品
BCT editor selftest: colored_blocks has 12 entries, 12 group icon(s), 0 loose wool item(s)
```

- 草稿面板的 JSON 现在带**真实 priority（1）**，与写盘文件一致；
- `uniqueId` 生效：`bct_user:wool` 已被同 entries 的旧组占用 → 复用同 id（编辑路径），无后缀；
- 创造页截图：染色方块页 12 个组图标（含新羊毛组），散羊毛 0 个。

**本轮新增的过时项**：无。`selftest` 哨兵用完即删（本轮已删），`autopen` 保留（文档 §5.4 已记录）。

---

## 11. 第七轮：弃用 `<slot>`，自绘物品格（2026-10-11）

用户反馈三点：不要用 AUI 自带槽位、物品太小、"选择后无法选择"。三点同源，一并解决。

### 11.1 ★ 自定义元素的正确姿势（反编译核实）

AUI 的标签注册有两半，缺一不可：

1. `Element.register(tag, (doc, tagName) -> new XxxElement(doc))` — 公开静态，把工厂放进 `Element.REGISTRY`；
   构造函数必须是 `public XxxElement(Document)` 单参（反射只找这个签名）。
2. **`Element.init(element)`** — `Document.createElement` 永远返回普通 `Element`（反编译确认，`new Element` 一条路），
   注册表只在 **HTML 解析路径** 生效。Java 侧造的自定义标签节点必须手动过一遍 `Element.init`，
   它按 tagName 查表、用工厂重建实例并搬走 id/uuid 等状态。跳过这步就是 `ClassCastException`。

本轮在页面上注册了 `bct-item`（`editor/element/BctItemElement.java`）：`MinecraftElement` +
`BodyRenderNodeProvider`，`createBodyRenderNodes()` 返回 `ElementBackgroundNode`（CSS 背景/选中底色）+
`ItemNode`（11 参全控构造：stack/enabled/x/y/scale 全部自己给），图标按 `body/16` 缩放、在 body 盒内居中
——**格子多大，图标多大**，不再被钉死在原版 16px。`getTooltipStack` 一并覆写。

### 11.2 三个问题的因果

- **"选择后无法选择"**：`<slot>` 自带一整套指针机制（`shouldAcceptPointer`/焦点/双击检测/INTERACTIVE_RESOLUTION），
  站在玩家点击和监听器之间。换成普通自绘元素后点击直通监听器。
- **"物品太小"**：旧网格是 18px 原版槽位尺寸（§9.1 被迫对齐的）。自绘后格子 28px（2px 边框 → 图标 24px），
  `CELL_SIZE` 一个常量与样式表同步。
- **"不要自带槽位"**：网格、页签条图标、图例三处全部换成 `bct-item`，页面里已无 `<slot>`。
  选中态仍走内联样式；边框改成两态恒定 2px，避免选中时图标跳尺寸。

### 11.3 验证（真机）

```
BCT editor: 1755 foldable item(s), 13 tab(s)
BCT editor: applied bct_user:wool priority=1 ... folded=16          ← 闭环仍然通
BCT editor selftest: click toggle 16 -> 17 -> 16 (OK)               ← 点击探针：选中→再点→取消
BCT editor selftest: colored_blocks has 12 entries, 12 group icon(s), 0 loose wool item(s)
```

点击探针（`clickProbe()`）是本轮新增的 selftest 环节：对一格未选中物品走 `Element.click()` 真实事件派发，
计数必须 +1 再 -1——"选择后无法选择"从此有了一个不依赖人手的回归检查。截图确认：28px 格子图标清晰、
图例的选中/未选中两态渲染正确、页签条自绘图标正常。

**待观察**：真实鼠标（非 `Element.click()`）的连点手感；`bct-item` 无 AUI 槽位附带的 tooltip——
已覆写 `getTooltipStack`，若 AUI 只对 Slot/Item 弹提示则悬停无名字，实测再定。

---

## 12. 第八轮：虚拟滚动——点击从"几秒"到 1ms（2026-10-11）

用户实测：物品太小、1755 个物品点一下卡几秒，"根本无法使用"。

**根因**：AUI 对一次样式变更的回答是全量重排 + 重绘**所有**元素。1755 个格子全在树里，每次点击的
内联样式失效就是整个物品栏级别的重排。放大图标只会让它更贵。

**修法：虚拟滚动**（参照 BCTtools 的"只渲染视口"思想，但实现是 AUI 原生的）：

- `.bct-grid` 保持原生滚动，内部一个 `position:relative` 的 spacer 撑出总高度；
- Java 只把**可视行 ±1** 物化成元素（~200 个），绝对定位到行列坐标；
- 滚动检测走 AUI 自己的 `requestAnimationFrame`（其调度器保证在客户端线程），每帧只读一次
  `getScrollTop()` 字段，行号变化才重建（~50ms/次，含 200 次反射构造）；
- 搜索/切页签/清空选择都只作用于可视窗口；选中态按 id 记录，滚回即回填。

实测：`grid sync rows 0-198 took 46-67ms`（此前首帧 1.7s），**`click toggle 16 -> 17 -> 16 in 1ms/0ms`**。
图标同时加大：格子 28→36px（图标 32px），`CELL_SIZE` 一个常量与样式表同步。

---

## 13. 第九轮：分组列表 + 属性面板 + 导出（2026-10-11）

参照 BCTtools 的编辑器字段（组名中英 / 图标 / 页签多选 / priority / 成员预览），在游戏内补齐三个视图。
导航 编辑/分组/导出 现在是真页签（内联样式点亮，`display:none` 收起死视图——AUI 一次样式变更全量重排，
死视图必须不参与布局）。

### 13.1 「分组」列表

`CreativeGroupLoader.groups()` 全表 91 组一行一行列：自绘图标（首个成员）、名字（用户存储名 / 路径）、
来源徽标（`isUser` → 绿"我的" / 灰"包"）、priority、页签数、成员数。点行进面板。

### 13.2 属性面板

- **只读条目**：entries 由点选收敛产生，面板不改条目——"什么折叠"和"调什么属性"分开；
- **可改**：中英组名（写进 lang，留空用路径）、图标物品 id（输入即预览，留空取首成员，
  `ItemStackTemplate.fromNonEmptyStack`）、priority（数字输入）、页签 chip 勾选（builtTabs 全集，点击切换）；
- **操作**：保存并生效（`BctGroupStore.save` + `putUser` + 重载折叠；packed id 拒绝直改——复制才是拥有它的方式）、
  复制为我的（packed → bct_user 新 id）、删除（仅 user）、返回列表；
- `Input` 用 `setAttribute("value", …)` 预填、`getValue()` 读取，实测生效。

### 13.3 导出

`BctGroupStore.exportPack()`：把 config 目录镜像成资源包 zip 直接写进 `resourcepacks/BCT_groups.zip`
（覆盖 BCTtools 时代那个旧格式包）。结构：`groups/<ns>/<path>.json` →
`assets/<ns>/bedrock_creative_tabs/creative_group/<path>.json`；lang → `assets/bct_user/lang/<locale>.json`
（`creative_group.*` 键全局有效）。`pack.mcmeta` 写 `pack_format:64 + min_format:64 + max_format:64`——
反编译 `PackFormat$IntermediaryFormat` 确认 min/max 走 `PackFormat.of(int)`，且 26.1 对"声明支持 >64 却
没有 min/max"的包直接报错（旧 BCT Studio 导出的包就是这个警告）。先写 .tmp 再原子改名。

### 13.4 验证（selftest 驱动，日志读回）

```
BCT editor selftest: 91 group(s) listed
BCT editor: saved bct_user:wool priority=6 ... / panel priority 1 -> 6 (OK) / restored to 1
BCT editor: exported 1 group(s) to .../resourcepacks/BCT_groups.zip
BCT editor selftest: export BCT_groups.zip entries=4 packmeta=present
```

面板布局（侧栏 26% 宽、13 个页签 chip）已加滚动约束（chipbox max-height + 面板 overflow-y），
**视觉效果未经截图确认**（用户在用机器，截图会抢焦点）——待用户自测。

### 13.5 从 BCTtools 取了什么（本轮）

| 取 | 用在哪 |
|---|---|
| 编辑器字段清单与交互（组名中英/图标留空取首成员/页签多选/priority） | 属性面板，照它的产品形态 |
| 语言命名空间约定（键全局有效、组名留空回退路径） | 面板保存路径 |
| pack.mcmeta 跨版本教训 | 26.1 格式修正（旧包警告的根因） |
| 冲突页/校验页信息架构 | **未取**——本轮列表行内联展示 P/页/成员数，够用再说 |

**未做**：候选自动生成（它的核心算法，等属性面板稳定后移植）、条目 JSON 双向编辑、撤销/重做。

---

## 14. 第十轮：候选分组自动生成（2026-10-11）

设计共识里"首版不做"的那块，按 BCTtools 的算法移植成 Java（`editor/BctCandidates.java`，无 AUI 依赖）：

- **标签候选**：`BuiltInRegistries.ITEM.getTags()`（26.1 的方法名，不是 `getTagNames`）逐个对 1755 个可折叠物品
  求交集，4..200 个成员的进候选——这是高置信来源，标签本来就是包会写的形式；
- **后缀候选**：BCTtools 校准过的 66 词后缀表（`*_stairs`/`*_sword`/...），补没有标签覆盖的族；
- **去重三连**：候选间同成员集合并、与现有组的成员集合相同则跳过（现有组签名取 `members(tab,id)` 并集）；
  同签名后到者让位于先到者（标签先跑，后缀只补空隙）；
- **中文命名**：BCTtools 的 `ZH` 词表逐条移植，`zhName()` 按 `_` 分词拼接，有未知词就退回英文 titleCase——
  不出半中半英的名字；
- **接受流**：候选行点击 → `openGroupDraft` 预填面板（条目=推断结果只读、名字、页签=成员实际出现的页、
  priority=`planPriority` 预规划），玩家确认后 `saveGroup` 落盘——候选不经过面板绝不折叠；
  `saveGroup` 现在用 `editingPath`（候选给的 tag path / `auto_<suffix>`）而不是时间戳 slug。

### 14.1 一个真 bug

`TreeSet<Item>` 直接炸 `ClassCastException`：26.1 的 `Item` 不实现 `Comparable`。条目排序一律按注册表 id
字符串排，物品对象永不进排序器。

### 14.2 验证（真机，selftest 探针）

```
BCT editor selftest: 224 candidate(s); first: #minecraft:hoes 锄 15 member(s)
BCT editor: saved bct_user:hoes priority=1 icon=false folded=15
BCT editor selftest: candidate accepted as bct_user:hoes priority=1 (15 members)
```

224 个候选（标签 + 后缀，已对 91 个现有组去重）；第一个候选经真实草稿流接受 → 折叠 15 成员 →
探针清理不留痕。既有探针（闭环/点击/面板/导出）全部保持绿。

### 14.3 剩余

- 条目 JSON 双向编辑（面板里编辑 entries 原文）；
- 撤销/重做；
- 候选的"部分重叠"提示（当前只做完全相同去重，重叠候选靠 priority 规划兜底）。

---

## 15. 第十一轮：空网格真凶——滚动泵停摆（2026-10-11）

用户实测：X 光模组页签（135 物品）计数正常但**网格全空**。日志里无任何异常、无 grid sync 记录。

### 15.1 根因链

1. 编辑器**重开**时（20:54:04），新文档刚建立、AUI 首次布局提交还没发生，`getBoundingClientRect()` 返回 0，
   `syncGrid` 静默早退（当时没有 deferred 日志，无从察觉）；
2. **滚动泵的缺陷**：`seenScrollTop` 在调用 `syncGrid` 之前就被推进——早退后泵每帧看到"0 == 0 无变化"，
   **永不重试**。网格从此死透；
3. 用户切页签时布局仍未提交 → force 同步同样早退 → 泵依旧停摆 → 截图里的空网格。

上一轮（20:50/20:59）自检运行恰好没踩中时序（populate 时布局已提交，rect 718x317 有效），所以真机探针
一路全绿而用户一开就空——**探针全绿 ≠ 时序安全**。

### 15.2 修法

- `syncGrid` 返回"是否真的同步了"；泵在早退时把 `seenScrollTop` 重置为 -1，下一帧强制重试，直到布局就绪；
  就绪后第一帧自动物化，无需用户做任何事。
- force 同步无条件打日志（rect/cols/行数/耗时），空网格从此从日志可见。
- selftest 新增 `tabProbe`：程序化切到第一个模组页签（真实点击路径）数格子——本次
  `tab rexray:rexray_tab built 135 cell(s) for 135 item(s)` ✓。

### 15.3 顺手修：pack.mcmeta 还是差一个字段

round 9 的 `pack_format:64 + min_format:64 + max_format:64` 仍触发警告（日志 20:50:24）：
"Pack declares support for format 64, but game versions supporting formats 17 to 64 require a
supported_formats field"。已补 `supported_formats: [64, 64]`。三字段齐全的 mcmeta 才是 26.1 的完整答案。

---

## 16. 第十二轮：空网格第二层真凶——裸 div 的布局虚空（2026-10-11）

§15 的泵修复后用户仍见空网格（截图两次）。几何读回（force 同步后对前 3 个格子量 rect）给出了决定性数据：

```
spacer 427x3724 at 0,0;  cells: [0] 0,0 427x4  [1] 0,4 427x4  [2] 0,8 427x4
```

格子是 **427×4 的小条**（只有内联的 2px 边框在，内容 0 高），按 4px 间距纵向堆叠——`.bct-cell` 的
36px/绝对定位规则全部没生效，内联 left/top 对 static 元素无效。而 427 = 视口逻辑宽度（1280/3）：
**spacer 的布局父级不是 grid，是视口**。日志确认滚动条在、spacer 高度内联生效——树在、尺寸错。

**根因**：`document.createElement("div")` 造的是**未升级的裸 Element**（§11.1 的坑换了张脸）。
裸 div 的布局行为不正确（宽=视口、样式表规则读死），只有 `Element.init` 升级后的节点才正常参与布局。
spacer/格子/页签图标/图例里，凡 Java 造的节点都必须 init。

**修法**：
1. spacer 改走 `Element.init(document.createElement("div"))`；
2. spacer 与格子的盒模型（display/position/width/height/box-sizing）全部改**内联样式**——
   样式表匹配在这类节点上是否可靠已经不重要，内联是唯一被反复证明会重绘的路径（§6.3）；
3. force 同步的几何读回保留为常驻诊断：spacer rect + 父节点 + 前 3 格 rect 一次打全。

修后读回：`spacer 712x3724 at 28,257 parent=DIV, cells 36x36 at 行列坐标` ✓，截图确认 36px 格 +
32px 图标完整渲染，1755/1755。

**方法论教训（第二次踩）**：§9.4 写过"先做最小对照实验再动主界面"，这次又是改了三轮才想到读回几何。
rect 读回应作为 AUI 布局问题的**第一反应**，不是最后手段。

---

## 17. 第十三轮：分组操作全面转原生创造屏幕（2026-10-11）

用户连续点击 AUI 页签卡死数秒 + 定向要求"通过原版创造标签页途径"、"完全放弃 AUI 操作分组"。

### 17.1 为什么原生是对的

AUI 网格的卡死根源是**物品图标首次绘制**：模组物品的纹理/模型烘焙走 AUI 的绘制管线，一页 135 个新物品
就是一两秒的帧停顿，切两个页签 = 两次。而原版创造屏幕的管线是游戏自己的、**已经被玩家浏览时焐热**，
模组物品烘焙成本为零。选择器没有理由不用它。

### 17.2 原生流程（全部在创造屏幕里）

| 按键 | 动作 |
|---|---|
| **B** | 进入/退出选择模式（进 = 打开原版创造屏幕；退出后装饰器立刻停画） |
| **点击** | 选中/取消悬停物品（事件取消，不会拿走物品）；actionbar 报已选数 |
| **G** | 从选中创建分组（收敛 → 落盘 → 注入 → 当场折叠），结果走 actionbar |
| **C** | 清空选择 |
| **Delete** | 删除悬停的**自己的**分组图标（资源包的组不可删） |
| 组图标点击 | 折叠/展开照旧（`CreativeGroupClient` 不变） |

- 选中高亮 = `BctRenderers.SelectedMarker`：为**全部注册物品**注册的 IItemDecorator，绿色描边+半透明填充；
  模式关闭时一次 HashSet 前置判断直接返回，零成本；
- 选中状态在 `BctEditorState.CHOSEN`，跨页签/跨会话保持；原版搜索（id+名称）和页签原生可用；
- 屏幕内按键走 `ScreenEvent.KeyPressed`（**Keybind 在 Screen 打开时不触发**，这是独立踩的坑）；
- `/bct edit` = 进选择模式；`/bct export` = 导出 zip 到 resourcepacks。

### 17.3 代码迁移

| 新 | 从哪来 |
|---|---|
| `editor/BctEditorState` | CHOSEN + PICKER_MODE，无 AUI 依赖，两个表面共享 |
| `editor/BctGroupCreator` | 从 BctEditorAui 抽出：收敛/uniqueId/planPriority/winner/tabsContaining——纯逻辑，AUI 面板和 G 键共用 |
| `editor/BctEditorKeys` + `BctEditorMode` | 按键注册 + 模式维护 + 点击/键盘处理 |
| `BctRenderers.SelectedMarker` | 全物品选择高亮 |

26.1 API 坑（本轮新增）：`InputConstants.Type.KEYSYM`（无 KEYBOARD）、`KeyMapping` 构造无
KeyConflictContext、分类是 `KeyMapping.Category.register(Identifier)`（标签键
`key.category.<ns>.<path>`）、玩家消息是 `sendSystemMessage`/`sendOverlayMessage`（无
displayClientMessage）、`@EventBusSubscriber` 无 `bus` 参数（单总线）。

**AUI 编辑器代码保留未删**（面板/列表/导出/候选仍可经 `/bct edit` 前的路径触达——autopen 与命令已重指向
picker），但主流程不再经过它；`/bct edit` 现在就是进选择模式。

### 17.4 验证

`BCT picker mode on (screen was <none>)`——autopen 进世界即开创造屏幕 ✓。点击/G/C/Delete 的完整交互
**需要真人验证**（合成输入进不了 GLFW 的老限制）；探针（闭环/候选/导出）经 `BctGroupCreator` 保持覆盖。

**待用户实测**：点击选中、G 创建、Delete 删除、B 退出，以及模组页签的切换流畅度（这正是转原生的动机）。

### 17.5 第六个迭代：快捷键面板 + 批量选择（2026-10-11 晚）

| 能力 | 实现 |
|---|---|
| 左侧快捷键面板 | `ScreenEvent.Render.Post` 在屏幕左上画半透明面板（fill + `GuiGraphicsExtractor.text`），列出全部按键；**H** 开关 |
| 回车批量选中 | 选中屏幕**当前显示列表**（`menu.items`）：搜索页=查询过滤结果（id+名称，原版自己匹配），普通页=整页内容；跳过空堆叠与组图标 |
| 右键拖选 | 右键按下定方向（首个物品已选中→本次拖动是去除，否则是添加），拖动期间光标经过的槽位逐个加入/移出（`MouseDragged.Pre`，取 `getHoveredSlot()`），松开结束；拖选事件取消以防原版拖拽逻辑 |

**关键护栏**：屏幕内有文本框（搜索框）获得焦点时，B/G/C/Delete/H/回车**全部不生效**——否则在搜索框里打字就会折叠分组。拖选方向由首个物品决定（Web 框选惯例），`lastSwept` 防同一物品反复翻转。

### 17.7 第八个迭代：AUI 全面退役 + 两个反馈修复（2026-10-11 深夜）

用户指示"完全放弃 AUI"后要求清理："之前做 aui 相关的内容删了没，没必要的东西就删了吧"。

**已删除**：`BctEditorAui`（672 行）、`BctEditor` 门面、`editor/element/BctItemElement`、
`assets/apricityui/` 页面与样式、autopen/reopen 里的 AUI 热重载路径、**build.gradle 的 ApricityUI 依赖与
Modrinth 仓库**。`grep apricity|sighs src/main/java` 零命中，jar 内零残留——**本模组从此不需要安装
ApricityUI**（实例 mods 里的 apricityui jar 留给用户自便）。`BctCandidates`（纯逻辑、无 AUI 依赖）保留
待原生入口接入。autopen 哨兵保留但只进选择模式。

**修复一：中键标签面板打不开。** 根因是**监听器顺序 + 取消语义**：`BctEditorMode.onClick` 把中键
（button==2）落进了"其他按钮"分支——选中物品并取消事件；被取消的事件会终止后续监听器，
`BctPickerOverlay.onMouseDown` 根本没有执行机会。修法：mode 的 onClick 显式只处理左键和右键，
其余按钮直接放行给 overlay。

**修复二（诊断中）：输入框只能粘贴、打字无效。** 反编译 AUI 的 `CommonKeyboardHandlerMixin` 排除了
"吃字符事件"假说（`Operation.onCharTyped` 只在有 AUI 焦点文本时返回 true，选择模式无 AUI 文档）。
已布下 `ScreenEvent.CharacterTyped` 探针：进选择模式打字后看日志里的 `BCT char 'x' focused=…`——
字符到达屏幕但焦点不对 → 焦点链问题；字符根本不出现 → 更底层的输入问题。待用户实测一轮定位。

---

## 18. 第十四轮：分组自动命名（2026-10-12）

用户反馈："edit 新建分组后命名还是随机的，吃人工"，并给出判据：**分组 id 和中英本地化应能从组内物品
提取出相同的语义**（五把不同前缀的剑 → sword/剑/Sword；多个 xx 抽屉 → 抽屉）。

### 18.1 设计：三来源同源

新增 `editor/BctNames.java`（无 UI 依赖，纯函数）：id、中文、英文**从同一批成员的公共部分**推出，
天然一致——这是"同源"的实现方式，不是三次独立猜测。

| 例 | id 依据 | 中文依据 | 英文 |
|---|---|---|---|
| `wooden_sword`…`diamond_sword` | id 公共尾词 `sword` | 物品显示名公共结尾 `剑` | `Sword` |
| `*_drawer` | `drawer` | 字典 `抽屉`（显示名不成族时的兜底） | `Drawer` |
| `#minecraft:wool` | 标签路径 `wool` | 成员显示名公共结尾 `羊毛` | `Wool` |

### 18.2 关键决策：中文取"游戏自己的名字"

**第一来源是物品的本地化显示名**（`getHoverName`）的公共结尾——木剑/石剑/铁剑 → `剑`。这招对**任何
mod 都成立**（用游戏已翻译好的名字），不依赖手写词典；词典降级为"显示名不成族时的兜底"。
这直接补上了原方案的漏洞：原词典没有 `drawer`，会退化成英文，达不到用户要求，故词典也补了
`drawer/crate/cabinet/tank/generator/drill/wrench` 等模组常用词。

### 18.3 命名顺序

1. 精确标签 → id=标签路径；中文=显示名公共结尾 → 词典 → 英文
2. 无精确标签 → id/英文=**id 公共尾词**；中文=显示名公共结尾 → 词典 → 英文
3. 尾词不成族 → 最高频共同词 → 词典
4. 全失败 → **确定性 slug**（按成员 id 排序派生，取代随机时间戳）；名字回退英文，绝不显示 `group_3f9a`

id 仍走 `uniqueId` 唯一化；**显示名与 id 分离**（改名不影响 id）。

### 18.4 反馈与附带修复

- actionbar：`已创建 剑 / Sword · 折起 N 个 · 按 R 可微调名称`；**不自动弹重命名框**（不打断连续建组）。
- `BctGroupStore` 名称缓存改由 `refreshName` 按 `zh_cn → en_us → 移除` 重算：修好了"只改英文名图标
  不更新""删中文后英文不接管""双语清空不回退翻译键"三个旧缺陷。`save` 与 `rename` 都走它。

### 18.5 实测（selftest 哨兵，真机日志读回）

```
[swords(5)]      id=sword   zh=剑     en=Sword     (OK/OK)
[drawer ids]     tail=drawer (OK)
[drawer dict]    zh=抽屉 (OK)
[tag wool]       id=wool    zh=羊毛   en=Wool      (OK/OK)
[planks family]  id=planks  zh=木板   en=Planks    (OK/OK)
```

**过程中修掉一个真缺陷**：颜色羊毛的公共结尾算成了「**色**羊毛」（白/橙/红/蓝/黑 里四个以"色"结尾，
逐字回溯把它吃进去了）。修法：`trimColorPrefix` 在中文结尾前剥离被词典识别为独立颜色词的相邻前缀，
于是回到 `羊毛`。

### 18.6 剩余

- 中文词典仍可继续扩充（当前是 BCTtools 词表 + 模组常用词）；
- 尾词不成族时目前退到确定性 slug，可选增强为"显示名共同词"。

---

## 19. 第十五轮：命名按"最高频重复片段" + 面板重排 + 输入真凶（2026-10-12）

用户四项反馈：命名改按"数量最多的重复字符串（双语）和重复 id 片段最多"；重命名和标签选择分别移到
左下角与创造背包中下方（"你看它们都无法正常使用了"）；标签列表加滑轮防溢出。

### 19.1 ★ 输入失效真凶：创造屏幕重写 charTyped 且不调 super

反编译 `CreativeModeInventoryScreen.charTyped`：非 SEARCH 页签时**直接 return false，不调用 super**。
而 NeoForge 的 `ScreenEvent.CharacterTyped` 是在 `Screen.charTyped` 里投递的——被重写后**这个事件永不触发**，
字符也永远到不了我们的 `EditBox`。粘贴走 `keyPressed`（创造屏幕会转发给子控件）所以能用。
**这就是"只能粘贴、不能打字"的根因。**

修法：新增 `mixin/CreativeModeInventoryScreenInputMixin`，在 `charTyped`/`preeditUpdated` 的 **HEAD** 注入——
在屏幕自己的重写丢弃事件之前，把字符转发给获得焦点的覆盖层输入框（`BctPickerOverlay.forwardChar/forwardPreedit`）。
mixin 只做转发，判断逻辑（模式开关、哪个框有焦点）留在覆盖层。

### 19.2 命名改按"最高频重复片段"（覆盖人数优先）

原实现是"严格公共尾词/结尾"，一个异类就会让整族退化。改为**按覆盖成员数排序**取最高频片段：

- **id**：把每个成员 id 按 `_` 分词做词频统计，取覆盖 ≥ 半数（且 ≥2）的片段；并列时**靠 id 结尾者优先**、
  再长者优先。`[wooden_sword, stone_sword, iron_sword]` → `sword`；抽屉族 → `drawer`。
- **中文**：对显示名取长度 1..4 的尾部片段做词频统计，先取"全员共有"，否则取覆盖 ≥60% 者；
  并列时结尾优先、再长者优先。木剑/石剑/铁剑 → `剑`；存储抽屉/抽屉 → `抽屉`。

这更贴合用户说的"数量最多的重复字符串"，且容忍异类成员。

### 19.3 面板重排 + 标签滚动

| 面板 | 原位置 | 新位置 |
|---|---|---|
| 重命名 | 右侧弹窗（压住 GUI，无法点） | **左下角**（`x=6, y=height-84-6`），完全避开居中的创造 GUI |
| 标签列表 | 右侧弹窗 | **创造背包中下方**（水平居中，`y` 在 GUI 网格之下），随屏幕高度自适应行数 |

标签列表加了**滚轮翻页 + 右侧滚动条**（`MouseScrolled.Pre`，光标在面板内时 `tagScroll ± 1`，
滚动条滑块按可见比例绘制），超长标签 id 用 `font.plainSubstrByWidth` 裁剪，不再溢出屏幕。
标签面板点击区域排除滚动条槽；点面板外关闭面板。

### 19.4 实测（真机）

```
[swords(5)]  id=sword  zh=剑   en=Sword   (OK)
[drawer ids] tail=drawer (OK)   [drawer dict] zh=抽屉 (OK)
[tag wool]   id=wool   zh=羊毛  en=Wool    (OK)
[planks]     id=planks zh=木板  en=Planks  (OK)
BCT picker mode on (screen was <none>)   ← mixin 加载无报错，模式正常
```

---

## 20. 第十六轮：选择集跨创建污染（2026-10-12）

用户报告：先给"升级"（`storagedrawers:*_upgrade`）建了组，接着选钥匙再建，**钥匙组仍被命名成"升级"**。

**根因**：`BctGroupCreator.createFromSelection()` 创建后**不清空** `BctEditorState.CHOSEN`。上一次的升级物品
仍留在选择集里，于是钥匙那次是"升级 + 钥匙"的**并集**；词频统计里升级物品更多、覆盖率更高，`升级` 赢过
`钥匙`。截图佐证：钥匙那一排全绿，而升级物品（带 + 角标）也在选中态——两族被合进了同一次选择。

**修法**（按用户选择"创建后自动清空"）：`save` + `putUser` + 重载之后立即 `CHOSEN.clear()`。创建是一个
完整动作，选中的东西已经变成组，不该继续参与下一次命名与成员推断。同一个 bug 也会污染**成员**（所以不只是
名字错，折叠进去的物品也是错的），清空一并修掉。

---

## 21. 第十七轮：标签面板锚定到创造 GUI（2026-10-12）

用户圈定了标签面板的目标位置与大小：**创造 GUI 正下方那条空带**，宽度随 GUI。不再用"屏幕底部居中"的猜测。

### 21.1 拿到 GUI 真实几何

`leftPos`/`topPos`/`imageWidth`/`imageHeight` 是 `AbstractContainerScreen` 的 protected 字段，
`CreativeModeInventoryScreen` 自己不声明它们。**混入的 accessor 只查目标类自身字段**，所以第一次把它加在
`CreativeModeInventoryScreenAccessor` 上直接 `InvalidAccessorException`（No candidates found matching leftPos:I）——
mod 加载失败。修法：新建定位 `AbstractContainerScreen` 的 `AbstractContainerScreenAccessor`。

### 21.2 面板几何

`tagX = leftPos`、`tagWidth = imageWidth`、`tagY = topPos + imageHeight + 2`，
`tagRows = 下方空带能容纳的行数`（`(screen.height - tagY - 6) / ROW_H`，夹在 3..14）。于是面板：
左边缘对齐 GUI、宽度等于 GUI、垂直贴在 GUI 正下方，行数按剩余空间定，**永不超出 GUI 底边**。
点击/滚轮命中区同步改用这三个函数计算的矩形 + GUI 宽度。

---

## 22. 第十八轮：中文命名回退修复 + 中止输入（2026-10-12）

用户报告两点：中文分组名退化成单字（"好几个 xx 牛排，结果只给了个『排』"，英文正常）；搜索选中后
快捷键与输入冲突。

### 22.1 ★ 中文名退化的根因：早退的"全员共有"档

`commonZhTail` 原来先跑"**全员共有**"这一档，一旦命中就**立即返回，不再看更长片段**。真实数据里的反例：

| 组 | 成员显示名 | 字面全员共有 | 期望 |
|---|---|---|---|
| 牛排 | 铜牛排/铁牛排/金牛排/钻石牛排/下界合金牛排 | `排` | `牛排` |
| 木板 | 橡木木板/云杉木板/白桦木板/**竹板** | `板` | `木板` |

`竹板`（原版 bamboo_planks 的中文）这类异类让"板"成为全员共有 → 早退返回单字，"木板"（覆盖 94%）没机会。

**修法**：覆盖率只当**门槛**（不早退），达标者里取**最长**片段（等长再比覆盖率）。片段上限 4→8 字
（覆盖"下界合金块/铜傀儡雕像"这类模组词），并加 `stripConnectives` 去掉尾巴上的连接词（"的避雷针"→"避雷针"）。

### 22.2 中止输入：选中后释放搜索框焦点

`BctEditorMode.onKey`（G/C/Delete/R/B/回车）在**有 EditBox 获得焦点时全部让位**（防打字误触快捷键）。
但搜索框点「选中匹配项」或回车后**焦点仍留在框里**，于是接下来按 G/C 被当成输入打进搜索框。修法：
`selectMatching` 完成后 `blur(searchBox)`；且点击搜索框与匹配按钮以外的任何位置也失焦，键盘交还模式。

### 22.3 实测（真机）

```
[swords(5)] id=sword zh=剑 en=Sword (OK)      [planks family] zh=木板 (OK)
[steak tail] 牛排 (OK)                        [planks tail w/ outlier] 木板 (OK)   ← 竹板异类不再塌成"板"
[lightning tail] 避雷针 (OK)                   [tag wool] zh=羊毛 (OK)
```

### 17.6 第七个迭代：BCTtools 式搜索 + 标签面板 + 多语言重命名（2026-10-11 深夜）

新组件 `editor/BctPickerOverlay.java`——原版创造屏幕上的自绘覆盖层（`ScreenEvent.Init.Post` 挂
`EditBox`（输入走原版 children 路由，**IME 可用**），`Render.Post` 手动渲染（screen 的 renderable 名单
对外封闭，26.1 的公开渲染口是 `extractRenderState`））：

| 能力 | 交互 | 实现 |
|---|---|---|
| 搜索框（左上） | 输入名称/id → 点「选中匹配项」或回车 | 当前页签的 universe 里按 id + 本地化名 contains 匹配（BCTtools 的一键相似选中） |
| 标签面板 | **中键**物品 → 右侧面板列出其全部 item 标签 → 点标签 | 选中 universe 中同标签全部物品——"从一个物品带走整个标签" |
| 多语言重命名 | **R** 悬停自己的组图标 → 面板两个输入框（中/英）→ 保存 | `BctGroupStore.rename` 直写 config 语言文件，重载后图标名字生效 |

工程细节：隐藏面板用 `AbstractWidget.visible` 公开字段（`mouseClicked` 有 isActive 守卫，隐藏即不接输入）；
面板点击命中在 Pre 阶段自测，面板外点击关闭面板；焦点走 `screen.setFocused`（打字路由由此进入）；
"屏幕宽度 ≥ 340"才挂载（左侧列不与创造 GUI 重叠），右侧面板按弹窗处理、可覆盖 GUI。
