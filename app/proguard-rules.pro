# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# App POJOs, Room Entities, Models & Presenters (prevents Gson/Room stripping)
-keep class com.fanok.audiobooks.pojo.** { *; }
-keep class com.fanok.audiobooks.room.** { *; }
-keep class com.fanok.audiobooks.model.** { *; }
-keep class com.fanok.audiobooks.presenter.** { *; }
-keep class com.fanok.audiobooks.interface_pacatge.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Gson serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * implements java.io.Serializable { *; }

# Moxy MVP
-keep class com.arellomobile.mvp.** { *; }
-keep class tech.schoolhelper.moxy.** { *; }

# JSoup
-keep class org.jsoup.** { *; }

# Glide (if AppGlideModule is ever added)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }

# Release builds drop debug/verbose logging (search queries, URLs).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
