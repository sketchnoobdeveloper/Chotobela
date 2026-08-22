#include "audio_backend.h"
#include "../engine-host/engine_host.h"

#include <aaudio/AAudio.h>
#include <string.h>

#define CB_AUDIO_CHANNELS 2

static AAudioStream *g_stream = NULL;
static volatile float g_volume = 1.0f;
static volatile int32_t g_underruns = 0;

static aaudio_data_callback_result_t audio_callback(
        AAudioStream *stream,
        void *user_data,
        void *audio_data,
        int32_t num_frames) {
    (void)stream; (void)user_data;

    int16_t *out = (int16_t *)audio_data;
    if (num_frames <= 0) return AAUDIO_CALLBACK_RESULT_CONTINUE;

    int written = cb_audio_render(out, num_frames);
    if (written < num_frames) {
        memset(out + written * CB_AUDIO_CHANNELS, 0,
               (size_t)(num_frames - written) * CB_AUDIO_CHANNELS * sizeof(int16_t));
    }

    float vol = g_volume;
    if (vol < 0.999f) {
        for (int i = 0; i < num_frames * CB_AUDIO_CHANNELS; i++) {
            out[i] = (int16_t)(out[i] * vol);
        }
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void error_callback(AAudioStream *stream, void *user_data, aaudio_result_t error) {
    (void)stream; (void)user_data;
    g_underruns++;
}

int cb_audio_start(void) {
    if (g_stream) return (int)AAudioStream_getSampleRate(g_stream);

    AAudioStreamBuilder *builder = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return -1;

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, CB_AUDIO_CHANNELS);
    AAudioStreamBuilder_setSampleRate(builder, cb_target_sample_rate());
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setDataCallback(builder, audio_callback, NULL);
    AAudioStreamBuilder_setErrorCallback(builder, error_callback, NULL);

    AAudioStream *stream = NULL;
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    if (result != AAUDIO_OK) {
        AAudioStreamBuilder_delete(builder);
        return -1;
    }

    result = AAudioStream_requestStart(stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        AAudioStream_close(stream);
        return -1;
    }

    g_stream = stream;
    return (int)AAudioStream_getSampleRate(stream);
}

void cb_audio_stop(void) {
    if (!g_stream) return;
    AAudioStream_requestStop(g_stream);
    AAudioStream_close(g_stream);
    g_stream = NULL;
}

void cb_audio_set_volume(float vol) {
    if (vol < 0.0f) vol = 0.0f;
    if (vol > 1.0f) vol = 1.0f;
    g_volume = vol;
}

int cb_audio_underruns(void) { return g_underruns; }
