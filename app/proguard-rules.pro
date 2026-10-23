# Rules for the release build.

# libarchive calls its Java classes from native code through JNI.
-keep class me.zhanghai.android.libarchive.** { *; }
