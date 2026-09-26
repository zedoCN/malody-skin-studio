#!/usr/bin/env python3
"""Repeat the read-only ARM64 UIS extraction used for docs/4.3.7-native.md.

Requires llvm-objdump, capstone and pyelftools; writes only to an output directory.
"""

import argparse
import bisect
import hashlib
import shutil
import subprocess
import zipfile
from pathlib import Path


APK_SHA256 = "7b6f5650481f6b1e04ebe8eec93f18c2205521522f94da3f129cfb0c37044138"
PROPERTY_NAMES = "tex pos size anchor type rotate scale skew opacity zindex flip color rect size2 text fsize align part hide motion name include includex unit angle apply define if version source font value file speed toggle loop frame ani image array frames layout parent clip".split()


def bkdr(value):
    result = 0
    for character in value.encode():
        result = (result * 131 + character) & ((1 << 64) - 1)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--objdump", default=shutil.which("llvm-objdump") or "/opt/homebrew/opt/llvm/bin/llvm-objdump")
    args = parser.parse_args()

    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if digest != APK_SHA256:
        parser.error(f"APK SHA-256 不匹配 4.3.7 校准版本: {digest}")
    try:
        import capstone
        from elftools.elf.elffile import ELFFile
        from elftools.dwarf.callframe import FDE
    except ImportError as error:
        parser.error(f"缺少反汇编依赖，请安装 capstone pyelftools: {error}")
    objdump = args.objdump
    if not Path(objdump).is_file() and shutil.which(objdump) is None:
        parser.error("找不到 llvm-objdump；可用 --objdump 指定路径")

    args.output.mkdir(parents=True, exist_ok=True)
    library = args.output / "libmalodycpp.so"
    with zipfile.ZipFile(args.apk) as archive:
        library.write_bytes(archive.read("lib/arm64-v8a/libmalodycpp.so"))
    with (args.output / "uis-arm64.asm").open("w") as assembly:
        subprocess.run([objdump, "-d", "--no-show-raw-insn", "--start-address=0x2c8000",
                        "--stop-address=0x2d9000", str(library)], stdout=assembly, check=True)

    contents = library.read_bytes()
    with library.open("rb") as stream:
        elf = ELFFile(stream)
        text = elf.get_section_by_name(".text")
        functions = sorted((entry["initial_location"], entry["initial_location"] + entry["address_range"])
                           for entry in elf.get_dwarf_info().EH_CFI_entries() if isinstance(entry, FDE))

        def owner(address):
            index = bisect.bisect_right(functions, (address, 1 << 64)) - 1
            return functions[index] if index >= 0 and functions[index][0] <= address < functions[index][1] else None

        disassembler = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_LITTLE_ENDIAN)
        disassembler.skipdata = True
        registers = {}
        xrefs = []
        for instruction in disassembler.disasm(text.data(), text["sh_addr"]):
            operands = instruction.op_str.split(", ")
            if instruction.mnemonic == "adrp":
                registers[operands[0]] = int(operands[1][1:], 16)
            elif (instruction.mnemonic == "add" and len(operands) == 3
                  and operands[1] in registers and operands[2].startswith("#")):
                value = registers[operands[1]] + int(operands[2][1:], 0)
                registers[operands[0]] = value
                if 0x9e3b00 <= value <= 0x9e4200:
                    label = contents[value:contents.find(b"\0", value)].decode("utf8", "backslashreplace")
                    xrefs.append(f"{instruction.address:x} {value:x} {owner(instruction.address)} {label}")
            elif instruction.mnemonic in ("ret", "b"):
                registers.clear()
    (args.output / "uis-xrefs.txt").write_text("\n".join(xrefs) + "\n")
    (args.output / "functions.txt").write_text(
        "\n".join(f"{start:x} {end:x}" for start, end in functions) + "\n")

    wanted = {bkdr(name): name for name in PROPERTY_NAMES}
    registers = {}
    hashes = []
    for instruction in disassembler.disasm(contents[0x2c0000:0x2d9000], 0x2c0000):
        operands = instruction.op_str.split(", ")
        if instruction.mnemonic == "mov" and len(operands) == 2 and operands[1].startswith("#"):
            try:
                registers[operands[0]] = int(operands[1][1:], 0)
            except ValueError:
                pass
        elif instruction.mnemonic == "movk" and len(operands) >= 2:
            shift = int(operands[2].split("#")[1], 0) if len(operands) > 2 else 0
            registers[operands[0]] = (registers.get(operands[0], 0) & ~(65535 << shift)) | (
                int(operands[1][1:], 0) << shift)
        elif instruction.mnemonic == "cmp":
            for register in operands:
                if registers.get(register) in wanted:
                    hashes.append(f"{instruction.address:x}: {instruction.op_str} = {wanted[registers[register]]}")
        elif instruction.mnemonic in ("bl", "ret"):
            registers.clear()
    (args.output / "hash-xrefs.txt").write_text("\n".join(hashes) + "\n")
    print(f"APK SHA-256: {digest}\n分析结果: {args.output.resolve()}")


if __name__ == "__main__":
    main()
