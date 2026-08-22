#include "engine_host.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>

/* ---- Core registry ----
 * Cores self-register via constructor-priority registration in their TU.
 * We use explicit registration calls here to keep linking simple and static. */
extern const cb_core_api cb_core_chip8;

static const cb_core_api *g_registry[CB_MAX_CORES];
static int g_registry_count = 0;

static void register_all(void) {
    if (g_registry_count > 0) return;
    g_registry[g_registry_count++] = &cb_core_chip8;
}

const cb_core_api *cb_find_core(const char *id) {
    register_all();
    for (int i = 0; i < g_registry_count; i++) {
        if (strcmp(g_registry[i]->id, id) == 0) return g_registry[i];
    }
    return NULL;
}

int cb_core_count(void) {
    register_all();
    return g_registry_count;
}

/* ---- Host state ---- */
typedef struct {
    cb_session_state state;
    const cb_core_api *core;
    char saves_dir[512];
    char rom_name[256];      /* basename without extension */
    char slot_path[768];
} cb_host;

static cb_host g_host = { CB_SESSION_IDLE, NULL, {0}, {0}, {0} };

int cb_host_init(const char *saves_dir) {
    if (!saves_dir || strlen(saves_dir) >= sizeof(g_host.saves_dir)) return -1;
    memset(&g_host, 0, sizeof(g_host));
    g_host.state = CB_SESSION_IDLE;
    snprintf(g_host.saves_dir, sizeof(g_host.saves_dir), "%s", saves_dir);
    /* best-effort mkdir; app guarantees existence */
    mkdir(g_host.saves_dir, 0755);
    return 0;
}

void cb_host_deinit(void) {
    if (g_host.core && g_host.state != CB_SESSION_IDLE) {
        g_host.core->deinit();
    }
    g_host.core = NULL;
    g_host.state = CB_SESSION_IDLE;
}

static void basename_no_ext(const char *path, char *out, size_t cap) {
    const char *base = strrchr(path, '/');
    base = base ? base + 1 : path;
    const char *dot = strrchr(base, '.');
    size_t len = dot ? (size_t)(dot - base) : strlen(base);
    if (len >= cap) len = cap - 1;
    memcpy(out, base, len);
    out[len] = '\0';
}

int cb_load_rom(const char *core_id, const char *rom_path) {
    const cb_core_api *core = cb_find_core(core_id);
    if (!core || !rom_path) return -1;

    FILE *f = fopen(rom_path, "rb");
    if (!f) return -2;

    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0 || size > (64L * 1024 * 1024)) { fclose(f); return -3; }

    uint8_t *data = malloc((size_t)size);
    if (!data) { fclose(f); return -4; }
    if (fread(data, 1, (size_t)size, f) != (size_t)size) {
        free(data); fclose(f); return -5;
    }
    fclose(f);

    if (g_host.core && g_host.state != CB_SESSION_IDLE) g_host.core->deinit();

    if (core->init() != 0) { free(data); return -6; }
    int rc = core->load_rom(data, (size_t)size);
    free(data);
    if (rc != 0) { core->deinit(); return -7; }

    g_host.core = core;
    basename_no_ext(rom_path, g_host.rom_name, sizeof(g_host.rom_name));
    g_host.state = CB_SESSION_READY;
    return 0;
}

void cb_host_reset(void) {
    if (g_host.core && g_host.state != CB_SESSION_IDLE) {
        g_host.core->reset();
        g_host.state = CB_SESSION_READY;
    }
}

int cb_step_frame(void) {
    if (g_host.state != CB_SESSION_READY && g_host.state != CB_SESSION_RUNNING) return -1;
    int rc = g_host.core->step_frame();
    if (rc == 0) g_host.state = CB_SESSION_RUNNING;
    else g_host.state = CB_SESSION_ERROR;
    return rc;
}

const uint32_t *cb_video_buffer(int *out_w, int *out_h) {
    if (!g_host.core) { *out_w = 0; *out_h = 0; return NULL; }
    return g_host.core->video_buffer(out_w, out_h);
}

float cb_video_aspect(void) {
    return g_host.core ? g_host.core->video_aspect() : 1.0f;
}

int cb_audio_render(int16_t *out, int max_frames) {
    if (!g_host.core) { memset(out, 0, (size_t)max_frames * 4); return max_frames; }
    return g_host.core->audio_render(out, max_frames);
}

int cb_target_sample_rate(void) {
    return g_host.core ? g_host.core->target_sample_rate() : 48000;
}

void cb_set_buttons(uint32_t mask) {
    if (g_host.core) g_host.core->set_buttons(mask);
}

/* ---- Save states ---- */
static void build_slot_path(int slot) {
    snprintf(g_host.slot_path, sizeof(g_host.slot_path), "%s/%s_%s.slot%d.state",
             g_host.saves_dir, g_host.core ? g_host.core->id : "unknown",
             g_host.rom_name, slot);
}

int cb_save_state(int slot) {
    if (!g_host.core || g_host.state == CB_SESSION_IDLE) return -1;
    build_slot_path(slot);

    long cap = g_host.core->state_size();
    if (cap <= 0) return -2;
    uint8_t *buf = malloc((size_t)cap);
    if (!buf) return -3;

    long written = g_host.core->state_save(buf, (size_t)cap);
    if (written <= 0) { free(buf); return -4; }

    char tmp_path[800];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", g_host.slot_path);
    FILE *f = fopen(tmp_path, "wb");
    if (!f) { free(buf); return -5; }
    size_t wn = fwrite(buf, 1, (size_t)written, f);
    fclose(f);
    free(buf);
    if ((long)wn != written) { remove(tmp_path); return -6; }

    /* atomic replace */
    remove(g_host.slot_path);
    return rename(tmp_path, g_host.slot_path) == 0 ? 0 : -7;
}

int cb_load_state(int slot) {
    if (!g_host.core || g_host.state == CB_SESSION_IDLE) return -1;
    build_slot_path(slot);

    FILE *f = fopen(g_host.slot_path, "rb");
    if (!f) return -2;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) { fclose(f); return -3; }

    uint8_t *buf = malloc((size_t)size);
    if (!buf) { fclose(f); return -4; }
    if (fread(buf, 1, (size_t)size, f) != (size_t)size) {
        free(buf); fclose(f); return -5;
    }
    fclose(f);

    int rc = g_host.core->state_load(buf, (size_t)size);
    free(buf);
    return rc;
}

long cb_state_slot_size(int slot) {
    if (!g_host.core) return -1;
    build_slot_path(slot);
    struct stat st;
    return stat(g_host.slot_path, &st) == 0 ? (long)st.st_size : -1;
}

long cb_state_slot_mtime(int slot) {
    if (!g_host.core) return -1;
    build_slot_path(slot);
    struct stat st;
    if (stat(g_host.slot_path, &st) != 0) return -1;
    return (long)st.st_mtime * 1000L;
}
