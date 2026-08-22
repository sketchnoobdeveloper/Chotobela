/*
 * Chotobela - Input state aggregation
 *
 * Kotlin-side sources (touch overlay, gamepads, keyboard) write through JNI
 * into the host's button bitmask. This module adds analog axis state and
 * key-repeat suppression for future use.
 */
#include "input_state.h"

cb_input_state g_cb_input = { 0, 0.0f, 0.0f };

void cb_input_reset(void) {
    g_cb_input.buttons = 0;
    g_cb_input.axis_x = 0.0f;
    g_cb_input.axis_y = 0.0f;
}

uint32_t cb_input_buttons(void) { return g_cb_input.buttons; }
