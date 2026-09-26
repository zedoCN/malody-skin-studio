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
  "startOffset": 0
}
```

执行 Unity 菜单 `Tools/Malody/Codex/Play Skin Fixture`。入口等待 PlayMode 和歌曲扫描，以谱面绝对路径选中 Chart，指定 Skin 后进入 ScenePlay。`Library/CodexSkinFixture.status.json` 的 `phase` 为 `scene-play` 时，可以等待加载结束再抓 Game View；`failed` 时读取 `detail`。`startOffset` 单位为毫秒。Game View 捕获仍须检查返回的 `sourceWidth`、`sourceHeight`，并记录实际游戏时间；入口目前没有冻结游戏时间或强制 Lua 更新到特定帧。

2026-09-26 用 Emiria `d3b60d0fd`、EX Rhythm Master VI 的隔离副本和 R.I.P. 的 `slide_easy.mc` 实测：Unity 正常进入 ScenePlay，捕获到 **1752×986** 的最终 Game View；本项目同尺寸、`windows` 平台、图层 `1` 的静态 PNG 可用于几何对照。首次用同曲 `4k_easy.mc` 被 `Skin.SupportPlayMode` 拒绝，说明入口确实执行了游戏的模式检查。图层 1 中四个固定图标的边框位置目视相差约 1–2 px，尚未发现可确定归因于静态布局计算的偏差。赛道边沿在两图中的位置不同，但运行截图还叠有游戏赛道、Lua、图层 4 和游戏 UI，不能直接把整图像素误差或这些边沿差异当作静态渲染缺陷。当前证据只证明对照链可运行，不证明所有模块与游戏像素一致。
