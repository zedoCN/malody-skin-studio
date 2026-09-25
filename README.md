# Malody Skin Studio

马老弟音游的非官方皮肤编辑与预览工具。当前支持 `.mui` 皮肤，使用 JavaFX，支持打开皮肤文件、预览组件与动画、切换设备比例，以及调整预览时间轴。

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

## 项目结构

- `src/main/java/top/zedo/skin/uis/ui`：JavaFX 编辑界面与代码区
- `src/main/java/top/zedo/skin/uis/MuiSkinLoader.java`：读取 `.mui`、`@include` 与图片资源，生成一次加载结果
- `src/main/java/top/zedo/skin/uis/UISSkin.java`：将加载结果同步到组件和渲染器
- `src/main/java/top/zedo/skin/uis/component`：各类预览渲染器
- `src/main/java/top/zedo/skin/plist`：纹理包拆图
- `src/main/resources`：应用样式和图标
- `src/test/java`：解析边界与关键计算测试
- `examples`：用于验证预览的 `.mui` 文件和资源

本仓库从 [ZXNoter 的 `UISEditor` 分支](https://github.com/ZX-Organization/ZXNoter/tree/UISEditor) 提取。原分支的 `UISEditor`、早期 `ZXNoterUIFrame` 和 `docs/UISEditorTest` 的相关提交历史已保留；提取基点是原仓库提交 `fc5441a4ed710d2cda43af43c1c605b73dc7c9e7`。原分支中未参与应用构建的旧窗口原型、旧渲染器和未完成的格式转换实验没有保留在当前文件树中，可从历史提交查阅。

## 后续适配

当前目标是现有马老弟 `.mui` 皮肤。马老弟 V 的格式和预览行为尚未验证，也没有在此版本实现。取得真实样例和格式依据后，再确定解析与预览需要调整的边界。
