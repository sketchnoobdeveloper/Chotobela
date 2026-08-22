"""CHIP-8 simulator mirroring native-engine/cores/chip8/chip8.c exactly.

Mirrored semantics (do not diverge from the C core):
- 11 cycles per frame; DT/ST decrement once per frame after cycles
- FX0A edge-detect wait using persistent prev_keys, stalls without PC advance
- DXYN: coords wrapped mod 64/32, clipped at edges, VF collision
- 8XY6/8XYE shift VX by itself (quirk); 8XY5/7 standard borrow
- FX55/FX65 increment I by X+1 (VIP quirk)
- BNNN: pc = nnn + v0 ; all pc increments masked & 0xFFF
"""
import random

MEM = 4096
PROG = 0x200
W, H = 64, 32
FONT = bytes([
    0xF0, 0x90, 0xF0, 0x90, 0x90,
    0x20, 0x60, 0x20, 0x20, 0x70,
    0xF0, 0x10, 0xF0, 0x80, 0xF0,
    0xF0, 0x10, 0xF0, 0x10, 0xF0,
    0x90, 0x90, 0xF0, 0x10, 0x10,
    0xF0, 0x80, 0xF0, 0x10, 0xF0,
    0xF0, 0x80, 0xF0, 0x90, 0xF0,
    0xF0, 0x10, 0x20, 0x40, 0x40,
    0xF0, 0x90, 0xF0, 0x90, 0xF0,
    0xF0, 0x90, 0xF0, 0x10, 0xF0,
    0xF0, 0x90, 0xF0, 0x90, 0x90,
    0xE0, 0x90, 0xE0, 0x90, 0xE0,
    0xF0, 0x80, 0x80, 0x80, 0xF0,
    0xE0, 0x90, 0x90, 0x90, 0xE0,
    0xF0, 0x80, 0xF0, 0x80, 0xF0,
    0xF0, 0x80, 0xF0, 0x80, 0x80,
])
CYCLES_PER_FRAME = 11


class Chip8Error(Exception):
    pass


class Chip8:
    def __init__(self):
        self.mem = bytearray(MEM)
        self.mem[0x50:0x50 + len(FONT)] = FONT
        self.v = [0] * 16
        self.i = 0
        self.pc = PROG
        self.stack = []
        self.dt = self.st = 0
        self.keypad = 0
        self.display = bytearray(W * H)
        self.waiting_key = None      # None idle, else target register index
        self._prev_wait_keys = 0     # mirrors C static prev_keys
        self.loaded_len = 0
        self.max_stack = 0
        self.frames_run = 0

    def load_rom(self, data: bytes):
        assert len(data) <= MEM - PROG, "ROM too large"
        self.reset()
        self.mem[PROG:PROG + len(data)] = data
        self.loaded_len = len(data)

    def reset(self):
        self.v = [0] * 16
        self.i = 0
        self.pc = PROG
        self.stack = []
        self.dt = self.st = 0
        self.display = bytearray(W * H)
        self.waiting_key = None
        self.max_stack = 0
        self.frames_run = 0

    # ---- helpers ----
    @property
    def pixels_on(self):
        return sum(self.display)

    def _fetch(self):
        a = self.pc & 0xFFF
        return (self.mem[a] << 8) | self.mem[(a + 1) & 0xFFF]

    def _draw(self, x, y, n):
        vx = self.v[x] % W
        vy = self.v[y] % H
        self.v[0xF] = 0
        for row in range(n):
            if vy + row >= H:
                break
            bits = self.mem[(self.i + row) & 0xFFF]
            for col in range(8):
                if not (bits & (0x80 >> col)):
                    continue
                px = vx + col
                if px >= W:
                    continue
                idx = (vy + row) * W + px
                if self.display[idx]:
                    self.v[0xF] = 1
                self.display[idx] ^= 1

    def _cycle(self):
        s = self
        if s.waiting_key is not None:
            now = s.keypad
            newly = now & ~s._prev_wait_keys
            s._prev_wait_keys = now
            if newly:
                for k in range(16):
                    if newly & (1 << k):
                        s.v[s.waiting_key] = k
                        break
                s.waiting_key = None
            else:
                return  # stall without advancing PC

        op = s._fetch()
        s.pc = (s.pc + 2) & 0xFFF
        x = (op >> 8) & 0xF
        y = (op >> 4) & 0xF
        n = op & 0xF
        nn = op & 0xFF
        nnn = op & 0xFFF

        hi = op >> 12
        if hi == 0x0:
            if op == 0x00E0:
                s.display = bytearray(W * H)
            elif op == 0x00EE:
                if not s.stack:
                    raise Chip8Error("stack underflow")
                s.pc = s.stack.pop()
            elif op != 0:
                pass  # 0NNN ignored
        elif hi == 0x1:
            s.pc = nnn
        elif hi == 0x2:
            if len(s.stack) >= 16:
                raise Chip8Error("stack overflow")
            s.stack.append(s.pc)
            s.max_stack = max(s.max_stack, len(s.stack))
            s.pc = nnn
        elif hi == 0x3:
            if s.v[x] == nn:
                s.pc += 2
        elif hi == 0x4:
            if s.v[x] != nn:
                s.pc += 2
        elif hi == 0x5:
            if (op & 0xF) == 0 and s.v[x] == s.v[y]:
                s.pc += 2
        elif hi == 0x6:
            s.v[x] = nn
        elif hi == 0x7:
            s.v[x] = (s.v[x] + nn) & 0xFF
        elif hi == 0x8:
            vx = s.v[x]
            vy = s.v[y]
            sub = n
            if sub == 0x0:
                s.v[x] = vy
            elif sub == 0x1:
                s.v[x] |= vy
            elif sub == 0x2:
                s.v[x] &= vy
            elif sub == 0x3:
                s.v[x] ^= vy
            elif sub == 0x4:
                t = vx + vy
                s.v[x] = t & 0xFF
                s.v[0xF] = 1 if t > 0xFF else 0
            elif sub == 0x5:
                s.v[0xF] = 1 if vx >= vy else 0
                s.v[x] = (vx - vy) & 0xFF
            elif sub == 0x6:
                s.v[0xF] = vx & 1
                s.v[x] = vx >> 1
            elif sub == 0x7:
                s.v[0xF] = 1 if vy >= vx else 0
                s.v[x] = (vy - vx) & 0xFF
            elif sub == 0xE:
                s.v[0xF] = (vx >> 7) & 1
                s.v[x] = (vx << 1) & 0xFF
            else:
                raise Chip8Error(f"illegal 8XY{sub:X}")
        elif hi == 0x9:
            if (op & 0xF) == 0 and s.v[x] != s.v[y]:
                s.pc += 2
        elif hi == 0xA:
            s.i = nnn
        elif hi == 0xB:
            s.pc = (nnn + s.v[0]) & 0xFFFF
        elif hi == 0xC:
            s.v[x] = random.getrandbits(8) & nn
        elif hi == 0xD:
            s._draw(x, y, n)
        elif hi == 0xE:
            key = s.v[x] & 0xF
            if nn == 0x9E:
                if s.keypad & (1 << key):
                    s.pc += 2
            elif nn == 0xA1:
                if not (s.keypad & (1 << key)):
                    s.pc += 2
            else:
                raise Chip8Error(f"illegal EX{nn:X}")
        elif hi == 0xF:
            if nn == 0x07:
                s.v[x] = s.dt
            elif nn == 0x15:
                s.dt = s.v[x]
            elif nn == 0x18:
                s.st = s.v[x]
            elif nn == 0x1E:
                s.i = (s.i + s.v[x]) & 0xFFF
            elif nn == 0x0A:
                s.waiting_key = x
            elif nn == 0x29:
                s.i = 0x50 + (s.v[x] & 0xF) * 5
            elif nn == 0x33:
                val = s.v[x]
                s.mem[s.i & 0xFFF] = val // 100
                s.mem[(s.i + 1) & 0xFFF] = (val // 10) % 10
                s.mem[(s.i + 2) & 0xFFF] = val % 10
            elif nn == 0x55:
                for r in range(x + 1):
                    s.mem[(s.i + r) & 0xFFF] = s.v[r]
                s.i = (s.i + x + 1) & 0xFFF
            elif nn == 0x65:
                for r in range(x + 1):
                    s.v[r] = s.mem[(s.i + r) & 0xFFF]
                s.i = (s.i + x + 1) & 0xFFF
            else:
                raise Chip8Error(f"illegal FX{nn:X}")

    def step_frame(self):
        for _ in range(CYCLES_PER_FRAME):
            self._cycle()
        if self.dt > 0:
            self.dt -= 1
        if self.st > 0:
            self.st -= 1
        self.frames_run += 1
        # sanity: pc escaped loaded region and is executing zeroed memory
        if not (PROG <= self.pc < PROG + max(self.loaded_len, 64)):
            raise Chip8Error(f"pc runaway {self.pc:#x}")

    def run(self, frames, input_script=None, seed=42):
        """input_script: callable(frame_index) -> set of pressed key ids"""
        random.seed(seed)
        history = []
        for f in range(frames):
            if input_script:
                keys = input_script(f)
                mask = 0
                for k in keys or []:
                    mask |= 1 << k
                self.keypad = mask
            self.step_frame()
            if f % 30 == 0 or f == frames - 1:
                history.append((f, self.pixels_on))
        return history
