#!/usr/bin/env python3
"""Package captures observed around an Emiria frozen module state for the V runtime viewer."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import tempfile


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
MAX_DIMENSION = 8192
MAX_JSON_BYTES = 1024 * 1024
MAX_MODULE_BYTES = 16 * MAX_JSON_BYTES
MAX_PNG_BYTES = 64 * MAX_JSON_BYTES


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def object_field(value: object, label: str) -> dict:
    require(isinstance(value, dict), f"{label} must be an object")
    return value


def positive_int(value: object, label: str, maximum: int | None = None) -> int:
    require(type(value) is int and value > 0 and (maximum is None or value <= maximum),
            f"{label} must be an integer in 1..{maximum}" if maximum else
            f"{label} must be a positive integer")
    return value


def finite_number(value: object, label: str) -> float:
    require(type(value) in (int, float) and math.isfinite(value), f"{label} must be finite")
    return float(value)


def read_limited(path: Path, label: str, limit: int) -> bytes:
    with path.open("rb") as stream:
        raw = stream.read(limit + 1)
    require(len(raw) < limit, f"{label} reaches or exceeds {limit} bytes")
    return raw


def read_json(path: Path, label: str, limit: int = MAX_JSON_BYTES) -> tuple[bytes, dict]:
    raw = read_limited(path, label, limit)
    def reject_constant(value: str) -> None:
        raise ValueError(f"{label} contains invalid JSON number {value}")
    data = json.loads(raw, parse_constant=reject_constant)
    return raw, object_field(data, label)


def utc(data: dict, label: str, key: str = "utc") -> datetime:
    value = data.get(key)
    require(isinstance(value, str), f"{label}.{key} must be an ISO UTC time")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(f"{label}.{key} must be an ISO UTC time") from error
    require(parsed.tzinfo is not None and parsed.utcoffset().total_seconds() == 0,
            f"{label}.{key} must be UTC")
    return parsed.astimezone(timezone.utc)


def file_record(name: str, raw: bytes) -> dict:
    return {"path": name, "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}


def normalized_modules(data: dict) -> dict:
    rows = data.get("modules")
    require(isinstance(rows, list), "modules must be an array")
    require(type(data.get("moduleCount")) is int and data["moduleCount"] == len(rows),
            "moduleCount does not match modules")
    require(all(isinstance(row, dict) for row in rows), "modules must contain objects")
    result = dict(data)
    result.pop("utc", None)
    result["modules"] = sorted(json.dumps(row, sort_keys=True, ensure_ascii=False,
                                          separators=(",", ":")) for row in rows)
    return result


def png_size(raw: bytes) -> tuple[int, int]:
    require(raw.startswith(PNG_SIGNATURE) and len(raw) >= 24 and raw[12:16] == b"IHDR",
            "image is not a PNG with IHDR")
    require(struct.unpack(">I", raw[8:12])[0] == 13, "invalid PNG IHDR length")
    width, height = struct.unpack(">II", raw[16:24])
    positive_int(width, "PNG width", MAX_DIMENSION)
    positive_int(height, "PNG height", MAX_DIMENSION)
    return width, height


def capture(path: Path, label: str) -> tuple[bytes, dict, bytes, tuple[int, int], tuple[int, int], datetime]:
    raw, data = read_json(path, label)
    require(data.get("captureKind") == "game_view", f"{label}.captureKind must be game_view")
    view = object_field(data.get("gameView"), f"{label}.gameView")
    source = (positive_int(view.get("sourceWidth"), f"{label}.gameView.sourceWidth", MAX_DIMENSION),
              positive_int(view.get("sourceHeight"), f"{label}.gameView.sourceHeight", MAX_DIMENSION))
    artifact = object_field(data.get("artifact"), f"{label}.artifact")
    require(artifact.get("mimeType") == "image/png", f"{label} must contain image/png")
    image_path = artifact.get("absolutePath")
    require(isinstance(image_path, str) and Path(image_path).is_absolute(),
            f"{label}.artifact.absolutePath must be absolute")
    image = read_limited(Path(image_path), f"{label} PNG", MAX_PNG_BYTES)
    actual_hash = hashlib.sha256(image).hexdigest()
    declared_hash = artifact.get("sha256")
    require(isinstance(declared_hash, str) and SHA256.fullmatch(declared_hash) is not None
            and declared_hash == actual_hash, f"{label} PNG SHA-256 mismatch")
    require(type(artifact.get("byteLength")) is int and artifact["byteLength"] == len(image),
            f"{label} PNG byteLength mismatch")
    size = png_size(image)
    require(type(artifact.get("width")) is int and type(artifact.get("height")) is int
            and size == (artifact["width"], artifact["height"]), f"{label} PNG dimensions mismatch")
    return raw, data, image, source, size, utc(data, label, "createdAtUtc")


def build_bundle(args: argparse.Namespace) -> dict:
    before_raw, status = read_json(args.status_before, "status-before")
    after_raw, _ = read_json(args.status_after, "status-after")
    require(before_raw == after_raw, "status changed during capture")
    require(status.get("phase") == "chart-time-frozen", "status is not chart-time-frozen")
    require(status.get("hasTime") is True, "status has no frozen time")
    frame = positive_int(status.get("unityFrame"), "status.unityFrame")
    first_raw, first = read_json(args.modules_before, "modules-before", MAX_MODULE_BYTES)
    second_raw, second = read_json(args.modules_after, "modules-after", MAX_MODULE_BYTES)
    require(first.get("schemaVersion") == second.get("schemaVersion") == 1,
            "unsupported module schema")
    require(normalized_modules(first) == normalized_modules(second),
            "module exports changed during capture")
    for key in ("asmSha256", "luaHash"):
        require(isinstance(first.get(key), str) and bool(first[key]) and first[key] == second.get(key),
                f"module {key} changed or is missing")
    require(SHA256.fullmatch(first["asmSha256"]) is not None, "invalid asmSha256")
    for key in ("audioTimeMs", "chartTimeMs", "displayTimeMs"):
        reference = finite_number(status.get(key), f"status.{key}")
        for label, data in (("modules-before", first), ("modules-after", second)):
            require(abs(finite_number(data.get(key), f"{label}.{key}") - reference) < 0.01,
                    f"{label}.{key} differs from status")

    capture_raw, _, image, source, size, first_time = capture(args.capture_first, "capture-first")
    _, _, second_image, second_source, second_size, second_time = capture(
        args.capture_second, "capture-second")
    require(source == second_source, "Game View source dimensions changed")
    require(size == second_size and image == second_image, "Game View image changed")
    require(utc(first, "modules-before") <= first_time <= second_time <= utc(second, "modules-after"),
            "capture timestamps are outside the module export interval")

    output = args.output
    require(not os.path.lexists(output), f"output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    try:
        files = {}
        for key, name, content in (("status", "status.json", before_raw),
                                   ("modules", "modules.json", first_raw),
                                   ("capture", "capture.json", capture_raw),
                                   ("image", "game-view.png", image)):
            (temporary / name).write_bytes(content)
            files[key] = file_record(name, content)
        bundle = {"kind": "malody-v-runtime-capture", "schemaVersion": 1,
                  "files": files,
                  "image": {"sourceWidth": source[0], "sourceHeight": source[1],
                            "width": size[0], "height": size[1]},
                  "audioTimeMs": finite_number(status["audioTimeMs"], "status.audioTimeMs"),
                  "frozenUnityFrame": frame, "captureStable": True}
        (temporary / "bundle.json").write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n",
                                                encoding="utf-8")
        require(not os.path.lexists(output), f"output already exists: {output}")
        os.rename(temporary, output)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    return bundle


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("status-before", "status-after", "modules-before", "modules-after",
                 "capture-first", "capture-second", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    args = parser.parse_args()
    try:
        bundle = build_bundle(args)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))
    print(json.dumps({"output": str(args.output.resolve()), "bundle": bundle}, ensure_ascii=False))


if __name__ == "__main__":
    main()
