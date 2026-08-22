# Keep JNI bridge classes and native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.chotobela.engine.** { *; }
-keep class com.chotobela.core.native.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.chotobela.**$$serializer { *; }
-keepclassmembers class com.chotobela.** { *** Companion; }
-keepclasseswithmembers class com.chotobela.** { kotlinx.serialization.KSerializer serializer(...); }

# Supabase / Ktor
-dontwarn org.slf4j.**
-dontwarn io.netty.**
