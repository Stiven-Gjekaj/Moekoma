# Rules for the release build.

# libarchive calls its Java classes from native code through JNI.
-keep class me.zhanghai.android.libarchive.** { *; }

# The Mihon extensions call the classes of the app by name, so these names
# stay. These are the rules of Mihon. The module ":mihon" keeps its own API.
-dontobfuscate
-keepattributes Signature, *Annotation*, InnerClasses
-keep,allowshrinking class * extends uy.kohesive.injekt.api.FullTypeReference
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** { <methods>; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
