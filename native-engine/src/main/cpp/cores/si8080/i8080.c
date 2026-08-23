#include "i8080.h"
#include <string.h>

/* shorthand aliases - direct member access, no aliasing tricks */
#define A (s->a)
#define B (s->b)
#define C (s->c)
#define D (s->d)
#define E (s->e)
#define H (s->h)
#define L (s->l)

/*
 * Register encoding used across opcodes:
 *   0=B 1=C 2=D 3=E 4=H 5=L 6=[HL] 7=A
 */
static inline uint16_t hl(const i8080_state *s) { return (uint16_t)((s->h << 8) | s->l); }
static inline void set_hl(i8080_state *s, uint16_t v) { s->h = (uint8_t)(v >> 8); s->l = (uint8_t)v; }
static inline uint16_t bc(const i8080_state *s) { return (uint16_t)((s->b << 8) | s->c); }
static inline uint16_t de(const i8080_state *s) { return (uint16_t)((s->d << 8) | s->e); }

static inline uint8_t rd(i8080_state *s, const i8080_hooks *hk, uint16_t a) {
    (void)hk;
    return s->mem[a];
}
static inline void wr(i8080_state *s, const i8080_hooks *hk, uint16_t a, uint8_t v) {
    s->mem[a] = v;
    if (hk && hk->mem_write) hk->mem_write(a, v);
}

static uint8_t reg_get(const i8080_state *s, uint8_t r) {
    switch (r) {
    case 0: return s->b;
    case 1: return s->c;
    case 2: return s->d;
    case 3: return s->e;
    case 4: return s->h;
    case 5: return s->l;
    default: return s->a;          /* 7 */
    }
}

static void reg_set(i8080_state *s, uint8_t r, uint8_t v) {
    switch (r) {
    case 0: s->b = v; break;
    case 1: s->c = v; break;
    case 2: s->d = v; break;
    case 3: s->e = v; break;
    case 4: s->h = v; break;
    case 5: s->l = v; break;
    default: s->a = v; break;      /* 7 */
    }
}

/* operand slot 0-5 registers, 6 memory at HL, 7 accumulator A */
static inline uint8_t opnd(i8080_state *s, const i8080_hooks *hk, uint8_t r) {
    return (r == 6) ? rd(s, hk, hl(s)) : reg_get(s, r);
}
static inline void opnd_store(i8080_state *s, const i8080_hooks *hk, uint8_t r, uint8_t v) {
    if (r == 6) wr(s, hk, hl(s), v);
    else reg_set(s, r, v);
}

static inline uint8_t fetch(i8080_state *s) { return s->mem[s->pc++]; }
static inline uint16_t fetch16(i8080_state *s) {
    uint16_t lo = fetch(s);
    uint16_t hi = fetch(s);
    return (uint16_t)(lo | (hi << 8));
}

static uint8_t parity(uint8_t v) {
    v ^= (uint8_t)(v >> 4);
    v ^= (uint8_t)(v >> 2);
    v ^= (uint8_t)(v >> 1);
    return (uint8_t)(~v & 1);
}

static void set_zsp(i8080_state *s, uint8_t r) {
    s->zf = (r == 0);
    s->sf = (r & 0x80) != 0;
    s->pf = parity(r);
}

static void push16(i8080_state *s, uint16_t v) {
    s->sp = (uint16_t)(s->sp - 1); s->mem[s->sp] = (uint8_t)(v >> 8);
    s->sp = (uint16_t)(s->sp - 1); s->mem[s->sp] = (uint8_t)v;
}

static uint16_t pop16(i8080_state *s) {
    uint16_t lo = s->mem[s->sp++];
    uint16_t hi = s->mem[s->sp++];
    return (uint16_t)(lo | (hi << 8));
}

static int cond_met(const i8080_state *s, uint8_t c) {
    static const uint8_t which[8] = { 0, 0, 1, 1, 2, 2, 3, 3 }; /* Z C P S */
    static const uint8_t want[8]   = { 0, 1, 0, 1, 0, 1, 0, 1 };
    uint8_t flag;
    switch (which[c]) {
    case 0: flag = s->zf; break;
    case 1: flag = s->cf; break;
    case 2: flag = s->pf; break;
    default: flag = s->sf; break;
    }
    return want[c] ? flag : !flag;
}

void i8080_init(i8080_state *s) {
    memset(s, 0, sizeof(*s));
}

uint16_t i8080_step(i8080_state *s, const i8080_hooks *hk) {
    if (s->halted) { s->cycles += 2; return 2; }

    uint8_t op = fetch(s);
    uint16_t addr, tmp16;
    uint8_t dst, src, v, res, cy, carry_in;
    long ret = 5;

    /* ---------- MOV family (0x40-0x7F, 0x76 = HLT) ---------- */
    if (op >= 0x40 && op <= 0x7F) {
        if (op == 0x76) { s->halted = 1; ret = 7; goto done; }
        src = op & 7;
        dst = (uint8_t)((op >> 3) & 7);
        v = opnd(s, hk, src);
        opnd_store(s, hk, dst, v);
        goto done;                                    /* MOV = 5 cycles */
    }

    /* ---------- ALU family (0x80-0xBF registers + immediate twins) ---------- */
    {
        const int imm = ((op & 0xC7) == 0xC6);
        if ((op & 0xC0) == 0x80 || imm) {
        uint8_t sel = (uint8_t)((op >> 3) & 7);
        v = imm ? fetch(s) : opnd(s, hk, op & 7);
        switch (sel) {
        case 0: /* ADD */
            tmp16 = (uint16_t)(A + v);
            s->cf = (tmp16 > 0xFF); s->hf = ((A & 0xF) + (v & 0xF)) > 0xF;
            A = (uint8_t)tmp16; set_zsp(s, A); break;
        case 1: /* ADC */
            carry_in = s->cf ? 1 : 0;
            tmp16 = (uint16_t)(A + v + carry_in);
            s->cf = (tmp16 > 0xFF); s->hf = ((A & 0xF) + (v & 0xF) + carry_in) > 0xF;
            A = (uint8_t)tmp16; set_zsp(s, A); break;
        case 2: /* SUB */
            res = (uint8_t)(A - v);
            s->cf = A < v; s->hf = (A & 0xF) < (v & 0xF);
            A = res; set_zsp(s, A); break;
        case 3: /* SBB */
            carry_in = s->cf ? 1 : 0;
            tmp16 = (uint16_t)((uint16_t)v + carry_in);
            res = (uint8_t)(A - tmp16);
            s->cf = (uint16_t)A < tmp16; s->hf = (A & 0xF) < (tmp16 & 0xF);
            A = res; set_zsp(s, A); break;
        case 4: /* ANA */
            s->hf = ((A | v) & 0x08) != 0;
            A &= v; s->cf = 0; set_zsp(s, A); break;
        case 5: /* XRA */
            A ^= v; s->cf = 0; s->hf = 0; set_zsp(s, A); break;
        case 6: /* ORA */
            A |= v; s->cf = 0; s->hf = 0; set_zsp(s, A); break;
        default: /* CMP */
            res = (uint8_t)(A - v);
            s->cf = A < v; s->hf = (A & 0xF) < (v & 0xF);
            set_zsp(s, res); break;
        }
        ret = imm ? 7 : 5;
        goto done;
        }
    }

    switch (op) {

    /* MVI r / MVI M */
    case 0x06: case 0x0E: case 0x16: case 0x1E:
    case 0x26: case 0x2E: case 0x36: case 0x3E:
        v = fetch(s);
        opnd_store(s, hk, (uint8_t)((op >> 3) & 7), v);
        ret = 7;
        break;

    /* LXI rp,d16 */
    case 0x01: case 0x11: case 0x21: case 0x31:
        tmp16 = fetch16(s);
        switch ((op >> 4) & 3) {
        case 0: B = (uint8_t)(tmp16 >> 8); C = (uint8_t)tmp16; break;
        case 1: D = (uint8_t)(tmp16 >> 8); E = (uint8_t)tmp16; break;
        case 2: set_hl(s, tmp16); break;
        default: s->sp = tmp16; break;
        }
        ret = 10;
        break;

    /* INX / DCX rp */
    case 0x03: case 0x13: case 0x23: case 0x33:
    case 0x0B: case 0x1B: case 0x2B: case 0x3B: {
        int inc = (op & 1) != 0;
        int delta = inc ? 1 : -1;
        switch ((op >> 4) & 3) {
        case 0: tmp16 = (uint16_t)(bc(s) + delta); B = (uint8_t)(tmp16 >> 8); C = (uint8_t)tmp16; break;
        case 1: tmp16 = (uint16_t)(de(s) + delta); D = (uint8_t)(tmp16 >> 8); E = (uint8_t)tmp16; break;
        case 2: set_hl(s, (uint16_t)(hl(s) + delta)); break;
        default: s->sp = (uint16_t)(s->sp + delta); break;
        }
        ret = 5;
        break;
    }

    /* INR / DCR r */
    case 0x04: case 0x0C: case 0x14: case 0x1C:
    case 0x24: case 0x2C: case 0x34: case 0x3C:
    case 0x05: case 0x0D: case 0x15: case 0x1D:
    case 0x25: case 0x2D: case 0x35: case 0x3D: {
        dst = (uint8_t)((op >> 3) & 7);
        int is_inc = (op & 1) == 0;   /* even opcodes = INR, odd = DCR */
        uint8_t old = opnd(s, hk, dst);
        if (is_inc) {
            res = (uint8_t)(old + 1);
            s->hf = (old & 0xF) == 0xF;
        } else {
            res = (uint8_t)(old - 1);
            s->hf = (old & 0xF) == 0;
        }
        opnd_store(s, hk, dst, res);
        set_zsp(s, res);
        break;
    }

    /* DAD rp */
    case 0x09: case 0x19: case 0x29: case 0x39: {
        uint16_t w = (op == 0x09) ? bc(s) : (op == 0x19) ? de(s)
                   : (op == 0x29) ? hl(s) : s->sp;
        uint32_t r32 = (uint32_t)hl(s) + w;
        set_hl(s, (uint16_t)r32);
        s->cf = (uint8_t)((r32 >> 16) & 1);
        ret = 10;
        break;
    }

    /* STAX / LDAX */
    case 0x02: wr(s, hk, bc(s), A); break;
    case 0x12: wr(s, hk, de(s), A); break;
    case 0x0A: A = rd(s, hk, bc(s)); break;
    case 0x1A: A = rd(s, hk, de(s)); break;

    /* LHLD / SHLD / XCHG / XTHL / SPHL / PCHL */
    case 0x2A: addr = fetch16(s); L = s->mem[addr]; H = s->mem[(uint16_t)(addr + 1)]; ret = 16; break;
    case 0x22: addr = fetch16(s); s->mem[addr] = L; s->mem[(uint16_t)(addr + 1)] = H; ret = 16; break;
    case 0xEB: { uint8_t th = D, te = E; D = H; E = L; H = th; E = te; } break;
    case 0xE3: {
        uint8_t lo = s->mem[s->sp], hi = s->mem[(uint16_t)(s->sp + 1)];
        s->mem[s->sp] = L; s->mem[(uint16_t)(s->sp + 1)] = H;
        L = lo; H = hi; ret = 18;
        break;
    }
    case 0xF9: s->sp = hl(s); ret = 5; break;
    case 0xE9: s->pc = hl(s); break;

    /* rotates / accumulators */
    case 0x07: cy = (uint8_t)(A & 0x80); A = (uint8_t)((A << 1) | (cy ? 1 : 0)); s->cf = (uint8_t)(cy != 0); break;
    case 0x0F: cy = (uint8_t)(A & 1);   A = (uint8_t)((A >> 1) | (cy ? 0x80 : 0)); s->cf = (uint8_t)(cy != 0); break;
    case 0x17: cy = (uint8_t)(A & 0x80); A = (uint8_t)((A << 1) | (s->cf ? 1 : 0)); s->cf = (uint8_t)(cy != 0); break;
    case 0x1F: cy = (uint8_t)(A & 1);   A = (uint8_t)((A >> 1) | (s->cf ? 0x80 : 0)); s->cf = (uint8_t)(cy != 0); break;
    case 0x2F: A = (uint8_t)(~A); break;
    case 0x3F: s->cf = s->cf ? 0 : 1; break;
    case 0x37: s->cf = 1; break;
    case 0x27: { /* DAA */
        uint8_t corr = 0;
        int new_cy = s->cf;
        if ((A & 0xF) > 9 || s->hf) corr += 6;
        if ((A >> 4) > 9 || s->cf) { corr = (uint8_t)(corr + 0x60); new_cy = 1; }
        uint16_t r16 = (uint16_t)(A + corr);
        A = (uint8_t)r16;
        s->cf = (uint8_t)new_cy;
        set_zsp(s, A);
        break;
    }

    /* stack */
    case 0xC1: case 0xD1: case 0xE1: case 0xF1:
        tmp16 = pop16(s);
        switch ((op >> 4) & 3) {
        case 0: B = (uint8_t)(tmp16 >> 8); C = (uint8_t)tmp16; break;
        case 1: D = (uint8_t)(tmp16 >> 8); E = (uint8_t)tmp16; break;
        case 2: set_hl(s, tmp16); break;
        default:
            A = (uint8_t)(tmp16 >> 8);
            v = (uint8_t)tmp16;
            s->cf = v & 1;        s->pf = (uint8_t)((v >> 2) & 1);
            s->hf = (uint8_t)((v >> 4) & 1);
            s->zf = (uint8_t)((v >> 6) & 1);
            s->sf = (uint8_t)((v >> 7) & 1);
            break;
        }
        ret = 10;
        break;
    case 0xC5: case 0xD5: case 0xE5: case 0xF5: {
        uint16_t w;
        switch ((op >> 4) & 3) {
        case 0: w = bc(s); break;
        case 1: w = de(s); break;
        case 2: w = hl(s); break;
        default: {
            uint8_t psw = (uint8_t)(2 | (s->cf ? 1 : 0) | (s->pf ? 4 : 0)
                                   | (s->hf ? 0x10 : 0) | (s->zf ? 0x40 : 0)
                                   | (s->sf ? 0x80 : 0));
            w = (uint16_t)((A << 8) | psw);
            break;
        }
        }
        push16(s, w);
        ret = 11;
        break;
    }

    /* jumps / calls / returns / RST */
    case 0xC3: case 0xC2: case 0xCA: case 0xD2: case 0xDA:
    case 0xE2: case 0xEA: case 0xF2: case 0xFA:
        addr = fetch16(s);
        if (op == 0xC3 || cond_met(s, (uint8_t)((op >> 3) & 7))) { s->pc = addr; ret = 10; }
        else ret = 7;
        break;
    case 0xCD: case 0xC4: case 0xCC: case 0xD4: case 0xDC:
    case 0xE4: case 0xEC: case 0xF4: case 0xFC:
        addr = fetch16(s);
        if (op == 0xCD || cond_met(s, (uint8_t)((op >> 3) & 7))) {
            push16(s, s->pc);
            s->pc = addr;
            ret = 17;
        } else ret = 11;
        break;
    case 0xC9: s->pc = pop16(s); ret = 10; break;
    case 0xC0: case 0xC8: case 0xD0: case 0xD8:
    case 0xE0: case 0xE8: case 0xF0: case 0xF8:
        if (cond_met(s, (uint8_t)((op >> 3) & 7))) { s->pc = pop16(s); ret = 11; }
        else ret = 5;
        break;
    case 0xC7: case 0xCF: case 0xD7: case 0xDF:
    case 0xE7: case 0xEF: case 0xF7: case 0xFF:
        push16(s, s->pc);
        s->pc = (uint16_t)(op - 0xC7);
        ret = 11;
        break;

    /* interrupts / halt / io */
    case 0xFB: s->iff = 1; break;
    case 0xF3: s->iff = 0; break;
    case 0x76: s->halted = 1; ret = 7; break;
    case 0xDB: { uint8_t p = fetch(s); A = (hk && hk->port_in) ? hk->port_in(p) : 0; ret = 10; break; }
    case 0xD3: { uint8_t p = fetch(s); if (hk && hk->port_out) hk->port_out(p, A); ret = 10; break; }

    default: /* NOP + undocumented no-ops */
        break;
    }

done:
    s->cycles += ret;
    return (uint16_t)ret;
}
