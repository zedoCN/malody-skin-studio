# Malody V 静态布局校准

截至 2026-09-26，“布局概览”投影背景层（1）和顶层（4）中可按原始参数绘制的自定义图片，并可用时间轴预览受支持的 ASM 内置动画。参考视口为 1920×1080；平台默认 Windows，可切换 iOS／Android。静态图片可拖动来修改原组件的 `dx`／`dy` 偏移量，点击“保存皮肤”才写回包。拖拽保留原有 X／Y 锚点、单位、尺寸及其他字段。Lua 仍可能在运行时改写这些模块的位置或尺寸；它用于检查原始参数和内置动画，不是游戏运行截图。

## 依据

- Emiria `Assets/Malody/Scripts/Composer/SkinInstanceFactory.cs` 的 `ApplyBasicParam`、`ApplyImageSize` 决定百分比／Unit 坐标、位移、pivot 和图片尺寸；`SkinModuleBase.cs` 从**直接父容器**的 `RectTransform` 取得尺寸，遇到 `CanvasScaler` 时按参考高度换算 Unit。
- Emiria `Assets/Malody/Scripts/Scene/Play/Skin/SkinRuntime.cs` 的 `Prepare` 按禁用状态、场景条件、模块有效性筛选并按 `Param.Order` 排序；`IsSceneMatch` 的 Width、Height、Ratio、Platform 使用 `ParentSize` 和平台值。`ScenePlay.cs` 的 `PrepareSkin` 把 `frontCanvas` 矩形赋给 `ParentSize`；`SkinUtil.Runtime.cs` 定义条件比较方式。
- 在 Unity 2023.2.20f1 的 `PlayModeKey.unity` 中，通过 Editor Bridge 检查层级和 `RectTransform`：Background、Foreground 是 RootPlay 的直接子节点，均有 CanvasScaler；Below、Above 位于 Track 3D → Track Scale → Track Rect → Track Anchor → Track 下，没有 CanvasScaler。在当时的 Editor Game View，背景／顶层宽约 1916.9、高 1080；游玩区上下层为 1680×20000。该尺寸是当次视口的观察值，不能当作所有设备的固定值。检查结束后已关闭附加打开的 PlayModeKey 场景。

因此层 2／3 不能套用全屏画布的投影。Key 模式下，模块的直接父容器是 Below／Above；两者位于 `Content → Track → Track Anchor → Track Rect → Track Scale → Track 3D` 变换链内。`PlayTrack3D.prefab` 的 Track 高约 20000，宽度还会被 `UIRelativeSize` 按父尺寸和曲线改写；`CanvasScalerFOV` 随相机像素尺寸调整 FOV 与缩放。因此场景 YAML 中的尺寸不是稳定的游戏屏幕坐标。下一步若做局部赛道预览，必须把场景条件的视口尺寸与模块父容器的局部尺寸分开，再用固定配置的 Unity PlayMode 画面校验投影。

场景条件仅对 Width、Height、Ratio、Platform 在选定参考视口下求值；其余需要游玩模式、赛道设置、谱面或玩家状态的条件标记为“无法静态判定”，不猜测显示结果。参考视口并不表示某台设备的实际 `frontCanvas` 尺寸。

## ASM 内置动画

Emiria `AnimateEngine.Cls.cs` 的 `AnimateRecord.From(ModuleAnimation)`、`Current` 和 `ApplyValue` 给出时间单位、缓动、重复、延迟以及属性写入方式；`AnimateEngine.cs` 把双轴动画拆成单轴并按开始时间更新。编辑器据此支持全屏图片的 Move、Size、Scale、Alpha、RotateZ，滑动时间轴时直接重算当前帧。带触发器、RotateX／RotateY、Lua 控制的模块仍需运行态核对；带动画的图片暂不支持画布拖拽，以免改动动画与基础偏移的关系。`--snapshot-v` 仍导出静态基础布局，不使用时间轴。

`examples/asm/timeline-demo` 是不依赖 Lua 的小样例，可直接打开目录验证播放、暂停、往返重复和渐显。现有真实 `EX Rhythm Master VI` 样例的 85 个模块没有 ASM 内置动画，它的动态变化主要由 Lua 驱动，因此该皮肤的时间轴处于停用状态。

## 复查入口

```sh
./mvnw javafx:run '-Djavafx.args=--inspect-v --layout --platform android /path/to/skin.msp'
./mvnw -q '-Dmalody.v.samples=/path/to/skin1.msp:/path/to/skin2.msp' -Dtest=VSceneLayoutTest test
```

命令会输出能投影的矩形，并分别统计游玩区／未知层、场景不匹配和无法静态判定的模块。两份真实样本路径由运行者提供，仓库不收录皮肤素材。后续要支持层 2／3，应先建模对应模式的赛道容器大小、锚点和 Track 3D／Scale 变换，并从 Unity 或实际运行帧验证，再开放这两层的拖拽编辑。
通过 `javafx:run` 传含空格的路径时，`--inspect-v` 也可使用 `file:` URI（空格写作 `%20`），与 `--snapshot-v` 一致。
