"""Chotobela CHIP-8 mini-assembler.

Two clean passes:
  1) walk instructions, assign addresses, collect labels
  2) re-walk and emit via encode_final() with complete symbol table

Supports: labels, org, db, classic CHIP-8 mnemonics. Emits raw .ch8 at 0x200.
"""
import re
import sys

REG = {f"V{i:X}": i for i in range(16)}

def split_lines(src):
    lines = []
    for raw in src.splitlines():
        line = raw.split(";")[0].strip()
        if not line:
            continue
        m = re.match(r"^(\w+):(.*)$", line)
        if m:
            lines.append((m.group(1), "LABEL", [m.group(2).strip()]))
        else:
            parts = line.split(None, 1)
            op = parts[0].upper()
            args = [a.strip() for a in parts[1].split(",")] if len(parts) > 1 else []
            lines.append((None, op, args))
    return lines

def r(x):
    x = x.upper()
    assert x in REG, f"not a register: {x}"
    return REG[x]

def num(x):
    return int(x.strip(), 0)

class Assembler:
    def __init__(self, src):
        self.lines = split_lines(src)
        self.syms = {}
        self.base = 0x200

    def _value(self, expr):
        expr = expr.strip()
        if expr in self.syms:
            return self.syms[expr]
        return num(expr)

    def pass1(self):
        addr = self.base
        for label, op, args in self.lines:
            if op == "LABEL":
                assert label not in self.syms, f"duplicate label {label}"
                self.syms[label] = addr
                continue
            if op == "ORG":
                addr = self._value(args[0]) if args else addr
                continue
            if op == "DB":
                addr += len(args)
            else:
                addr += 2

    def pass2(self):
        out = bytearray()
        cursor = self.base

        def pad_to(target):
            nonlocal cursor
            pad = target - cursor
            assert pad >= 0 and pad % 2 == 0, f"bad ORG/pad {cursor:#x}->{target:#x}"
            out += b"\x00" * pad
            cursor = target

        for _, op, args in self.lines:
            if op == "LABEL":
                continue
            if op == "ORG":
                pad_to(self._value(args[0]))
                continue
            if op == "DB":
                for a in args:
                    out.append(self._value(a) & 0xFF)
                    cursor += 1
            else:
                code = self.encode(op, args)
                out += code.to_bytes(2, "big")
                cursor += 2
        return bytes(out)

    # ---- instruction encoding ----
    def encode(self, op, A):
        v = self._value

        if op == "CLS":  return 0x00E0
        if op == "RET":  return 0x00EE
        if op == "SYS":  return 0x0000 | (v(A[0]) & 0xFFF)
        if op == "JP":
            if len(A) == 2 and A[0].upper() == "V0":
                return 0xB000 | (v(A[1]) & 0xFFF)
            return 0x1000 | (v(A[0]) & 0xFFF)
        if op == "CALL": return 0x2000 | (v(A[0]) & 0xFFF)

        if op == "SE":
            if A[1].upper() in REG:
                return 0x5000 | (r(A[0]) << 8) | (r(A[1]) << 4)
            return 0x3000 | (r(A[0]) << 8) | (v(A[1]) & 0xFF)
        if op == "SNE":
            if A[1].upper() in REG:
                return 0x9000 | (r(A[0]) << 8) | (r(A[1]) << 4)
            return 0x4000 | (r(A[0]) << 8) | (v(A[1]) & 0xFF)

        if op == "LD":
            dst, src = A[0], A[1]
            ds, ss = dst.upper(), src.upper()
            if ds == "I":
                assert ss not in ("DT", "ST"), "LD I,DT/ST is not valid CHIP-8"
                return 0xA000 | (v(src) & 0xFFF)
            if ds == "DT":   return 0xF015 | (r(src) << 8)
            if ds == "ST":   return 0xF018 | (r(src) << 8)
            if ss == "DT":   return 0xF007 | (r(dst) << 8)
            if ss == "K":    return 0xF00A | (r(dst) << 8)
            if ss == "FONT": return 0xF029 | (r(dst) << 8)
            if ss in REG:    return 0x8000 | (r(dst) << 8) | (r(src) << 4)
            return 0x6000 | (r(dst) << 8) | (v(src) & 0xFF)

        if op == "ADD":
            dst, src = A[0], A[1]
            if dst.upper() == "I":
                return 0xF01E | (r(src) << 8)
            if src.upper() in REG:
                return 0x8004 | (r(dst) << 8) | (r(src) << 4)
            return 0x7000 | (r(dst) << 8) | (v(src) & 0xFF)

        if op == "SUB":  return 0x8005 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "SUBN": return 0x8007 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "OR":   return 0x8001 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "AND":  return 0x8002 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "XOR":  return 0x8003 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "SHR":  return 0x8006 | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "SHL":  return 0x800E | (r(A[0]) << 8) | (r(A[1]) << 4)
        if op == "RND":  return 0xC000 | (r(A[0]) << 8) | (v(A[1]) & 0xFF)
        if op == "DRW":  return 0xD000 | (r(A[0]) << 8) | (r(A[1]) << 4) | (num(A[2]) & 0xF)
        if op == "SKP":  return 0xE09E | (r(A[0]) << 8)
        if op == "SKNP": return 0xE0A1 | (r(A[0]) << 8)
        if op == "BCD":  return 0xF033 | (r(A[0]) << 8)
        if op == "STORE": return 0xF055 | (r(A[0]) << 8)
        if op == "LOAD":  return 0xF065 | (r(A[0]) << 8)
        if op == "FONT":  return 0xF029 | (r(A[0]) << 8)
        raise ValueError(f"unknown mnemonic {op}")

def assemble(src):
    a = Assembler(src)
    a.pass1()
    return a.pass2()

def assemble_with_symbols(src):
    a = Assembler(src)
    a.pass1()
    return a.pass2(), dict(a.syms)

if __name__ == "__main__":
    src_path, out_path = sys.argv[1], sys.argv[2]
    with open(src_path) as f:
        rom = assemble(f.read())
    with open(out_path, "wb") as f:
        f.write(rom)
    print(f"{src_path} -> {out_path} ({len(rom)} bytes)")
