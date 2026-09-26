# Malody V 运行画面对照

本项目的 V「布局概览」目前只显示背景层（1）和顶层（4）的静态自定义图片。它不能代替 Malody V 的游玩画面：Lua、动画、谱面条件和赛道层仍由运行时决定。原始实现参考 Emiria `d3b60d0fd0ba`；4.3.7 `.mui` 另以实际 APK 画面校准。

## 已确认的运行链

- Emiria 的 `ScenePlay.PrepareSkin` 可从 `IntentPlay.assignSkin` 指定皮肤；`SkinRuntime.Prepare` 再按模式、谱面和场景条件选择模块。`ScenePlay.Start` 读取 `PlayInfo.InUse.Chart`，`ChartPlayer` 启动播放。
- Editor Bridge 的 `editor.visual.game_view_capture` 能抓最终 Game View，但其 `width`、`height` 是输出尺寸上限，**不会设定原始 Game View 视口**。比较前必须固定 Game View 分辨率，并记录截图返回的源尺寸。
- `ChartPlayer.TimeJump` 后立即 `Pause` 不能保证画面已刷新；图形更新与 Lua `Update` 发生在后续运行帧。对照截图须记录实际播放时刻，并至少等待一次图形和 Lua 更新。要做像素级同一时刻的比较，还需要可控的时间源。
- Composer 的 `SceneEditorSkin.ToTest` 会保存当前皮肤，并任意选取同模式谱面；它不适合作为无损、可重复的基准入口。

## 可用本机样本与保护边界

本机 `/Users/zedo/Downloads/6091_EX_Rhythm_Master_VI (1).msp` 有 Slide 组件和 Lua；Emiria 的 `Assets/StreamingAssets/Charts/_song_711.mcz` 包含 `slide_easy.mc`。这些样本不进入仓库。可在隔离副本上准备同一皮肤、同一谱面和同一 Game View 配置；皮肤与谱面的扫描器会处理并删除扫描目录中的原始包，因此不要把唯一的 `.msp` 或 `.mcz` 放进扫描目录。

首轮对照先用静态背景／顶层：由本项目导出同一视口、平台和图层的透明 PNG，再在 Unity PlayMode 捕获最终 Game View，对齐视口后检查位置、尺寸、旋转和透明度。动态内容另记实际播放时刻，避免把脚本、谱面或帧差异误判为静态布局误差。群内民间资料可提供测试案例，结论仍以代码和运行画面核实。

## 下一项运行时入口

如果静态基准发现需要验证动态或赛道层，可在 Emiria 增加仅供 Editor 使用的 fixture 入口：指定**隔离副本**中的皮肤和谱面、配置 `PlayInfo` 与 `IntentPlay.assignSkin`、进入 PlayMode、等待目标时刻和渲染更新，再记录实际时间并截图。该入口尚未实现，也尚未取得 Unity 与本项目的像素差异数据。
