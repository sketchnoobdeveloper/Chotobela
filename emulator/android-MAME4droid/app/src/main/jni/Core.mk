# Chotobela Arcade — core library builder
#
# Included at the end of Android.mk when MAME_SRC_ROOT points at a merged
# MAME source tree (upstream tag + src-overlay applied on top).
# Produces libMAME4droid.so — the library mame4droid-jni dlopen()s.

ifeq ($(strip $(MAME_SRC_ROOT)),)
$(warning MAME_SRC_ROOT not set — building shell APK without MAME core)
else

LOCAL_PATH := $(call my-dir)
MROOT := $(MAME_SRC_ROOT)

define walk-cpp
$(wildcard $(1)/*.cpp) $(foreach d,$(wildcard $(1)/*),$(call walk-cpp,$(d)))
endef

define walk-c
$(wildcard $(1)/*.c) $(foreach d,$(wildcard $(1)/*),$(call walk-c,$(d)))
endef

include $(CLEAR_VARS)

LOCAL_MODULE := MAME4droid

# ---- curated source sets ----
CORE_DIRS := \
    $(MROOT)/src/emu \
    $(MROOT)/src/lib/util \
    $(MROOT)/src/lib/netlist \
    $(MROOT)/src/lib/formats \
    $(MROOT)/src/lib/softwarelist \
    $(MROOT)/src/lib/utf8proc \
    $(MROOT)/src/osd/myosd \
    $(MROOT)/src/osd/modules/file \
    $(MROOT)/src/osd/modules/lib \
    $(MROOT)/src/osd/modules/font \
    $(MROOT)/src/osd/modules/input \
    $(MROOT)/src/osd/modules/render \
    $(MROOT)/src/osd/modules/sound \
    $(MROOT)/src/osd/modules/debugger \
    $(MROOT)/src/frontend/mame \
    $(MROOT)/src/frontend/mame/ui

DRIVER_DIRS := \
    $(MROOT)/src/mame

EXT_DIRS := \
    $(MROOT)/ext/lua \
    $(MROOT)/ext/expat/lib \
    $(MROOT)/ext/zlib \
    $(MROOT)/ext/utf8proc \
    $(MROOT)/ext/libflac \
    $(MROOT)/ext/libjpeg \
    $(MROOT)/ext/rapidjson/include \
    $(MROOT)/ext/sol2

FILE_LIST := $(foreach d,$(CORE_DIRS),$(call walk-cpp,$(d)))
FILE_LIST += $(foreach d,$(DRIVER_DIRS),$(call walk-cpp,$(d)))
FILE_LIST += $(foreach d,$(EXT_DIRS),$(call walk-cpp,$(d)) $(call walk-c,$(d)))

LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)

# ---- includes ----
LOCAL_C_INCLUDES := \
    $(MROOT)/src/emu \
    $(MROOT)/src/devices \
    $(MROOT)/src/mame \
    $(MROOT)/src/lib \
    $(MROOT)/src/lib/util \
    $(MROOT)/src/lib/netlist \
    $(MROOT)/src/osd \
    $(MROOT)/src/osd/myosd \
    $(MROOT)/src/frontend/mame \
    $(MROOT)/src/frontend/mame/ui \
    $(MROOT)/ext/lua \
    $(MROOT)/ext/expat/lib \
    $(MROOT)/ext/zlib \
    $(MROOT)/ext/utf8proc \
    $(MROOT)/ext/libflac/include \
    $(MROOT)/ext/rapidjson/include \
    $(MROOT)/ext/sol2/include \
    $(MROOT)/ext/bgfx/include \
    $(LOCAL_PATH)

# ---- flags ----
LOCAL_CPPFLAGS += \
    -std=c++17 \
    -fexceptions \
    -frtti \
    -O1 \
    -fno-strict-aliasing \
    -Wno-everything \
    -DNDEBUG \
    -DUSE_QTDEBUG=0 \
    -DNO_DEBUG_BUILTIN \
    -DMAME_DEBUG=0 \
    -DUSE_NETWORK=0 \
    -DUSE_OPENGL=0 \
    -DUSE_SDL_SOUND=0 \
    -DLUA_COMPAT_APIINTCASTS \
    -DZLIB_COMPAT

LOCAL_CFLAGS += -O1 -Wno-everything -DNDEBUG

LOCAL_LDLIBS += -llog -lz -ldl -lm -lEGL -lGLESv2 -lOpenSLES -landroid

include $(BUILD_SHARED_LIBRARY)

endif # MAME_SRC_ROOT
