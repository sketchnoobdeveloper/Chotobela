/*
 * Chotobela Engine Host
 *
 * Owns: core registry, ROM file loading, save-state persistence to disk,
 * and the single-threaded engine session lifecycle.
 * The JNI layer (chotobela_jni.c) is the only caller.
 */
#ifndef CHOTOBELA_ENGINE_HOST_H
#define CHOTOBELA_ENGINE_HOST_H

#include "core_abi.h"
#include <stddef.h>

typedef enum {
    CB_SESSION_IDLE = 0,
    CB_SESSION_READY,      /* rom loaded */
    CB_SESSION_RUNNING,
    CB_SESSION_ERROR
} cb_session_state;

/* ---- Registry ---- */
const cb_core_api *cb_find_core(const char *id);
int cb_core_count(void);

/* ---- Session management (host-locked; single engine thread) ---- */

/**
 * Initializes the host. saves_dir must be a writable absolute path.
 * Returns 0 on success.
 */
int cb_host_init(const char *saves_dir);

void cb_host_deinit(void);

/** Loads a ROM file from absolute path into the given core. 0 = ok. */
int cb_load_rom(const char *core_id, const char *rom_path);

/** Resets the active core to power-on state. */
void cb_host_reset(void);

/** Steps one frame on the active core. 0 = ok. */
int cb_step_frame(void);

/* Video access (active core) */
const uint32_t *cb_video_buffer(int *out_w, int *out_h);
float cb_video_aspect(void);

/* Audio access (active core) */
int cb_audio_render(int16_t *out, int max_frames);
int cb_target_sample_rate(void);

void cb_set_buttons(uint32_t mask);

/* ---- Save states (host-managed disk persistence) ----
 * Slots are files: <saves_dir>/<coreid>_<romname>.slot<0..9>.state */
int cb_save_state(int slot);          /* 0 = ok */
int cb_load_state(int slot);          /* 0 = ok */
long cb_state_slot_size(int slot);    /* -1 if missing */
long cb_state_slot_mtime(int slot);   /* unix ms, -1 if missing */

#endif /* CHOTOBELA_ENGINE_HOST_H */
