# Malody Skin Studio

马老弟音游的非官方皮肤编辑与预览工具。一个主窗口支持打开 4.x `.mui`／`.msz` 和 Malody V `.msp`／`info.asm`，可通过「文件」菜单或 `⌘/Ctrl+O` 打开、`⌘/Ctrl+S` 保存当前编辑页。`.mui` 使用 JavaFX 画布预览组件与动画、切换设备比例和时间轴；V 皮肤可编辑元数据和部分组件参数，预览选中资源及同层静态图片布局。V 编辑器的“Lua 源码”页可编辑 `info.asm` 引用的 UTF-8 脚本，点击“保存皮肤”一同写回；缺失或无效文件会显示诊断，不会执行脚本。“运行态对照”页可加载 Unity 冻结帧快照或截图包，核对 `info.asm` 与 Lua 指纹后只读查看游戏画面和模块值；素材仍须另行核对。操作见 [V 运行画面对照](docs/v-runtime-compare.md)。

外层标签中的「4.x · UIS」工作区保留各 `.mui`／`.msz` 脚本标签及共享画布；每份 V 皮肤占一个独立标签。切换格式不会改变各自的预览和保存语义。

`.mui` 文本编辑会自动保存，也可点击“保存”或按系统保存快捷键；关闭时若保存失败会提示并保留窗口。保存成功但预览解析失败时，工具栏会单独提示。画布可拖动未旋转、未倾斜、未受动画或透视影响的普通图片组件，包括像素坐标锚点；写回时核对其真实来源文件、组件段和属性行，包含 `@include` 中的属性。组合段和父组件位置暂不参与拖拽。V 布局概览可拖动全屏背景／顶层中可投影的静态图片，改动组件偏移量后用自己的“保存皮肤”按钮写回；关闭 V 标签或主窗口时会提示未保存的修改。两种格式分别保留源文件中未被编辑的内容：`.mui` 的原文 section、注释、编码和行尾，以及 `.msp` 的未知 protobuf 字段和素材。

可直接打开 4.3.7 `.msz` 包。包有多个 `.mui` 时先选择一个脚本；编辑器把包安全展开到临时目录，供预览加载引用脚本和素材。所选脚本的文本编辑与画布拖拽会写回原 `.msz`；其他资源只用于预览。包在外部被修改时保存会报冲突，未写回内容保留在报错所示临时路径；关闭标签页或应用时若仍有同步错误，会阻止关闭。

## 运行

需要 JDK 22 或更新版本。项目自带 Maven Wrapper，无须单独安装 Maven。

```sh
./mvnw clean test
./mvnw javafx:run
```

也可以在启动时打开样例皮肤：

```sh
./mvnw javafx:run -Djavafx.args=examples/基本.mui
```

Windows 使用 `mvnw.cmd`。首次运行会下载 Maven 和 JavaFX 依赖。应用退出时把最近打开目录写入工作目录的 `config.json`；该文件不纳入版本控制。

## 固定帧调试

可以用同一皮肤、时间点和设备比例重复生成预览图，便于比较渲染修改：

```sh
./mvnw javafx:run '-Djavafx.args=--snapshot examples/基本.mui target/debug/basic-0.png 0 PC'
./mvnw javafx:run '-Djavafx.args=--trace-mui examples/基本.mui target/debug/basic-0.json 0 PC'
./mvnw javafx:run '-Djavafx.args=--snapshot-v /path/to/skin.msp target/debug/v-layer1.png 1 android 1920x1080'
```

`--snapshot` 和 `--trace-mui` 的最后两个参数可省略，默认是 `0` 毫秒和 `PC`。设备比例可填 `PC`、`PHONE`、`PHONE_LONG`、`IPAD` 等 `ResolutionInfo` 枚举名；对照 Android 真机时可填 `ANDROID:2376x1152`，直接输出该像素尺寸。截图写入指定 PNG 路径；`--trace-mui` 将同一帧有效组件的渲染器、坐标、原始属性和源文件行号写为 JSON，便于对照原生实现。解析和资源告警仍输出在终端及 `latest.log`。这些入口不启动编辑器窗口。

`--snapshot-v` 接受 `.msp`、皮肤目录或目录内的 `info.asm`；图层必须显式指定为全屏背景 `1` 或顶层 `4`，平台为 `windows`、`ios`、`android`，尺寸为输出及场景条件使用的 `宽x高`。路径包含空格时可传 `file:` URI，例如 `file:///Users/me/Downloads/skin%20copy.msp`。输出 PNG 背景透明，与 V 编辑器“布局概览”共用静态图片筛选、排序、资源读取和布局；终端会报告跳过数量。Lua、动画、动态条件和赛道层未包含，PNG 不能代表游戏完整运行画面。Unity 运行画面的对照条件见 [V 运行画面对照](docs/v-runtime-compare.md)。

批量检查 `.mui` 文件能否读取，以及查看 V 皮肤的元数据与组件：

```sh
./mvnw javafx:run '-Djavafx.args=--audit-mui /path/to/mui-directory'
./mvnw javafx:run '-Djavafx.args=--audit-msz /path/to/skin.msz'
./mvnw javafx:run '-Djavafx.args=--inspect-v /path/to/skin.msp'
./mvnw javafx:run '-Djavafx.args=--inspect-v --layout /path/to/skin.msp'
./mvnw javafx:run '-Djavafx.args=--inspect-v --layout --platform android /path/to/skin.msp'
```

`--audit-mui` 和 `--audit-msz` 只检查文本解析，不代表所有组件的显示都与游戏一致。后者在隔离临时目录中检查包内每个 `.mui`，结束后清理目录，不改动原包。`--inspect-v` 会输出 Lua 引用路径和读取状态；加 `--layout` 会列出全屏背景／顶层中可投影的静态自定义图片在 1920×1080 参考视口上的矩形、图层和顺序；默认 Windows，可用 `--platform` 选择 iOS 或 Android。检查独立 `.mui` 时若目录中包含完整资源，`@texpack` 会照常在皮肤目录下生成 `cache` 拆图文件。

## 项目结构

- `src/main/java/top/zedo/skin/uis/ui`：JavaFX 编辑界面与代码区
- `src/main/java/top/zedo/skin/uis/MuiSkinLoader.java`：读取 `.mui`、`@include` 与图片资源，生成一次加载结果
- `src/main/java/top/zedo/skin/uis/MuiDocument.java`：保留 `.mui` 原文结构的属性编辑与保存模型
- `src/main/java/top/zedo/skin/uis/MuiPositionEditor.java`：把预览组件的位移校验并写回其真实源属性行
- `src/main/java/top/zedo/skin/uis/MszSkinPackage.java`、`MszWorkspace.java`：4.3.7 `.msz` 包的脚本读取、隔离预览目录与安全回写层
- `src/main/java/top/zedo/skin/uis/UISSkin.java`：将加载结果同步到组件和渲染器
- `src/main/java/top/zedo/skin/uis/component`：各类预览渲染器
- `src/main/java/top/zedo/skin/v`：V 皮肤 `.msp`／`info.asm` 读写和基本属性编辑
- `src/main/proto/skin_v.proto`：来自 Emiria 的 V 皮肤数据结构
- `src/main/java/top/zedo/skin/plist`：纹理包拆图
- `src/main/resources`：应用样式和图标
- `src/test/java`：解析边界与关键计算测试
- `examples`：用于验证预览的 `.mui` 文件和资源

本仓库从 [ZXNoter 的 `UISEditor` 分支](https://github.com/ZX-Organization/ZXNoter/tree/UISEditor) 提取。原分支的 `UISEditor`、早期 `ZXNoterUIFrame` 和 `docs/UISEditorTest` 的相关提交历史已保留；提取基点是原仓库提交 `fc5441a4ed710d2cda43af43c1c605b73dc7c9e7`。原分支中未参与应用构建的旧窗口原型、旧渲染器和未完成的格式转换实验没有保留在当前文件树中，可从历史提交查阅。

## 格式依据与校准边界

Emiria 历史中找到的 UIS 解析器属于 5.0.0 原型，可用于核对 `@unit`、`w`、条件、定义与继承等规则；它不足以证明 4.3.7 的每处运行时行为。当前 `.mui` 预览保持原编辑器已接近游戏的画面作为基线。`UISPerspectiveTransform` 中的角度多项式来自本编辑器 2024 年的经验标定，未在 Emiria 原型中找到对应实现，现有测试固定了它的输出。4.x 样本中透明度常用 0–100，而 Emiria 5.0 原型按 0–255 处理，不能直接套用。

4.3.7 APK 的内置 UIS 脚本、真机校准及当前绘制差距见 [4.3.7 校准记录](docs/4.3.7-audit.md)；ARM64 原生解析器和组件工厂的静态取证见 [4.3.7 原生实现记录](docs/4.3.7-native.md)。`fsize` 单位与默认值、初始坐标取整、旋转截断、透明度量化和普通图片的像素锚点已按原生证据修正；其他类型的像素锚点与完整透视矩阵仍需处理。

连接可运行 4.3.7 的 Android 设备后，可用 [skin437_probe.py](scripts/skin437_probe.py) 生成九宫格纹理或透视九点标记、从一份 `.msz` 打包测试皮肤、推送到设备并保存带版本和皮肤选择信息的截图。操作步骤、实测坐标和已发现的脚本缓存行为写在校准记录中。[uis437_native.py](scripts/uis437_native.py) 可用相同 APK SHA-256 重建反汇编和属性哈希线索。

V 的 `SkinFile` 结构取自 Emiria 当前源码；两份真实 `.msp` 样本已验证读取、无修改字节级回写和修改标题后的素材及未知字段保留。V 编辑器的“布局概览”按 Emiria 的位置、尺寸、pivot 和同层顺序显示全屏背景／顶层的静态自定义图片，按参考视口与所选平台过滤可判定的场景条件。游玩区上下层使用赛道容器，当前不投影；动态条件、动画和特殊模块也不参与，因此不等同游戏运行画面。[V 布局校准记录](docs/v-layout.md) 记录了源码与 Unity 场景证据。所有组件类型的编辑和游戏内导入仍待验证。

V 编辑器保存前检查 `.msp` 原包或文件夹中的 `info.asm` 是否被外部修改；遇到冲突会报错并保留外部文件。成功保存后仍可继续编辑和保存。
