/*
 * Chotobela - Input state aggregation
 */
#ifndef CHOTOBELA_INPUT_STATE_H
#define CHOTOBELA_INPUT_STATE_H

#include <stdint.h>

typedef struct {
    volatile uint32_t buttons;
    volatile float axis_x;
    volatile float axis_y;
} cb_input_state;

extern cb_input_state g_cb_input;

void cb_input_reset(void);
uint32_t cb_input_buttons(void);

#endif
