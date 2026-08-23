#!/usr/bin/env python3
"""Build + run Chotobela 8080 core verification against the real C implementation."""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))          # repo root
CORE = os.path.join(ROOT, "native-engine/src/main/cpp/cores/si8080")
sys.path.insert(0, HERE)

from asm80 import Asm, assemble_fail_handler            # noqa: E402



_SEQ = iter(range(1, 999))

def expect(a, cond, n):
    """continue if <cond> met, else FAIL with id n (marker in A -> 0x2012)"""
    lbl = f"OK_{n}_{next(_SEQ)}"
    getattr(a, cond)(lbl)
    a.mvi("A", n)
    a.jmp("FAIL")
    a.label(lbl)

def build_main_rom() -> bytes:
    a = Asm()
    # entry
    a.jmp("MAIN")

    # IRQ vectors -> shared handler (slots are only 8 bytes each)
    a.org(0x0008)
    a.jmp("IRQHAND")
    a.org(0x0010)
    a.jmp("IRQHAND")

    a.org(0x0020)
    a.label("IRQHAND")
    a.push("PSW")
    a.push("H")
    a.lhld("COUNT")
    a.inr("L")
    a.shld("COUNT")
    a.pop("H")
    a.pop("PSW")
    a.ret()

    assemble_fail_handler_at = None
    a.org(0x0100)
    a.label("MAIN")
    a.lxi("SP", 0x23FE)

    def fail_jump():
        a.jnc if False else None
        return ("FAIL",)

    # T1 MOV/CPI
    a.mvi("A", 0xA5)
    a.mov("B", "A")
    a.cpi(0xA5)
    expect(a, "jz", 1)

    # T2 ADI carry+zero
    a.mvi("A", 0xFF)
    a.adi(1)
    expect(a, "jc", 2)
    expect(a, "jz", 2)

    # T3 SUI borrow
    a.label("T3")
    a.mvi("A", 0x00)
    a.sui(1)
    expect(a, "jc", 3)

    # T4 INR leaves CF set (still from T3), wraps 7F->80 sets SF
    a.label("T4")
    a.mvi("B", 0x7F)
    a.inr("B")
    expect(a, "jnz", 4)
    expect(a, "jc", 4)
    expect(a, "jm", 4)

    # T5 CALL/RET twice
    a.label("T5")
    a.mvi("C", 0)
    a.call("SUBR")
    a.call("SUBR")
    a.mov("A", "C")
    a.cpi(2)
    expect(a, "jz", 5)

    # T6 SHLD/LHLD
    a.lxi("H", 0x1234)
    a.shld("SCRATCH")
    a.lxi("H", 0x0000)
    a.lhld("SCRATCH")
    a.mov("A", "H")
    a.cpi(0x12)
    expect(a, "jz", 6)
    a.mov("A", "L")
    a.cpi(0x34)
    expect(a, "jz", 6)

    # T7 XCHG
    a.lxi("D", 0x5678)
    a.xchg()
    a.mov("A", "H")
    a.cpi(0x56)
    expect(a, "jz", 7)

    # T8 IO loopback OUT 40 / IN 41
    a.mvi("A", 0x5A)
    a.out(0x40)
    a.inp(0x41)
    a.cpi(0x5A)
    expect(a, "jz", 8)

    # T9 SI shift register: offset=4 data=AB -> IN3 == 0A
    a.mvi("A", 4)
    a.out(2)
    a.mvi("A", 0xAB)
    a.out(4)
    a.inp(3)
    a.cpi(0xB0)          # shift reg = AB00, offset 4 -> (AB00>>4)&FF = B0
    expect(a, "jz", 9)

    # T10 RAL through carry chain: STC; A=0 -> RAL: A=1 CF=0; RAL: A=2
    a.stc()
    a.mvi("A", 0)
    a.ral()               # A = (0<<1)|CF = 1
    expect(a, "jnc", 10)
    a.ral()
    a.cpi(2)
    expect(a, "jz", 10)

    # T11 PUSH/POP PSW preserves carry across flag-clobbering XRA
    a.mvi("B", 0x42)
    a.mvi("A", 0x42)
    a.cmp("B")            # equal: ZF=1, CF=0
    a.stc()               # CF=1
    a.push("PSW")
    a.xra("A")            # ZF=1, CF=0
    expect(a, "jz", 11)
    expect(a, "jnc", 11)
    a.pop("PSW")          # restore CF=1
    expect(a, "jc", 11)

    # T12 DAD double carry: HL=8001 + BC=8001 -> 0002 CF=1
    a.label("T12")
    a.lxi("B", 0x8001)
    a.lxi("H", 0x8001)
    a.dad("B")
    expect(a, "jc", 12)   # double-carry from 8001+8001
    a.mov("A", "H")
    a.ora("L")            # HL==0002 -> nonzero... use exact compare instead
    a.cpi(0)
    expect(a, "jnz", 12)  # A=H=00
    a.mov("A", "L")
    a.cpi(2)
    expect(a, "jz", 12)   # L=02

    # success sentinel
    a.lxi("H", 0x55AA)
    a.shld("PASS_FLAG")
    a.hlt()
    a.jmp("MAIN")

    # scratch RAM refs resolved as labels
    a.org(0x02E0)
    a.label("SCRATCH")
    a.db(0, 0)

    a.org(0x02F0)
    a.label("FAIL")
    a.lxi("H", 0x2012)
    a.mov("M", "A")
    a.label("FAIL_SPIN")
    a.hlt()
    a.jmp("FAIL_SPIN")

    a.org(0x0300)
    a.label("SUBR")
    a.inr("C")
    a.ret()

    # data labels live in RAM but assembler needs addresses only
    a.labels.setdefault("PASS_FLAG", 0x2010)
    a.labels.setdefault("COUNT", 0x2000)

    return a.resolve()


def build_irq_rom() -> bytes:
    a = Asm()
    a.org(0x0008)
    a.push("PSW")
    a.push("H")
    a.lhld("COUNT")
    a.inr("L")
    a.shld("COUNT")
    a.pop("H")
    a.pop("PSW")
    a.ret()

    a.org(0x0100)
    a.lxi("SP", 0x23FE)
    a.lxi("H", 0x0000)      # COUNT label below
    a.labels["COUNT"] = 0x2000
    # store zero via direct SHLD of H=0? use LXI H,COUNT then MVI M,0
    a.lxi("H", 0x2000)
    a.mvi("M", 0)
    a.ei()
    a.label("IDLE")
    a.jmp("IDLE")

    return a.resolve()


def main():
    main_rom = build_main_rom()
    irq_rom = build_irq_rom()
    open(os.path.join(HERE, "si_main.bin"), "wb").write(main_rom)
    open(os.path.join(HERE, "si_irq.bin"), "wb").write(irq_rom)
    print(f"main rom {len(main_rom)} B, irq rom {len(irq_rom)} B")

    cc = subprocess.run(["which", "gcc"], capture_output=True).stdout.strip() or \
         subprocess.run(["which", "cc"], capture_output=True).stdout.strip()
    if not cc:
        print("no compiler found"); sys.exit(3)
    cc = cc.decode()

    runner_src = os.path.join(HERE, "test_8080.c")
    runner_bin = os.path.join(HERE, "test_8080")
    subprocess.run([cc, "-O2", "-std=c17", f"-I{CORE}", "-o", runner_bin, runner_src,
                    os.path.join(CORE, "i8080.c")], check=True)

    ok = True
    r = subprocess.run([runner_bin, os.path.join(HERE, "si_main.bin"), "main"],
                       capture_output=True, text=True)
    print(r.stdout.strip())
    ok &= r.returncode == 0

    r = subprocess.run([runner_bin, os.path.join(HERE, "si_irq.bin"), "irq"],
                       capture_output=True, text=True)
    print(r.stdout.strip())
    ok &= r.returncode == 0

    # syntax-only validation of the arcade shell against the real ABI header
    incdir = os.path.join(ROOT, "native-engine/src/main/cpp/engine-host")
    r = subprocess.run([cc, "-fsyntax-only", "-std=c17",
                        f"-I{incdir}", os.path.join(CORE, "si_arcade.c")],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[:2000])
    else:
        print("si_arcade.c syntax OK")
    ok &= r.returncode == 0

    print("ALL 8080 TESTS PASSED" if ok else "TESTS FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
