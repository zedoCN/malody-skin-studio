#!/usr/bin/env python3
"""Summarize Emiria's frozen module export without treating raw parameters as runtime state."""

import argparse
import json
import math
from pathlib import Path


def finite(value: object, label: str) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(value):
        raise ValueError(f"{label} must be a finite number")
    return float(value)


def reject_constant(value: str) -> None:
    raise ValueError(f"Invalid JSON number: {value}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--name", help="Show only modules with this exact logical name")
    parser.add_argument("--all", action="store_true", help="Include unchanged comparable modules")
    args = parser.parse_args()

    with args.snapshot.open(encoding="utf-8") as stream:
        data = json.load(stream, parse_constant=reject_constant)
    if data.get("schemaVersion") != 1 or not isinstance(data.get("modules"), list):
        parser.error("Unsupported module export schema")
    if data.get("moduleCount") != len(data["modules"]):
        parser.error("moduleCount does not match modules length")
    if not isinstance(data.get("asmSha256"), str) or len(data["asmSha256"]) != 64:
        parser.error("Missing ASM SHA-256; regenerate the module export")

    rows = []
    errors = []
    comparable = 0
    matched = 0
    for index, module in enumerate(data["modules"]):
        if args.name is not None and module.get("name") != args.name:
            continue
        matched += 1
        if module.get("error"):
            errors.append({"index": index, "name": module.get("name"), "error": module["error"]})
            continue
        source, runtime = module.get("source"), module.get("runtime")
        if not isinstance(source, dict) or not isinstance(runtime, dict):
            errors.append({"index": index, "name": module.get("name"), "error": "missing source/runtime"})
            continue
        changes = {}
        if source.get("hasImage"):
            for axis in ("Width", "Height"):
                original = finite(source.get("image" + axis), f"module {index} image{axis}")
                actual = finite(runtime.get(axis.lower()), f"module {index} {axis.lower()}")
                if source.get("image" + axis + "Unit") == "Unit" and original > 0:
                    comparable += 1
                    if args.all or abs(actual - original) > 0.01:
                        changes[axis.lower()] = {
                            "sourceUnit": original,
                            "runtimeUnit": actual,
                            "ratio": round(actual / original, 6),
                        }
        source_alpha = source.get("alpha")
        runtime_alpha = runtime.get("alpha")
        if isinstance(source_alpha, int) and isinstance(runtime_alpha, int) and (
                args.all or source_alpha != runtime_alpha):
            changes["alpha"] = {"source": source_alpha, "runtime": runtime_alpha}
        if changes:
            rows.append({"index": index, "name": module.get("name"), "layer": source.get("layer"),
                         "imageFile": source.get("imageFile"), "changes": changes})

    print(json.dumps({
        "skinTitle": data.get("skinTitle"),
        "chartFile": data.get("chartFile"),
        "audioTimeMs": finite(data.get("audioTimeMs"), "audioTimeMs"),
        "asmSha256": data["asmSha256"],
        "luaHash": data.get("luaHash"),
        "moduleCount": len(data["modules"]),
        "matchedModules": matched,
        "comparableDimensions": comparable,
        "duplicateNames": data.get("duplicateNames", []),
        "errors": errors,
        "rows": rows,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
