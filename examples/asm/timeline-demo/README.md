# ASM 时间轴演示

在编辑器中打开本目录或 `info.asm`，进入“布局概览”。点击播放、暂停或拖动时间轴：光点在 2 秒内向右移动并渐显，停留 0.4 秒后折返。

这是独立制作的最小样例，只使用 ASM 内置动画，不依赖 Lua 或游戏事件。真实 V 皮肤中由 Lua 或事件控制的变化仍需在游戏中核对。

需要重新生成 `info.asm` 和 `orb.png` 时，从仓库根目录运行：

```bash
./mvnw -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/v-demo-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/v-demo-classpath.txt)" top.zedo.skin.v.VTimelineSample
```
