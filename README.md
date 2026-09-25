# Malody Skin Studio

马老弟音游的非官方皮肤编辑与预览工具。支持打开 4.x `.mui` 和 Malody V `.msp`／`info.asm`。`.mui` 使用 JavaFX 画布预览组件与动画、切换设备比例和时间轴；V 皮肤可编辑元数据和部分组件参数，并预览选中组件的图片资源。

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
```

最后两个参数可省略，默认是 `0` 毫秒和 `PC`。设备比例可填 `PC`、`PHONE`、`PHONE_LONG`、`IPAD` 等 `ResolutionInfo` 枚举名。截图写入指定 PNG 路径；解析和资源告警仍输出在终端及 `latest.log`。此入口只渲染一帧，不启动编辑器窗口。

批量检查 `.mui` 文件能否读取，以及查看 V 皮肤的元数据与组件：

```sh
./mvnw javafx:run '-Djavafx.args=--audit-mui /path/to/mui-directory'
./mvnw javafx:run '-Djavafx.args=--inspect-v /path/to/skin.msp'
```

`--audit-mui` 只检查文本解析，不代表所有组件的显示都与游戏一致。检查时若目录中包含完整资源，`@texpack` 会照常在皮肤目录下生成 `cache` 拆图文件。

## 项目结构

- `src/main/java/top/zedo/skin/uis/ui`：JavaFX 编辑界面与代码区
- `src/main/java/top/zedo/skin/uis/MuiSkinLoader.java`：读取 `.mui`、`@include` 与图片资源，生成一次加载结果
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

4.3.7 APK 的内置 UIS 脚本及当前绘制差距见 [4.3.7 校准记录](docs/4.3.7-audit.md)。

V 的 `SkinFile` 结构取自 Emiria 当前源码，并用真实 `.msp` 样本验证了读取。V 编辑器目前是元数据和部分组件参数编辑器，尚未实现完整场景合成预览、所有组件类型的编辑或游戏内视觉一致性验证。
