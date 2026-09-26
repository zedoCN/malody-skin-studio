# /// script
# requires-python = ">=3.10"
# dependencies = ["Pillow>=10,<13"]
# ///
"""Measure what changed when a Malody V skin Canvas was hidden in Unity."""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("full", type=Path, help="Game View with the skin layer visible")
    parser.add_argument("hidden", type=Path, help="Same paused frame with that layer hidden")
    parser.add_argument("--restored", type=Path, help="Optional capture after showing the layer again")
    parser.add_argument("--mask", type=Path, help="Optional output PNG of changed pixels")
    parser.add_argument("--threshold", type=int, default=3, help="Minimum channel difference, 0-255")
    args = parser.parse_args()
    if not 0 <= args.threshold <= 255:
        parser.error("--threshold must be between 0 and 255")

    with Image.open(args.full) as source, Image.open(args.hidden) as comparison:
        full = source.convert("RGB")
        hidden = comparison.convert("RGB")
    if full.size != hidden.size:
        parser.error(f"Capture sizes differ: {full.size} vs {hidden.size}")

    difference = ImageChops.difference(full, hidden)
    channels = difference.tobytes()
    mask = Image.frombytes("L", full.size, bytes(
        255 if max(channels[index:index + 3]) > args.threshold else 0
        for index in range(0, len(channels), 3)
    ))
    changed = mask.histogram()[255]
    report = {
        "width": full.width,
        "height": full.height,
        "threshold": args.threshold,
        "changedPixels": changed,
        "changedFraction": round(changed / (full.width * full.height), 6),
        "boundingBox": mask.getbbox(),
    }

    if args.restored:
        with Image.open(args.restored) as restored_image:
            restored = restored_image.convert("RGB")
        report["restoredExactly"] = full.size == restored.size and not ImageChops.difference(full, restored).getbbox()

    if args.mask:
        args.mask.parent.mkdir(parents=True, exist_ok=True)
        mask.save(args.mask)
        report["mask"] = str(args.mask)

    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
