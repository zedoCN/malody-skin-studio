# Malody V 运行画面对照

本项目的 V「布局概览」目前只显示背景层（1）和顶层（4）的静态自定义图片。它不能代替 Malody V 的游玩画面：Lua、动画、谱面条件和赛道层仍由运行时决定。原始实现参考 Emiria `d3b60d0fd0ba`；4.3.7 `.mui` 另以实际 APK 画面校准。

## 已确认的运行链

- Emiria 的 `ScenePlay.PrepareSkin` 可从 `IntentPlay.assignSkin` 指定皮肤；`SkinRuntime.Prepare` 再按模式、谱面和场景条件选择模块。`ScenePlay.Start` 读取 `PlayInfo.InUse.Chart`，`ChartPlayer` 启动播放。
- Editor Bridge 的 `editor.visual.game_view_capture` 能抓最终 Game View，但其 `width`、`height` 是输出尺寸上限，**不会设定原始 Game View 视口**。比较前必须固定 Game View 分辨率，并记录截图返回的源尺寸。
- `ChartPlayer.TimeJump` 后立即 `Pause` 不能保证画面已刷新；图形更新与 Lua `Update` 发生在后续运行帧。对照截图须记录实际播放时刻，并至少等待一次图形和 Lua 更新。要做像素级同一时刻的比较，还需要可控的时间源。
- Composer 的 `SceneEditorSkin.ToTest` 会保存当前皮肤，并任意选取同模式谱面；它不适合作为无损、可重复的基准入口。

## 可用本机样本与保护边界

本机 `/Users/zedo/Downloads/6091_EX_Rhythm_Master_VI (1).msp` 有 Slide 组件和 Lua；Emiria 的 `Assets/StreamingAssets/Charts/_song_711.mcz` 包含 `slide_easy.mc`。这些样本不进入仓库。皮肤与谱面的扫描器会处理并删除扫描目录中的原始包，因此不要把唯一的 `.msp` 或 `.mcz` 放进扫描目录。

首轮对照先用静态背景／顶层：由本项目导出同一视口、平台和图层的透明 PNG，再在 Unity PlayMode 捕获最终 Game View，对齐视口后检查位置、尺寸、旋转和透明度。动态内容另记实际播放时刻，避免把脚本、谱面或帧差异误判为静态布局误差。群内民间资料可提供测试案例，结论仍以代码和运行画面核实。

## Editor 对照入口

将仓库中的 [`tools/emiria/CodexSkinFixture.cs`](../tools/emiria/CodexSkinFixture.cs) 复制到 Emiria 的 `Assets/Malody/Scripts/EditorScript/Editor/`，让 Unity 完成 AssetDatabase 刷新和脚本编译。入口需要引用 Malody 游戏程序集，所以不能放在独立的 Editor Bridge 程序集中。不要在含有未保存场景或 Prefab 修改的 Editor 中开始运行。

先把 `.msp` 解包为皮肤目录、把 `.mcz` 解包为谱面目录，两者都放在 Malody V 用户数据目录下的**隔离副本**中。写入 Emiria 的 `Library/CodexSkinFixture.json`（`Library` 不受 Git 跟踪）：

```json
{
  "skinDir": "/absolute/path/to/extracted-skin",
  "chartPath": "/absolute/path/to/extracted-chart/slide_easy.mc",
  "startOffset": 0,
  "freezeAtAudioMs": 10000
}
```

执行 Unity 菜单 `Tools/Malody/Codex/Play Skin Fixture`。入口等待 PlayMode 和歌曲扫描，以谱面绝对路径选中 Chart，指定 Skin 后进入 ScenePlay。`startOffset` 和 `freezeAtAudioMs` 单位均为毫秒；省略 `freezeAtAudioMs` 或设为 `0` 时正常播放。设为正数时，入口等待 ChartPlayer 的图形更新达到该音频时刻，再用 `ScenePlay.DoStreamPause()` 暂停音频、谱面和 PlayBehavior（包括 Lua）。`Library/CodexSkinFixture.status.json` 的 `phase` 为 `chart-time-frozen` 后可截图，其中还记录实际 `audioTimeMs`、`chartTimeMs`、`displayTimeMs` 和 `unityFrame`；`failed` 时读取 `detail`。暂停点可能越过目标几毫秒，须以状态文件中的实际时间为准。

同一暂停状态下可用 `Tools/Malody/Codex/Hide Skin Layer 1`、`Show Skin Layer 1`、`Hide Skin Layer 4`、`Show Skin Layer 4` 临时切换相应皮肤 Canvas，并分别抓 Game View。切换只作用于运行中的场景；比较结束后恢复显示并退出 PlayMode。以下命令报告隐藏前后的变化范围，并可检查恢复后的画面是否逐像素一致：

```sh
uv run scripts/v_capture_diff.py full.png hidden-layer1.png --restored restored.png --mask target/debug/layer1-mask.png
```

这里的变化遮罩表示该 Canvas 对最终画面的**可见贡献**，不会包含被其他层遮挡的像素，也不能直接还原源模块的 RGBA 图。Game View 捕获仍须检查返回的 `sourceWidth`、`sourceHeight`。任意 Lua 脚本可能依赖帧数或系统时间；此入口固定的是已经运行到的画面和时钟，不提供任意时刻的无副作用重放。

2026-09-26 用 Emiria `d3b60d0fd`、EX Rhythm Master VI 的隔离副本和 R.I.P. 的 `slide_easy.mc` 实测：Unity 正常进入 ScenePlay，捕获到 **1752×986** 的最终 Game View。目标音频时刻为 10000 ms，实际暂停于 **10001.07 ms**；隐藏图层 1 与图层 4 后，分别有约 65% 与 14% 的最终画面像素变化。两层都恢复显示后，整张 PNG 的 SHA-256 与最初截图相同，说明暂停后的图层对照可重复。本项目同尺寸、`windows` 平台、图层 `1` 的静态 PNG 可用于几何对照。首次用同曲 `4k_easy.mc` 被 `Skin.SupportPlayMode` 拒绝，说明入口确实执行了游戏的模式检查。图层 1 中四个固定图标的边框位置目视相差约 1–2 px；其他区域仍须按模块识别，不能直接把整图差异当作静态布局缺陷。

这次最明显的赛道横杆差异已经定位到 Lua，而非画布坐标：背景层模块 `#16`（`trackbg`，`1712474142416.png`）原始高度为 1200 Unit；样本的 `rmslideEXF.lua` 根据 `Game:FieldMeta("Angle")` 计算 `trackscale = 1 - (angle - 30) / 200`，并在第 360–362 行改写模块宽高。此次游戏配置的角度为 45°，所以高度乘以 **0.925**；横杆从原始参数快照的约 `y=790` 移到 Unity 画面的约 `y=733`。隐藏图层 1 时该横杆随之消失。说明当前预览中的“静态”仅指原始模块参数可投影，**不保证模块在 Lua 运行后仍保持该位置和尺寸**；因此暂不改动 `VSceneLayout` 公式。

## 冻结帧模块状态

达到 `chart-time-frozen` 后执行 Unity 菜单 `Tools/Malody/Codex/Export Module State`，可将背景、游玩区上下层及顶层工厂中的已实例化模块写入 Emiria 的 `Library/CodexSkinFixture.modules.json`。每个条目包含逻辑名称、所属工厂、原始 `info.asm` 参数，以及游戏运行后的模块尺寸、透明度和 RectTransform 状态。导出还记录 `info.asm` 的 SHA-256、皮肤 Lua hash、谱面文件名和实际音频时刻；同名模块列在 `duplicateNames`。Unity 枚举顺序并不稳定，因此匹配必须靠原始参数，不使用 JSON 数组序号。输出留在 Emiria 的 `Library`，不提交皮肤内容。游玩区层的运行值可以查看，但编辑器仍不能把该层投影到全屏布局概览。

```sh
python3 scripts/v_module_state.py /Users/zedo/Projects/Emiria/Library/CodexSkinFixture.modules.json --name trackbg
```

这次冻结帧共发现 4 个工厂，其中背景和顶层实例化了 97 个模块，游玩区上下层没有实例化模块；逐模块读取没有报错。`trackbg` 原始 `width=2325.40`、`height=1200` Unit，运行后分别为 `2442.98`、`1110` Unit，高度比正好是 **0.925**。这个诊断脚本只比较能直接对应的 Unit 图片尺寸和透明度；出现差异仍需结合游戏代码与 Lua 判断原因，不应将全部差异归于 Lua。Unity Editor 菜单回调里的 `Screen.height` 曾报告 1616，而同次 Game View 截图源高度是 986；导出字段明确标成 `unityReportedScreenHeight`，对照实际视口要以截图的 `sourceWidth`、`sourceHeight` 为准。

V 编辑器的「运行态对照」页可显式加载这份 JSON，按 `info.asm` 原始字节 SHA-256 和 Emiria 的整套 Lua 文件 hash 校验当前保存版皮肤。`.msp` 与解包目录若这两类内容相同，也可通过校验；**素材文件和目录路径不在校验范围内**，运行值仍属于导出时的皮肤副本。指纹不匹配时不展示组件运行值。选中组件后按原始模块参数寻找唯一对应项；同名或相同参数导致歧义时不会猜测。该页始终只读，尚未保存的编辑草稿不参与匹配；修改皮肤并保存后，需要重新运行 Unity 导出新快照。若替换图片等素材，同样需要重新导出快照才能确认运行画面。

「变化汇总」会按保存版组件顺序列出能唯一匹配、且 **Unit 图片宽高或透明度**数值有变化的模块；点击左侧组件可看该模块的变化和完整原始／运行值。真实 EX Rhythm Master VI 冻结样本中有 28 个这样的模块，`trackbg` 宽高比约为 `1.050562`／`0.925`。PX、百分比尺寸及坐标不能与当前导出的运行值直接按数值比较；变化也不能单凭此报告归因于 Lua，无变化不代表像素画面一致。重复且原始参数完全相同的模块被记为匹配歧义。

## 同一冻结状态的截图包

若还要在「运行态对照」页看完整 Game View，先保持 `chart-time-frozen`，按顺序：复制一份 `status.json`，执行模块导出并复制为 `modules-before.json`；用 Bridge 的 `editor.visual.game_view_capture` 连续抓两张图，记录各自返回的 `artifact.manifestAbsolutePath`；再次导出并复制为 `modules-after.json`，最后再复制一份 `status.json`。两次截图应使用相同输出尺寸上限，且查看返回的 `gameView.sourceWidth/sourceHeight`；本样本以 `1752×986` 为上限得到未缩放的全帧 PNG。命令的 `width`、`height` 不会设置游戏视口。

```sh
python3 scripts/v_capture_bundle.py \
  --status-before /path/to/status-before.json \
  --modules-before /path/to/modules-before.json \
  --capture-first /path/to/first-capture-manifest.json \
  --capture-second /path/to/second-capture-manifest.json \
  --modules-after /path/to/modules-after.json \
  --status-after /path/to/status-after.json \
  --output target/debug/my-v-capture
```

脚本只在冻结状态文件前后字节相同、模块状态与三项游戏时间相同、两张 PNG 字节相同，且 Bridge manifest 的尺寸和 SHA-256 都正确时生成截图包。输出目录包含 `bundle.json`、`status.json`、`modules.json`、`capture.json`、`game-view.png`；编辑器用「加载截图包…」选择 `bundle.json`，会再次校验文件摘要、PNG 解码尺寸和皮肤 ASM／Lua 指纹。请在**同一次 PlayMode 冻结期间**连续采集这些文件：现有 Bridge 没有贯穿状态、模块与截图的运行会话 ID，包本身只能证明上述字节、时间和截图一致性，不能独立证明文件来自同一会话。Bridge 也未记录截图瞬间的游戏帧号，`frozenUnityFrame` 仅表示开始冻结时的帧。游戏资源若改变，需要重新采集。样本包及截图留在 `target/debug` 或 Emiria `Library`，不提交到仓库。
