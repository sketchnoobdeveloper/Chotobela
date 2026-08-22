/*
 * Chotobela - Audio backend (AAudio low-latency output)
 * Runs a native callback thread that pulls samples from the active core,
 * avoiding JNI overhead in the audio path.
 */
#ifndef CHOTOBELA_AUDIO_BACKEND_H
#define CHOTOBELA_AUDIO_BACKEND_H

/** Starts the AAudio stream. Returns actual sample rate, or -1 on failure. */
int cb_audio_start(void);

void cb_audio_stop(void);

/** Master volume 0.0..1.0 */
void cb_audio_set_volume(float vol);

int cb_audio_underruns(void);

#endif
