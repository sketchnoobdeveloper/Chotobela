/*
 * Chotobela - Renderer backend hooks
 *
 * The primary render path is a GLES3 pipeline driven from Kotlin
 * (see feature/player). These hooks expose shader-preset selection and
 * scaling parameters to native for future GPU-side post-processing.
 */
#ifndef CHOTOBELA_RENDERER_BACKEND_H
#define CHOTOBELA_RENDERER_BACKEND_H

typedef enum {
    CB_SHADER_NONE = 0,
    CB_SHADER_CRT,
    CB_SHADER_SCANLINES,
    CB_SHADER_LCD
} cb_shader_preset;

void cb_renderer_set_preset(cb_shader_preset preset);
cb_shader_preset cb_renderer_get_preset(void);
void cb_renderer_set_integer_scaling(int enabled);

#endif
