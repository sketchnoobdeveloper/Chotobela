#include "chip8.h"
#include <string.h>
#include <stdlib.h>

#define CHIP8_MEM_SIZE     4096
#define CHIP8_PROG_START   0x200
#define CHIP8_STACK_DEPTH  16
#define CHIP8_DISP_W       64
#define CHIP8_DISP_H       32
#define CHIP8_CYCLES_FRAME 11      /* ~660 Hz at 60 fps */
#define CHIP8_AUDIO_RATE   48000
#define CHIP8_BEEP_HZ      440.0f
#define CHIP8_STATE_MAGIC  0x43423853u /* "CB8S" */

static const uint8_t FONT_SET[80] = {
    0xF0,0x90,0xF0,0x90,0x90, /* 0 */
    0x20,0x60,0x20,0x20,0x70, /* 1 */
    0xF0,0x10,0xF0,0x80,0xF0, /* 2 */
    0xF0,0x10,0xF0,0x10,0xF0, /* 3 */
    0x90,0x90,0xF0,0x10,0x10, /* 4 */
    0xF0,0x80,0xF0,0x10,0xF0, /* 5 */
    0xF0,0x80,0xF0,0x90,0xF0, /* 6 */
    0xF0,0x10,0x20,0x40,0x40, /* 7 */
    0xF0,0x90,0xF0,0x90,0xF0, /* 8 */
    0xF0,0x90,0xF0,0x10,0xF0, /* 9 */
    0xF0,0x90,0xF0,0x90,0x90, /* A */
    0xE0,0x90,0xE0,0x90,0xE0, /* B */
    0xF0,0x80,0x80,0x80,0xF0, /* C */
    0xE0,0x90,0x90,0x90,0xE0, /* D */
    0xF0,0x80,0xF0,0x80,0xF0, /* E */
    0xF0,0x80,0xF0,0x80,0x80  /* F */
};

typedef struct {
    uint8_t  mem[CHIP8_MEM_SIZE];
    uint8_t  v[16];
    uint16_t i;
    uint16_t pc;
    uint16_t stack[CHIP8_STACK_DEPTH];
    uint8_t  sp;
    uint8_t  dt;               /* delay timer */
    uint8_t  st;               /* sound timer */
    uint32_t keypad;           /* bit n = key n pressed */
    uint8_t  display[CHIP8_DISP_W * CHIP8_DISP_H]; /* logical 1bpp */
    uint8_t  waiting_key;      /* FX0A state: -1 idle, else target reg */
} chip8_state;

/* Palette: warm amber phosphor on near-black (matches Chotobela brand) */
#define COL_BG  0xFF16130Eu
#define COL_ON  0xFFFFB74Du          /* ARGB amber */

typedef struct {
    chip8_state s;
    uint32_t fb[CHIP8_DISP_W * CHIP8_DISP_H];
    float beep_phase;
    int loaded;
} chip8_ctx;

static chip8_ctx g_ctx;

static void fb_rebuild(void) {
    for (int p = 0; p < CHIP8_DISP_W * CHIP8_DISP_H; p++) {
        g_ctx.fb[p] = g_ctx.s.display[p] ? COL_ON : COL_BG;
    }
}

static void chip8_reset(void) {
    memset(&g_ctx.s, 0, sizeof(chip8_state));
    g_ctx.s.pc = CHIP8_PROG_START;
    g_ctx.s.waiting_key = 0xFF;
    memcpy(g_ctx.s.mem + 0x50, FONT_SET, sizeof(FONT_SET));
}

static int chip8_init(void) {
    memset(&g_ctx, 0, sizeof(chip8_ctx));
    chip8_reset();
    return 0;
}

static void chip8_deinit(void) { g_ctx.loaded = 0; }

static int chip8_load_rom(const uint8_t *data, size_t size) {
    if (size == 0) return -1;
    if (CHIP8_PROG_START + size > CHIP8_MEM_SIZE) return -2;
    chip8_reset();
    memcpy(g_ctx.s.mem + CHIP8_PROG_START, data, size);
    g_ctx.loaded = 1;
    fb_rebuild();
    return 0;
}

static inline uint16_t fetch(uint16_t addr) {
    return (uint16_t)((g_ctx.s.mem[addr & 0xFFF] << 8) |
                       g_ctx.s.mem[(addr + 1) & 0xFFF]);
}

static void op_draw(uint8_t x, uint8_t y, uint8_t n) {
    uint8_t vx = g_ctx.s.v[x] % CHIP8_DISP_W;
    uint8_t vy = g_ctx.s.v[y] % CHIP8_DISP_H;
    g_ctx.s.v[0xF] = 0;

    for (int row = 0; row < n && (vy + row) < CHIP8_DISP_H; row++) {
        uint8_t bits = g_ctx.s.mem[(g_ctx.s.i + row) & 0xFFF];
        for (int col = 0; col < 8; col++) {
            if (!(bits & (0x80u >> col))) continue;
            int px = vx + col;
            if (px >= CHIP8_DISP_W) continue;
            int idx = (vy + row) * CHIP8_DISP_W + px;
            if (g_ctx.s.display[idx]) g_ctx.s.v[0xF] = 1;
            g_ctx.s.display[idx] ^= 1;
        }
    }
    fb_rebuild();
}

static int chip8_execute_cycle(void) {
    chip8_state *s = &g_ctx.s;
    if (s->waiting_key != 0xFF) {
        /* FX0A: block until a key press edge is observed */
        static uint32_t prev_keys = 0;
        uint32_t now = s->keypad;
        uint32_t newly = now & ~prev_keys;
        prev_keys = now;
        if (newly) {
            for (int k = 0; k < 16; k++) {
                if (newly & (1u << k)) {
                    s->v[s->waiting_key] = (uint8_t)k;
                    break;
                }
            }
            s->waiting_key = 0xFF;
        } else {
            return 0; /* stall without advancing PC */
        }
    }

    uint16_t op = fetch(s->pc);
    s->pc = (uint16_t)((s->pc + 2) & 0xFFF);

    uint8_t  x  = (uint8_t)((op >> 8) & 0xF);
    uint8_t  y  = (uint8_t)((op >> 4) & 0xF);
    uint8_t  n  = (uint8_t)(op & 0xF);
    uint8_t  nn = (uint8_t)(op & 0xFF);
    uint16_t nnn = op & 0xFFF;

    switch (op >> 12) {
    case 0x0:
        if (op == 0x00E0) {
            memset(s->display, 0, sizeof(s->display));
            fb_rebuild();
        } else if (op == 0x00EE) {
            if (s->sp == 0) return -1;
            s->pc = s->stack[--s->sp];
        }
        break;
    case 0x1: s->pc = nnn; break;
    case 0x2:
        if (s->sp >= CHIP8_STACK_DEPTH) return -2;
        s->stack[s->sp++] = s->pc;
        s->pc = nnn;
        break;
    case 0x3: if (s->v[x] == nn) s->pc += 2; break;
    case 0x4: if (s->v[x] != nn) s->pc += 2; break;
    case 0x5: if ((op & 0xF) == 0 && s->v[x] == s->v[y]) s->pc += 2; break;
    case 0x6: s->v[x] = nn; break;
    case 0x7: s->v[x] = (uint8_t)(s->v[x] + nn); break;
    case 0x8: {
        uint8_t *vx = &s->v[x], vy = s->v[y];
        uint16_t sum;
        switch (n) {
        case 0x0: *vx = vy; break;
        case 0x1: *vx |= vy; break;
        case 0x2: *vx &= vy; break;
        case 0x3: *vx ^= vy; break;
        case 0x4: sum = (uint16_t)(*vx + vy); *vx = (uint8_t)sum; s->v[0xF] = sum > 0xFF; break;
        case 0x5: s->v[0xF] = *vx >= vy; *vx = (uint8_t)(*vx - vy); break;
        case 0x6: s->v[0xF] = *vx & 1; *vx >>= 1; break;
        case 0x7: s->v[0xF] = vy >= *vx; *vx = (uint8_t)(vy - *vx); break;
        case 0xE: s->v[0xF] = (*vx >> 7) & 1; *vx <<= 1; break;
        default: return -3;
        }
        break;
    }
    case 0x9: if ((op & 0xF) == 0 && s->v[x] != s->v[y]) s->pc += 2; break;
    case 0xA: s->i = nnn; break;
    case 0xB: s->pc = (uint16_t)(nnn + s->v[0]); break;
    case 0xC: s->v[x] = (uint8_t)(((rand() << 8) ^ rand()) & nn); break;
    case 0xD: op_draw(x, y, n); break;
    case 0xE:
        if (nn == 0x9E)      { if (s->keypad & (1u << (s->v[x] & 0xF))) s->pc += 2; }
        else if (nn == 0xA1) { if (!(s->keypad & (1u << (s->v[x] & 0xF)))) s->pc += 2; }
        break;
    case 0xF:
        switch (nn) {
        case 0x07: s->v[x] = s->dt; break;
        case 0x15: s->dt = s->v[x]; break;
        case 0x18: s->st = s->v[x]; break;
        case 0x1E: s->i = (uint16_t)((s->i + s->v[x]) & 0xFFF); break;
        case 0x0A: s->waiting_key = x; break;
        case 0x29: s->i = (uint16_t)(0x50 + (s->v[x] & 0xF) * 5); break;
        case 0x33: {
            uint8_t val = s->v[x];
            s->mem[s->i & 0xFFF]         = val / 100;
            s->mem[(s->i + 1) & 0xFFF]   = (val / 10) % 10;
            s->mem[(s->i + 2) & 0xFFF]   = val % 10;
            break;
        }
        case 0x55:
            for (int r = 0; r <= x; r++) s->mem[(s->i + r) & 0xFFF] = s->v[r];
            s->i = (uint16_t)((s->i + x + 1) & 0xFFF);   /* VIP quirk */
            break;
        case 0x65:
            for (int r = 0; r <= x; r++) s->v[r] = s->mem[(s->i + r) & 0xFFF];
            s->i = (uint16_t)((s->i + x + 1) & 0xFFF);   /* VIP quirk */
            break;
        default: return -4;
        }
        break;
    default: return -5;
    }
    return 0;
}

static int chip8_step_frame(void) {
    if (!g_ctx.loaded) return -1;
    for (int c = 0; c < CHIP8_CYCLES_FRAME; c++) {
        int rc = chip8_execute_cycle();
        if (rc != 0) return rc;
    }
    if (g_ctx.s.dt > 0) g_ctx.s.dt--;
    if (g_ctx.s.st > 0) g_ctx.s.st--;
    return 0;
}

static const uint32_t *chip8_video(int *w, int *h) {
    *w = CHIP8_DISP_W;
    *h = CHIP8_DISP_H;
    return g_ctx.fb;
}

static float chip8_aspect(void) { return (float)CHIP8_DISP_W / (float)CHIP8_DISP_H; }

static int chip8_audio_render(int16_t *out, int max_frames) {
    if (g_ctx.s.st > 0) {
        float step = 2.0f * 3.14159265f * CHIP8_BEEP_HZ / (float)CHIP8_AUDIO_RATE;
        for (int f = 0; f < max_frames; f++) {
            g_ctx.beep_phase += step;
            if (g_ctx.beep_phase > 2.0f * 3.14159265f) g_ctx.beep_phase -= 2.0f * 3.14159265f;
            int16_t sample = (g_ctx.beep_phase < 3.14159265f) ? 9000 : -9000;
            out[f * 2] = sample;
            out[f * 2 + 1] = sample;
        }
    } else {
        memset(out, 0, (size_t)max_frames * 4);
    }
    return max_frames;
}

static int chip8_sample_rate(void) { return CHIP8_AUDIO_RATE; }

static void chip8_buttons(uint32_t mask) { g_ctx.s.keypad = mask; }

/* ---- State serialization ----
 * Layout: magic(4) version(1) regs+mem+display fixed blob. Display stored as
 * the 1bpp logical buffer so saves stay compact (2048 bytes total ~2KB). */
static long chip8_state_size(void) {
    return 5 + sizeof(chip8_state);
}

static long chip8_state_save(uint8_t *buf, size_t cap) {
    long need = chip8_state_size();
    if ((long)cap < need) return -1;
    uint32_t magic = CHIP8_STATE_MAGIC;
    memcpy(buf, &magic, 4);
    buf[4] = 1;
    memcpy(buf + 5, &g_ctx.s, sizeof(chip8_state));
    return need;
}

static int chip8_state_load(const uint8_t *buf, size_t size) {
    if (size < 5 || (size_t)size != (size_t)chip8_state_size()) return -1;
    uint32_t magic;
    memcpy(&magic, buf, 4);
    if (magic != CHIP8_STATE_MAGIC || buf[4] != 1) return -2;
    memcpy(&g_ctx.s, buf + 5, sizeof(chip8_state));
    g_ctx.loaded = 1;
    fb_rebuild();
    return 0;
}

const cb_core_api cb_core_chip8 = {
    .id = "chip8",
    .init = chip8_init,
    .deinit = chip8_deinit,
    .load_rom = chip8_load_rom,
    .reset = chip8_reset,
    .step_frame = chip8_step_frame,
    .video_buffer = chip8_video,
    .video_aspect = chip8_aspect,
    .audio_render = chip8_audio_render,
    .target_sample_rate = chip8_sample_rate,
    .set_buttons = chip8_buttons,
    .state_save = chip8_state_save,
    .state_load = chip8_state_load,
    .state_size = chip8_state_size
};
