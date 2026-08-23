/*
 * Chotobela - Intel 8080 CPU core
 * Clean-room interpreter written from the published ISA specification.
 * Implements the full documented instruction set including INTR/INT vectoring,
 * used by the arcade hardware shell (si_arcade.c).
 */
#ifndef CHOTOBELA_I8080_H
#define CHOTOBELA_I8080_H

#include <stdint.h>
#include <stddef.h>

#define I8080_MEM_SIZE 0x10000

typedef struct {
    uint8_t  mem[I8080_MEM_SIZE];
    uint8_t  a, b, c, d, e, h, l;
    uint16_t sp, pc;
    /* flag latches */
    uint8_t  cf, zf, sf, pf, hf;   /* carry, zero, sign, parity, aux-carry */
    int      iff;                  /* interrupt flip-flop */
    int      halted;
    long     cycles;               /* total executed */
} i8080_state;

/* Machine hooks provided by the hardware shell */
typedef struct {
    uint8_t (*port_in)(uint8_t port);              /* IN  */
    void    (*port_out)(uint8_t port, uint8_t v);  /* OUT */
    void    (*mem_write)(uint16_t addr, uint8_t v);/* VRAM etc.; NULL = plain RAM */
} i8080_hooks;

void     i8080_init(i8080_state *s);
uint16_t i8080_step(i8080_state *s, const i8080_hooks *hk); /* returns cycles used */

#endif
