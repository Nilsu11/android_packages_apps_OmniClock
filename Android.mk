LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_JAVA_LIBRARIES := org.apache.http.legacy

src_dirs := src MaterialDateTimePicker/src
res_dirs := res MaterialDateTimePicker/res
asset_dirs := MaterialDateTimePicker/assets

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v13
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-cardview

LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))
LOCAL_ASSET_DIR := $(addprefix $(LOCAL_PATH)/, $(asset_dirs))

LOCAL_PACKAGE_NAME := OmniClockOSS

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_USE_AAPT2 := true

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages com.wdullaer.materialdatetimepicker

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 24

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
