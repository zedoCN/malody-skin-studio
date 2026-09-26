#!/usr/bin/env python3
"""Build a 4.3.7 skin probe and capture evidence from an ADB device.

Generation and device capture need only Python's standard library and adb;
`compare` additionally needs ffmpeg to read PNG pixels. A probe always inherits
its UIS script and assets from one package without editing the source .msz.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import struct
import time
import zlib
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile


APP_ID = "me.mugzone.malody"
SKIN_DIR = "/sdcard/data/malody/skin"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


def write_rgb_png(path, width, height, scanlines):
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += png_chunk(b"IDAT", zlib.compress(scanlines))
    png += png_chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def color_panel(args):
    colors = (
        ((255, 0, 0), (0, 255, 0), (0, 0, 255)),
        ((255, 255, 0), (255, 0, 255), (0, 255, 255)),
        ((100, 0, 0), (0, 100, 0), (0, 0, 100)),
    )
    pixels = bytearray()
    for y in range(30):
        pixels.append(0)
        for x in range(30):
            pixels.extend(colors[y // 10][x // 10])

    write_rgb_png(args.output, 30, 30, bytes(pixels))
    print(f"九宫格色块: {args.output.resolve()}\nSHA-256: {sha256(args.output)}")


def scale9_fixture(args):
    lines = [
        "_codex-scale9",
        "    type=5",
        f"    tex={args.asset_name}",
        f"    rect={args.rect}",
        f"    size={args.size}",
        f"    pos={args.pos}",
        "    anchor=4",
        "    zindex=500",
    ]
    if args.size2 is not None:
        lines.append(f"    size2={args.size2}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n")
    print(f"九宫格节点: {args.output.resolve()}")


def measure_panel(args):
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("measure-panel 需要 ffmpeg")
    png = args.image.read_bytes()
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"不是 PNG 文件: {args.image}")
    width, height = struct.unpack(">II", png[16:24])
    x0, y0, area_width, area_height = map(int, args.roi.split(","))
    if x0 < 0 or y0 < 0 or area_width <= 0 or area_height <= 0 or x0 + area_width > width or y0 + area_height > height:
        raise ValueError("ROI 必须位于截图范围内，格式为 x,y,width,height")
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(args.image), "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
        capture_output=True, check=True,
    ).stdout
    if len(raw) != width * height * 3:
        raise ValueError(f"PNG 像素长度不匹配: {args.image}")
    colors = {
        "red": (255, 0, 0), "green": (0, 255, 0), "blue": (0, 0, 255),
        "yellow": (255, 255, 0), "magenta": (255, 0, 255), "cyan": (0, 255, 255),
    }
    boxes = {}
    for name, expected in colors.items():
        xs, ys = [], []
        for y in range(y0, y0 + area_height):
            for x in range(x0, x0 + area_width):
                offset = (y * width + x) * 3
                if all(abs(raw[offset + channel] - expected[channel]) < 8 for channel in range(3)):
                    xs.append(x)
                    ys.append(y)
        boxes[name] = [min(xs), min(ys), max(xs), max(ys)] if xs else None
    report = {"image": str(args.image.resolve()), "roi": [x0, y0, area_width, area_height], "color_boxes": boxes}
    result = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    print(result, end="")


def perspective_markers(args):
    marker_row = bytes((19, 245, 71)) * 16
    write_rgb_png(args.asset_output, 16, 16, (b"\0" + marker_row) * 16)
    lines = [] if args.no_apply else ["@apply 3d"]
    if not args.no_angle:
        lines.append(f"@angle {args.angle}")
    for row, y in enumerate((15, 50, 85)):
        for column, x in enumerate((20, 50, 80)):
            lines.extend((
                f"_codex-perspective-{row}-{column}",
                "    type=0",
                f"    tex={args.asset_output.name}",
                f"    pos={x}%,{y}%",
                "    size=12,12",
                "    anchor=4",
                "    zindex=50",
            ))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n")
    print(f"透视标记: {args.output.resolve()}\n纹理: {args.asset_output.resolve()}\nSHA-256: {sha256(args.asset_output)}")


def runs(values, gap):
    groups = []
    for value in sorted(values):
        if not groups or value > groups[-1][-1] + gap:
            groups.append([value])
        else:
            groups[-1].append(value)
    return groups


def marker_rows(path):
    if shutil.which("ffmpeg") is None:
        raise RuntimeError("compare 需要 ffmpeg")
    png = path.read_bytes()
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"不是 PNG 文件: {path}")
    width, height = struct.unpack(">II", png[16:24])
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"],
        capture_output=True, check=True,
    ).stdout
    if len(raw) != width * height * 4:
        raise ValueError(f"PNG 像素长度不匹配: {path}")
    colored = {}
    for y in range(height):
        line = y * width * 4
        xs = []
        for x in range(width):
            offset = line + x * 4
            if raw[offset] < 90 and raw[offset + 1] > 175 and raw[offset + 2] < 170:
                xs.append(x)
        if xs:
            colored[y] = xs
    result = []
    for ys in runs(colored, 2):
        xs = {x for y in ys for x in colored[y]}
        columns = runs(xs, 3)
        result.append({
            "y": (ys[0] + ys[-1]) / 2,
            "x": [(column[0] + column[-1]) / 2 for column in columns],
        })
    if len(result) != 3 or any(len(row["x"]) < 2 for row in result):
        raise ValueError(f"应检测到三行、每行至少两个绿色标记: {path}: {result}")
    return result


def compare(args):
    metadata = args.device.with_suffix(".json")
    if metadata.is_file():
        selected = json.loads(metadata.read_text()).get("skin_selection", "")
        if not selected or "," in selected:
            raise ValueError(f"设备截图没有唯一选中的测试皮肤: {selected!r}")
    editor = marker_rows(args.editor)
    device = marker_rows(args.device)
    deltas = []
    for expected, actual in zip(editor, device):
        deltas.extend((actual["x"][0] - expected["x"][0], actual["x"][-1] - expected["x"][-1]))
        if len(expected["x"]) == len(actual["x"]) == 3:
            deltas.append(actual["x"][1] - expected["x"][1])
    scale = (device[-1]["y"] - device[0]["y"]) / (editor[-1]["y"] - editor[0]["y"])
    offset = device[0]["y"] - scale * editor[0]["y"]
    report = {
        "editor": str(args.editor.resolve()),
        "device": str(args.device.resolve()),
        "editor_markers": editor,
        "device_markers": device,
        "max_horizontal_error_px": max(abs(delta) for delta in deltas),
        "device_y_scale": scale,
        "device_y_offset_px": offset,
        "middle_row_y_residual_px": device[1]["y"] - (scale * editor[1]["y"] + offset),
    }
    result = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    print(result, end="")


def adb(serial, *args):
    command = ["adb", "-s", serial, *args]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"adb 失败: {command}")
    return result.stdout.strip()


def build(args):
    base = args.base.resolve()
    output = args.output.resolve()
    if base == output:
        raise ValueError("输出文件不能覆盖原皮肤包")
    with ZipFile(base) as source:
        names = set(source.namelist())
        if args.script not in names:
            raise ValueError(f"皮肤包中找不到 {args.script}")
        script = source.read(args.script)
        if b"@version 4.3.7" not in script:
            raise ValueError("源皮肤脚本未声明 @version 4.3.7")
        replacement = {args.script: script + b"\n" + args.append.read_bytes() + b"\n"}
        for asset in args.asset:
            replacement[asset.name] = asset.read_bytes()
        output.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(output, "w") as target:
            for item in source.infolist():
                if item.filename not in replacement:
                    target.writestr(item, source.read(item.filename))
            for name, data in replacement.items():
                target.writestr(name, data)
        if "preview.json" not in names:
            print("提示：源包没有 preview.json；游戏皮肤列表可能只显示文件名")
    print(f"测试包: {output}\nSHA-256: {sha256(output)}")


def push(args):
    package = args.package.resolve()
    if package.suffix.lower() != ".msz":
        raise ValueError("只能推送 .msz 皮肤包")
    destination = f"{SKIN_DIR}/{package.name}"
    adb(args.serial, "push", str(package), destination)
    remote_hash = adb(args.serial, "shell", "sha256sum", destination).split()[0]
    local_hash = sha256(package)
    if remote_hash != local_hash:
        raise RuntimeError(f"设备上的 SHA-256 不一致：{remote_hash} != {local_hash}")
    print(f"已推送: {destination}\nSHA-256: {local_hash}")
    print("在游戏皮肤列表选中测试包；修改已有包后请重启客户端，游戏会缓存 UIS 脚本。")


def capture(args):
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    package_info = adb(args.serial, "shell", "dumpsys", "package", APP_ID)
    version = re.search(r"versionName=([^\s]+)", package_info)
    if not version or version.group(1) != "4.3.7":
        raise RuntimeError("设备上的 Malody 版本不是 4.3.7")
    remote = f"/sdcard/malody-437-probe-{os.getpid()}.png"
    adb(args.serial, "shell", "screencap", "-p", remote)
    try:
        adb(args.serial, "pull", remote, str(output))
    finally:
        adb(args.serial, "shell", "rm", "-f", remote)
    png = output.read_bytes()
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError("设备截图不是 PNG")
    width, height = struct.unpack(">II", png[16:24])
    config = json.loads(adb(args.serial, "shell", "cat", "/sdcard/data/malody/config.json"))
    metadata = {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "device_serial": args.serial,
        "app_version": version.group(1),
        "skin_selection": config.get("user_skin_name", ""),
        "screenshot_width": width,
        "screenshot_height": height,
        "screenshot_sha256": sha256(output),
    }
    output.with_suffix(".json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    logs = adb(args.serial, "shell", "ls", "-t", "/sdcard/data/malody/log").splitlines()
    if logs:
        latest = logs[0].strip()
        if re.fullmatch(r"log-[\w+.-]+\.txt", latest):
            log_text = adb(args.serial, "shell", "cat", f"/sdcard/data/malody/log/{latest}")
            output.with_suffix(".log").write_text(log_text + "\n")
    print(f"截图: {output}\n证据: {output.with_suffix('.json')}")


def capture_burst(args):
    if args.count < 1 or args.count > 120 or args.interval < 0.1:
        raise ValueError("count 应为 1–120，interval 应至少为 0.1 秒")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    package_info = adb(args.serial, "shell", "dumpsys", "package", APP_ID)
    version = re.search(r"versionName=([^\s]+)", package_info)
    if not version or version.group(1) != "4.3.7":
        raise RuntimeError("设备上的 Malody 版本不是 4.3.7")
    config = json.loads(adb(args.serial, "shell", "cat", "/sdcard/data/malody/config.json"))
    frames = []
    next_capture = time.monotonic()
    for index in range(args.count):
        delay = next_capture - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        captured_at = datetime.now(timezone.utc).isoformat()
        result = subprocess.run(["adb", "-s", args.serial, "exec-out", "screencap", "-p"],
                                capture_output=True)
        if result.returncode or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError(f"第 {index} 帧截图失败: {result.stderr.decode(errors='replace').strip()}")
        filename = f"frame-{index:03d}.png"
        path = output / filename
        path.write_bytes(result.stdout)
        frames.append({"file": filename, "captured_at_utc": captured_at, "sha256": sha256(path)})
        next_capture += args.interval
    manifest = {"device_serial": args.serial, "app_version": version.group(1),
                "skin_selection": config.get("user_skin_name", ""), "interval_seconds": args.interval,
                "frames": frames}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"连拍: {len(frames)} 帧\n证据: {output / 'manifest.json'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    panel_parser = commands.add_parser("panel", help="生成 30x30 九宫格测试纹理")
    panel_parser.add_argument("--output", type=Path, required=True)
    panel_parser.set_defaults(run=color_panel)
    scale9_parser = commands.add_parser("scale9", help="生成单个九宫格节点，隔离 size2 参数")
    scale9_parser.add_argument("--output", type=Path, required=True)
    scale9_parser.add_argument("--asset-name", default="panel.png")
    scale9_parser.add_argument("--rect", default="10px,10px,10px,10px")
    scale9_parser.add_argument("--size", default="90,90")
    scale9_parser.add_argument("--pos", default="50%,25%")
    scale9_parser.add_argument("--size2", help="保留原始 UIS 表达式，例如 0,540px")
    scale9_parser.set_defaults(run=scale9_fixture)
    measure_parser = commands.add_parser("measure-panel", help="量取九宫格纯色色块坐标，需要 ffmpeg")
    measure_parser.add_argument("--image", type=Path, required=True)
    measure_parser.add_argument("--roi", required=True, help="区域 x,y,width,height，避免画面其他纯色影响")
    measure_parser.add_argument("--output", type=Path)
    measure_parser.set_defaults(run=measure_panel)
    perspective_parser = commands.add_parser("perspective", help="生成 3x3 透视标记和纯色纹理")
    perspective_parser.add_argument("--angle", type=int, default=0)
    perspective_parser.add_argument("--no-apply", action="store_true", help="省略 @apply 3d，作为平面控制组")
    perspective_parser.add_argument("--no-angle", action="store_true", help="省略 @angle，测试默认角度")
    perspective_parser.add_argument("--output", type=Path, required=True)
    perspective_parser.add_argument("--asset-output", type=Path, required=True)
    perspective_parser.set_defaults(run=perspective_markers)
    compare_parser = commands.add_parser("compare", help="量取透视九点标记并比较真机与编辑器坐标，需要 ffmpeg")
    compare_parser.add_argument("--editor", type=Path, required=True)
    compare_parser.add_argument("--device", type=Path, required=True)
    compare_parser.add_argument("--output", type=Path)
    compare_parser.set_defaults(run=compare)
    build_parser = commands.add_parser("build", help="从同一个 .msz 继承脚本和资源并附加测试节点")
    build_parser.add_argument("--base", type=Path, required=True)
    build_parser.add_argument("--script", default="script-key-4K.mui")
    build_parser.add_argument("--append", type=Path, required=True)
    build_parser.add_argument("--asset", type=Path, action="append", default=[])
    build_parser.add_argument("--output", type=Path, required=True)
    build_parser.set_defaults(run=build)
    push_parser = commands.add_parser("push", help="推送测试包并校验 SHA-256")
    push_parser.add_argument("--serial", required=True)
    push_parser.add_argument("package", type=Path)
    push_parser.set_defaults(run=push)
    capture_parser = commands.add_parser("capture", help="保存真机截图、版本、皮肤选择和游戏日志")
    capture_parser.add_argument("--serial", required=True)
    capture_parser.add_argument("--output", type=Path, required=True)
    capture_parser.set_defaults(run=capture)
    burst_parser = commands.add_parser("capture-burst", help="用 adb exec-out 连拍并记录各帧时间与 SHA-256")
    burst_parser.add_argument("--serial", required=True)
    burst_parser.add_argument("--output", type=Path, required=True, help="帧文件和 manifest.json 的输出目录")
    burst_parser.add_argument("--count", type=int, default=24)
    burst_parser.add_argument("--interval", type=float, default=1.0, help="目标截图间隔，秒")
    burst_parser.set_defaults(run=capture_burst)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
