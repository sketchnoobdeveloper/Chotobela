/*
 * Chotobela - 8080 Arcade Hardware Shell
 *
 * Implements the classic fixed-shooter board widely documented in emulator
 * literature: 8KB ROM @ 0x0000, 1KB RAM @ 0x2000, 1bpp column-major VRAM
 * @ 0x2400, hardware byte shift register on ports 2/4/3, two frame IRQs
 * (RST 08 mid-frame, RST 10 end-of-frame), and discrete-style sound latches
 * on ports 3/5 rendered as synthesized blips.
 */
#include "../../engine-host/core_abi.h"
#include "i8080.h"
#include <string.h>
#include <stdlib.h>

#define SI_ROM_SIZE   8192
#define SI_RAM_BASE   0x2000
#define SI_RAM_END    0x2400          /* exclusive */
#define SI_VRAM_BASE  0x2400
#define SI_VRAM_SIZE  7168            /* 0x2400-0x3FFF */
#define SI_W 224
#define SI_H 256

/* engine button bitmask -> port1 bits */
#define BTN_LEFT   (1u << 0)
#define BTN_RIGHT  (1u << 1)
#define BTN_FIRE   (1u << 2)
#define BTN_START  (1u << 3)
#define BTN_COIN   (1u << 4)

typedef struct { float freq; int frames_left; } si_blip;
#define MAX_BLIPS 8

typedef struct {
    i8080_state  cpu;
    uint8_t      rom[SI_ROM_SIZE];
    /* shift register */
    uint16_t     shift_reg;
    uint8_t      shift_offset;
    /* input latch */
    uint32_t     buttons;
    uint8_t      coin_pulse;       /* frames remaining for coin signal */
    /* sound */
    si_blip      blips[MAX_BLIPS];
    float        beep_phase;
    /* irq sequencing within a frame */
    int          irq_phase;        /* 0 none, 1 sent rst08, 2 sent rst10 */
    long         frame_cycles;
    int          loaded;
} si_ctx;

static si_ctx g;

/* palette: white ship/alien rows with red/green tint bands like the strip overlay */
static inline uint32_t shade(uint16_t y) {
    if (y < 32)  return 0xFF21E6B0u;   /* green UFO band   (ARGB) */
    if (y < 64)  return 0xFFFFFFFFu;
    if (y >= 184 && y < 240) return 0xFF3DBDFFu;
    return 0xFFEDEDF2u;
}

/* ---------------- hooks ---------------- */

static uint8_t si_port_in(uint8_t port) {
    switch (port) {
    case 0: return 0x00;
    case 1: {
        uint8_t v = 0;
        if (g.buttons & BTN_COIN)  v |= 0x01;
        if (g.buttons & BTN_START) v |= 0x04;
        if (g.buttons & BTN_FIRE)  v |= 0x10;
        if (g.buttons & BTN_LEFT)  v |= 0x20;
        if (g.buttons & BTN_RIGHT) v |= 0x40;
        return v;
    }
    case 2: return 0x00;              /* dips: default lives/extra */
    case 3: return (uint8_t)(g.shift_reg >> (8 - g.shift_offset));
    default: return 0;
    }
}

static void si_sound(int code_bit_index, int bank);

static void si_port_out(uint8_t port, uint8_t v) {
    switch (port) {
    case 2: g.shift_offset = (uint8_t)(v & 7); break;
    case 3: si_sound(0, v); break;
    case 4: g.shift_reg = (uint16_t)((g.shift_reg >> 8) | ((uint16_t)v << 8)); break;
    case 5: si_sound(8, v); break;
    case 6: break;                    /* watchdog */
    default: break;
    }
}

static void si_mem_write(uint16_t addr, uint8_t v) {
    (void)v; (void)addr;              /* VRAM dirtiness handled per-frame scan */
}

/* map classic effect bits to synthesized blips */
static void si_trigger(float freq, int frames) {
    for (int i = 0; i < MAX_BLIPS; i++) {
        if (g.blips[i].frames_left <= 0) {
            g.blips[i].freq = freq;
            g.blips[i].frames_left = frames;
            return;
        }
    }
}
static void si_sound(int base, int mask) {
    static const float f[16] = {
        120.f, 260.f, 520.f, 90.f,     /* bank 3: walk ufo hit etc */
        700.f, 140.f, 60.f, 1100.f,
        180.f, 90.f, 320.f, 480.f,     /* bank 5: shot/explode/inflate */
        220.f, 150.f, 800.f, 1000.f
    };
    for (int b = 0; b < 8; b++) {
        if (mask & (1 << b)) {
            int idx = base + b;
            si_trigger(f[idx], 4);
        }
    }
}

/* ---------------- core api ---------------- */

static int si_init(void) {
    memset(&g, 0, sizeof(g));
    i8080_init(&g.cpu);
    return 0;
}
static void si_deinit(void) { g.loaded = 0; }

static int si_load_rom(const uint8_t *data, size_t size) {
    if (size == 0 || size > SI_ROM_SIZE) return -1;
    memset(&g, 0, sizeof(g));
    i8080_init(&g.cpu);
    memcpy(g.rom, data, size);
    memcpy(g.cpu.mem, g.rom, size);
    /* convenience: one credit inserted at boot */
    g.coin_pulse = 30;
    g.loaded = 1;
    return 0;
}

static void si_reset(void) {
    memset(g.cpu.mem, 0, sizeof(g.cpu.mem));
    memcpy(g.cpu.mem, g.rom, SI_ROM_SIZE);
    i8080_init(&g.cpu);
    g.shift_reg = 0; g.shift_offset = 0;
    g.coin_pulse = 30;
    g.irq_phase = 0;
    g.frame_cycles = 0;
}

/* inject interrupt vector if enabled */
static int si_step_frame(void) {
    if (!g.loaded) return -1;

    const long CYCLES_FRAME = 33333;   /* ~2MHz / 60 */
    const i8080_hooks hooks = {
        .port_in = si_port_in,
        .port_out = si_port_out,
        .mem_write = si_mem_write
    };
    long target_mid = CYCLES_FRAME / 2;
    long spent = 0;

    if (g.coin_pulse > 0) g.coin_pulse--;

    g.irq_phase = 0;
    while (spent < CYCLES_FRAME) {
        if (!g.cpu.halted) {
            spent += i8080_step(&g.cpu, &hooks);
        } else {
            spent += 2;
        }

        /* mid-frame vblank-start IRQ */
        if (g.irq_phase == 0 && spent >= target_mid) {
            g.irq_phase = 1;
            if (g.cpu.iff && !g.cpu.halted) {
                uint16_t pc = g.cpu.pc;
                g.cpu.sp = (uint16_t)(g.cpu.sp - 1); g.cpu.mem[g.cpu.sp] = (uint8_t)(pc >> 8);
                g.cpu.sp = (uint16_t)(g.cpu.sp - 1); g.cpu.mem[g.cpu.sp] = (uint8_t)pc;
                g.cpu.pc = 0x0008;
            }
        }
    }

    /* end-of-frame IRQ (RST 10) */
    if (g.cpu.iff && !g.cpu.halted) {
        uint16_t pc = g.cpu.pc;
        g.cpu.sp = (uint16_t)(g.cpu.sp - 1); g.cpu.mem[g.cpu.sp] = (uint8_t)(pc >> 8);
        g.cpu.sp = (uint16_t)(g.cpu.sp - 1); g.cpu.mem[g.cpu.sp] = (uint8_t)pc;
        g.cpu.pc = 0x0010;
    }

    /* decay blips once per frame */
    for (int i = 0; i < MAX_BLIPS; i++) {
        if (g.blips[i].frames_left > 0) g.blips[i].frames_left--;
    }
    return 0;
}

static const uint32_t *si_video(int *w, int *h) {
    static uint32_t fb[SI_W * SI_H];
    /* VRAM is column-major: byte = 8 vertical pixels; screen viewed rotated */
    for (int col = 0; col < 28; col++) {          /* 28 bytes = 224 px width */
        for (int row = 0; row < 256; row++) {
            uint8_t bits = g.cpu.mem[SI_VRAM_BASE + row * 32 + col];
            for (int k = 0; k < 8; k++) {
                int sx = col * 8 + k;             /* bit k -> x within column */
                int sy = 255 - row;               /* flip vertical */
                if (sx < SI_W) fb[sy * SI_W + sx] =
                    (bits & (1u << k)) ? shade(sy) : 0xFF101010u;
            }
        }
    }
    *w = SI_W;
    *h = SI_H;
    return fb;
}

static float si_aspect(void) { return (float)SI_W / (float)SI_H; }

static int si_audio_render(int16_t *out, int max_frames) {
    int active = -1;
    for (int i = 0; i < MAX_BLIPS; i++) {
        if (g.blips[i].frames_left > 0) { active = i; break; }
    }
    if (active < 0) {
        memset(out, 0, (size_t)max_frames * 4);
        return max_frames;
    }
    float freq = g.blips[active].freq;
    float step = 2.f * 3.14159265f * freq / (float)48000;
    for (int fr = 0; fr < max_frames; fr++) {
        g.beep_phase += step;
        if (g.beep_phase > 6.28318531f) g.beep_phase -= 6.28318531f;
        int16_t smp = (g.beep_phase < 3.14159265f) ? 8500 : -8500;
        out[fr * 2] = smp;
        out[fr * 2 + 1] = smp;
    }
    return max_frames;
}

static int si_sample_rate(void) { return 48000; }

static void si_buttons(uint32_t mask) { g.buttons = mask; }

/* ---- save states ---- */
static long si_state_size(void) {
    return (long)sizeof(si_ctx);
}
static long si_state_save(uint8_t *buf, size_t cap) {
    if (cap < sizeof(si_ctx)) return -1;
    memcpy(buf, &g, sizeof(si_ctx));
    return (long)sizeof(si_ctx);
}
static int si_state_load(const uint8_t *buf, size_t size) {
    if (size != sizeof(si_ctx)) return -1;
    memcpy(&g, buf, sizeof(si_ctx));
    return 0;
}

const cb_core_api cb_core_si8080 = {
    .id = "si8080",
    .init = si_init,
    .deinit = si_deinit,
    .load_rom = si_load_rom,
    .reset = si_reset,
    .step_frame = si_step_frame,
    .video_buffer = si_video,
    .video_aspect = si_aspect,
    .audio_render = si_audio_render,
    .target_sample_rate = si_sample_rate,
    .set_buttons = si_buttons,
    .state_save = si_state_save,
    .state_load = si_state_load,
    .state_size = si_state_size
};
