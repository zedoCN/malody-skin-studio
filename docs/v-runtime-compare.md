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
