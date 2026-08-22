#include "renderer_backend.h"

static cb_shader_preset g_preset = CB_SHADER_NONE;
static int g_integer_scaling = 0;

void cb_renderer_set_preset(cb_shader_preset preset) { g_preset = preset; }
cb_shader_preset cb_renderer_get_preset(void) { return g_preset; }
void cb_renderer_set_integer_scaling(int enabled) { g_integer_scaling = enabled; }
