"""Minimal two-pass Intel 8080 assembler for Chotobela core verification."""

REG = {"B": 0, "C": 1, "D": 2, "E": 3, "H": 4, "L": 5, "M": 6, "A": 7}
RP = {"B": 0, "D": 1, "H": 2, "SP": 3, "PSW": 3}

COND = {"NZ": 0, "Z": 1, "NC": 2, "C": 3, "PO": 4, "PE": 5, "P": 6, "M": 7}


class Asm:
    def __init__(self, base: int = 0x0000):
        self.base = base
        self.buf = bytearray()
        self.labels = {}
        self.fix = []          # (buf_pos, label)

    @property
    def pc(self):
        return self.base + len(self.buf)

    def org(self, addr):
        pad = addr - self.pc
        assert pad >= 0, f"org backwards {hex(self.pc)}->{hex(addr)}"
        self.buf.extend(b"\x00" * pad)

    def label(self, name):
        assert name not in self.labels
        self.labels[name] = self.pc

    def _e(self, *bs):
        self.buf.extend(bs)

    def _rel(self, label):
        pos = len(self.buf)
        self._e(0, 0)
        self.fix.append((pos, label))

    def resolve(self) -> bytes:
        out = bytearray(self.buf)
        base = self.org
        for pos, label in self.fix:
            v = self.labels[label]
            out[pos] = v & 0xFF
            out[pos + 1] = (v >> 8) & 0xFF
        return bytes(out)

    # ---- data ----
    def db(self, *vs):
        self._e(*[v & 0xFF for v in vs])

    # ---- data movement ----
    def mov(self, d, s):
        self._e(0x40 | (REG[d] << 3) | REG[s])

    def mvi(self, r, v):
        self._e(0x06 | (REG[r] << 3), v)

    def lxi(self, rp, v):
        self._e(0x01 | (RP[rp] << 4), v & 0xFF, (v >> 8) & 0xFF)

    def stax(self, rp):
        self._e(0x02 | (RP[rp] << 4))

    def ldax(self, rp):
        self._e(0x0A | (RP[rp] << 4))

    def shld(self, label):
        self._e(0x22); self._rel(label)

    def lhld(self, label):
        self._e(0x2A); self._rel(label)

    def xchg(self): self._e(0xEB)
    def xthl(self): self._e(0xE3)
    def sphl(self): self._e(0xF9)
    def pchl(self): self._e(0xE9)

    # ---- ALU register/immediate ----
    def _alu_reg(self, base, r):
        self._e(base | REG[r])

    def add(self, r): self._alu_reg(0x80, r)
    def adc(self, r): self._alu_reg(0x88, r)
    def sub(self, r): self._alu_reg(0x90, r)
    def sbb(self, r): self._alu_reg(0x98, r)
    def ana(self, r): self._alu_reg(0xA0, r)
    def xra(self, r): self._alu_reg(0xA8, r)
    def ora(self, r): self._alu_reg(0xB0, r)
    def cmp(self, r): self._alu_reg(0xB8, r)

    def adi(self, v): self._e(0xC6, v)
    def aci(self, v): self._e(0xCE, v)
    def sui(self, v): self._e(0xD6, v)
    def sbi(self, v): self._e(0xDE, v)
    def ani(self, v): self._e(0xE6, v)
    def xri(self, v): self._e(0xEE, v)
    def ori(self, v): self._e(0xF6, v)
    def cpi(self, v): self._e(0xFE, v)

    # ---- inc/dec ----
    def inr(self, r): self._e(0x04 | (REG[r] << 3))
    def dcr(self, r): self._e(0x05 | (REG[r] << 3))
    def inx(self, rp): self._e(0x03 | (RP[rp] << 4))
    def dcx(self, rp): self._e(0x0B | (RP[rp] << 4))
    def dad(self, rp): self._e(0x09 | (RP[rp] << 4))

    # ---- rotates / flags ----
    def rlc(self): self._e(0x07)
    def rrc(self): self._e(0x0F)
    def ral(self): self._e(0x17)
    def rar(self): self._e(0x1F)
    def cma(self): self._e(0x2F)
    def cmc(self): self._e(0x3F)
    def stc(self): self._e(0x37)
    def daa(self): self._e(0x27)

    # ---- stack ----
    def push(self, rp): self._e(0xC5 | (RP[rp] << 4)); 
    def pop(self, rp): self._e(0xC1 | (RP[rp] << 4))

    # ---- control flow ----
    def _j(self, opcode_base, label):
        self._e(opcode_base); self._rel(label)

    def jmp(self, l): self._j(0xC3, l)
    def jnz(self, l): self._j(0xC2, l)
    def jz(self, l): self._j(0xCA, l)
    def jnc(self, l): self._j(0xD2, l)
    def jc(self, l): self._j(0xDA, l)
    def jpo(self, l): self._j(0xE2, l)
    def jpe(self, l): self._j(0xEA, l)
    def jp(self, l): self._j(0xF2, l)
    def jm(self, l): self._j(0xFA, l)

    def call(self, l): self._j(0xCD, l)
    def cnz(self, l): self._j(0xC4, l)
    def cz(self, l): self._j(0xCC, l)
    def cnc(self, l): self._j(0xD4, l)
    def cc(self, l): self._j(0xDC, l)
    def ret(self): self._e(0xC9)
    def rnz(self): self._e(0xC0)
    def rz(self): self._e(0xC8)
    def rnc(self): self._e(0xD0)
    def rc(self): self._e(0xD8)
    def rst(self, n): self._e(0xC7 | (n << 3))

    # ---- misc ----
    def ei(self): self._e(0xFB)
    def di(self): self._e(0xF3)
    def hlt(self): self._e(0x76)
    def nop(self): self._e(0x00)
    def out(self, p): self._e(0xD3, p)
    def inp(self, p): self._e(0xDB, p)


def assemble_fail_handler(a: Asm):
    """Standard failure trap: mark 0x2011=EE then spin."""
    a.label("FAIL")
    a.mvi("A", 0xEE)
    a.lxi("H", 0x2011)
    a.mov("M", "A")
    a.label("FAIL_SPIN")
    a.hlt()
    a.jmp("FAIL_SPIN")
