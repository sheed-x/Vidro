# libmpv calls back into MPVLib (and its observer interfaces) from native code by name.
-keep class dev.jdtech.mpv.** { *; }
-keep interface dev.jdtech.mpv.** { *; }
-keepclassmembers class * implements dev.jdtech.mpv.MPVLib$EventObserver { *; }
-keepclassmembers class * implements dev.jdtech.mpv.MPVLib$LogObserver { *; }

# Player settings are stored as JSON through kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class app.vidro.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.vidro.**$$serializer { *; }

# Navigation routes are @Serializable objects resolved by name.
-keep @kotlinx.serialization.Serializable class app.vidro.ui.** { *; }
