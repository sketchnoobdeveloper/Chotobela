/*
 * Native verification runner for the Chotobela 8080 core.
 * Loads an assembled test image, executes it through the real i8080.c,
 * provides SI-style ports (shift register) plus IO loopback for tests.
 */
#include "i8080.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

static uint8_t g_latch = 0;
static uint16_t g_shift = 0;
static uint8_t g_shift_off = 0;

static uint8_t hook_in(uint8_t port) {
    switch (port) {
    case 0x41: return g_latch;                                   /* loopback */
    case 0x03: return (uint8_t)(g_shift >> (8 - g_shift_off));   /* SI shift */
    default: return 0;
    }
}

static void hook_out(uint8_t port, uint8_t v) {
    switch (port) {
    case 0x40: g_latch = v; break;
    case 0x02: g_shift_off = (uint8_t)(v & 7); break;
    case 0x04: g_shift = (uint16_t)((g_shift >> 8) | ((uint16_t)v << 8)); break;
    default: break;
    }
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: %s rom.bin main|irq\n", argv[0]); return 2; }

    FILE *f = fopen(argv[1], "rb");
    if (!f) { perror("rom"); return 2; }
    static uint8_t rom[8192];
    size_t n = fread(rom, 1, sizeof(rom), f);
    fclose(f);

    i8080_state cpu;
    i8080_init(&cpu);
    const i8080_hooks hooks = { .port_in = hook_in, .port_out = hook_out, .mem_write = NULL };
    memcpy(cpu.mem, rom, n);
    cpu.sp = 0x23FE;
    cpu.pc = (memcmp(argv[2], "irq", 3) == 0) ? 0x0100 : 0x0100;

    int mode_irq = (memcmp(argv[2], "irq", 3) == 0);
    long steps = 0, max_steps = 60000000L;
    int injections = 0;

    while (!cpu.halted && steps < max_steps) {
        i8080_step(&cpu, &hooks);
        steps++;
        if (mode_irq && injections < 5 && (steps % 3000 == 0)) {
            if (cpu.iff && !cpu.halted) {
                cpu.sp--; cpu.mem[cpu.sp] = (uint8_t)(cpu.pc >> 8);
                cpu.sp--; cpu.mem[cpu.sp] = (uint8_t)cpu.pc;
                cpu.pc = 0x0008;
                injections++;
            }
        }
    }

    if (mode_irq) {
        uint8_t count = cpu.mem[0x2000];
        printf("irq count=%u injections=%d\n", count, injections);
        if (count >= 5) { printf("IRQ PASS\n"); return 0; }
        printf("IRQ FAIL\n"); return 1;
    } else {
        uint8_t lo = cpu.mem[0x2010], hi = cpu.mem[0x2011];
        uint8_t failflag = cpu.mem[0x2011];
        printf("pass=%02X%02X fail=%02X marker=%02X pc=%04X steps=%ld halted=%d\n",
               hi, lo, failflag, cpu.mem[0x2012], cpu.pc, steps, cpu.halted);
        if (hi == 0x55 && lo == 0xAA && failflag != 0xEE) { printf("MAIN PASS\n"); return 0; }
        printf("MAIN FAIL\n"); return 1;
    }
}
