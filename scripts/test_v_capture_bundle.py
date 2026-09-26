"""Small synthetic checks for the frozen-frame bundle contract."""

import argparse
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

import v_capture_bundle


def png(width: int, height: int) -> bytes:
    def chunk(kind: bytes, content: bytes) -> bytes:
        return (struct.pack(">I", len(content)) + kind + content
                + struct.pack(">I", zlib.crc32(kind + content)))
    pixels = b"".join(b"\x00" + b"\x00\x00\x00" * width for _ in range(height))
    return (v_capture_bundle.PNG_SIGNATURE
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


class CaptureBundleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.image = png(3, 2)
        self.png_path = self.root / "frame.png"
        self.png_path.write_bytes(self.image)
        self.status = {"phase": "chart-time-frozen", "hasTime": True, "unityFrame": 42,
                       "audioTimeMs": 100.0, "chartTimeMs": 99.0, "displayTimeMs": 98.0,
                       "utc": "2026-09-26T00:00:00Z"}
        self.modules = {"schemaVersion": 1, "utc": "2026-09-26T00:00:01Z",
                        "moduleCount": 2, "modules": [{"name": "a"}, {"name": "b"}],
                        "asmSha256": "a" * 64, "luaHash": "b" * 32,
                        "audioTimeMs": 100.001, "chartTimeMs": 99.001,
                        "displayTimeMs": 98.001}
        self.capture = {"captureKind": "game_view",
                        "createdAtUtc": "2026-09-26T00:00:02Z",
                        "gameView": {"sourceWidth": 6, "sourceHeight": 4},
                        "artifact": {"mimeType": "image/png", "absolutePath": str(self.png_path),
                                     "width": 3, "height": 2,
                                     "byteLength": len(self.image),
                                     "sha256": hashlib.sha256(self.image).hexdigest()}}
        self.args = argparse.Namespace(
            status_before=self.root / "status-before.json", status_after=self.root / "status-after.json",
            modules_before=self.root / "modules-before.json", modules_after=self.root / "modules-after.json",
            capture_first=self.root / "capture-first.json", capture_second=self.root / "capture-second.json",
            output=self.root / "bundle")
        self.write(self.args.status_before, self.status)
        self.args.status_after.write_bytes(self.args.status_before.read_bytes())
        self.write(self.args.modules_before, self.modules)
        self.write(self.args.modules_after, {**self.modules, "utc": "2026-09-26T00:00:04Z",
                                             "modules": list(reversed(self.modules["modules"]))})
        self.write(self.args.capture_first, self.capture)
        self.write(self.args.capture_second,
                   {**self.capture, "createdAtUtc": "2026-09-26T00:00:03Z"})

    @staticmethod
    def write(path: Path, value: dict) -> None:
        path.write_text(json.dumps(value), encoding="utf-8")

    def test_bundle_accepts_reordered_modules(self) -> None:
        bundle = v_capture_bundle.build_bundle(self.args)
        self.assertEqual(bundle["kind"], "malody-v-runtime-capture")
        self.assertEqual(bundle["image"], {"sourceWidth": 6, "sourceHeight": 4,
                                            "width": 3, "height": 2})
        self.assertEqual(bundle["files"]["image"]["path"], "game-view.png")
        self.assertEqual((self.args.output / "game-view.png").read_bytes(), self.image)
        self.assertEqual(json.loads((self.args.output / "bundle.json").read_text()), bundle)
        with self.assertRaisesRegex(ValueError, "output already exists"):
            v_capture_bundle.build_bundle(self.args)

    def test_rejects_changed_capture_without_partial_output(self) -> None:
        changed = dict(self.capture)
        changed["gameView"] = {"sourceWidth": 7, "sourceHeight": 4}
        self.write(self.args.capture_second, changed)
        with self.assertRaisesRegex(ValueError, "source dimensions changed"):
            v_capture_bundle.build_bundle(self.args)
        self.assertFalse(self.args.output.exists())
        self.assertEqual(list(self.root.glob(".bundle.*")), [])

    def test_rejects_time_and_png_metadata_mismatch(self) -> None:
        self.write(self.args.modules_before, {**self.modules, "audioTimeMs": 100.01})
        self.write(self.args.modules_after, {**self.modules, "utc": "2026-09-26T00:00:04Z",
                                             "audioTimeMs": 100.01})
        with self.assertRaisesRegex(ValueError, "audioTimeMs differs"):
            v_capture_bundle.build_bundle(self.args)
        self.write(self.args.modules_before, self.modules)
        self.write(self.args.modules_after, {**self.modules, "utc": "2026-09-26T00:00:04Z"})
        changed = dict(self.capture)
        changed["artifact"] = {**self.capture["artifact"], "width": 4}
        self.write(self.args.capture_first, changed)
        with self.assertRaisesRegex(ValueError, "PNG dimensions mismatch"):
            v_capture_bundle.build_bundle(self.args)
        self.assertFalse(self.args.output.exists())

    def test_rejects_java_loader_limits(self) -> None:
        changed = dict(self.capture)
        changed["gameView"] = {"sourceWidth": 8193, "sourceHeight": 4}
        self.write(self.args.capture_first, changed)
        with self.assertRaisesRegex(ValueError, "1..8192"):
            v_capture_bundle.build_bundle(self.args)
        self.write(self.args.capture_first, self.capture)
        self.args.status_after.write_bytes(b" " * (v_capture_bundle.MAX_JSON_BYTES + 1))
        with self.assertRaisesRegex(ValueError, "exceeds"):
            v_capture_bundle.build_bundle(self.args)
        self.args.status_after.write_bytes(b" " * v_capture_bundle.MAX_JSON_BYTES)
        with self.assertRaisesRegex(ValueError, "exceeds"):
            v_capture_bundle.build_bundle(self.args)
        self.assertFalse(self.args.output.exists())

    def test_rejects_non_png_capture_manifest(self) -> None:
        changed = dict(self.capture)
        changed["artifact"] = {**self.capture["artifact"], "mimeType": "image/jpeg"}
        self.write(self.args.capture_first, changed)
        with self.assertRaisesRegex(ValueError, "image/png"):
            v_capture_bundle.build_bundle(self.args)


if __name__ == "__main__":
    unittest.main()
