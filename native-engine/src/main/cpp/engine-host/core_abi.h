/*
 * Chotobela Engine - Core ABI
 *
 * The contract between the engine host and any emulator core.
 * Cores are pure C modules with no platform dependencies; the host owns
 * threading, file I/O, save states, and platform backends (video/audio/input).
 *
 * A core registers itself via cb_register_core() at library load time.
 * Future adapters: mame/, fbneo/, libretro/ implement this same ABI.
 */
#ifndef CHOTOBELA_CORE_ABI_H
#define CHOTOBELA_CORE_ABI_H

#include <stddef.h>
#include <stdint.h>

#define CB_MAX_CORES 8
#define CB_CORE_ID_LEN 32
#define CB_BUTTON_COUNT 16

typedef struct cb_core_api {
    const char *id;                 /* unique core id, e.g. "chip8" */

    /* Lifecycle */
    int (*init)(void);              /* allocate per-run state. 0 = ok */
    void (*deinit)(void);           /* free everything */
    int (*load_rom)(const uint8_t *data, size_t size); /* 0 = ok */
    void (*reset)(void);

    /* Emulation: advance exactly one video frame (~60Hz) */
    int (*step_frame)(void);        /* 0 = ok, nonzero = fatal */

    /* Video: returns pointer to internal ARGB8888 buffer (w*h) */
    const uint32_t *(*video_buffer)(int *out_w, int *out_h);
    /* Nominal aspect ratio as width/height float, e.g. 64.0f/32.0f */
    float (*video_aspect)(void);

    /* Audio: fill up to max_frames stereo s16 interleaved frames.
     * Returns frames written. Called from audio thread. */
    int (*audio_render)(int16_t *out, int max_frames);
    int (*target_sample_rate)(void);

    /* Input: bitmask of pressed buttons, bit i = button i */
    void (*set_buttons)(uint32_t mask);

    /* Save states: serialize full machine state.
     * save writes <= cap bytes, returns written or -1.
     * load restores from buf, returns 0 or -1. */
    long (*state_save)(uint8_t *buf, size_t cap);
    int (*state_load)(const uint8_t *buf, size_t size);
    long (*state_size)(void);       /* upper bound of serialized size */
} cb_core_api;

/* Implemented by each core translation unit; host scans registry after load. */
void cb_register_core(const cb_core_api *api);

#endif /* CHOTOBELA_CORE_ABI_H */
