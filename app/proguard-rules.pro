# kotlinx.serialization: the library ships its own rules, these make sure the app's own
# @Serializable classes (saved settings, effects and playback state) keep their generated
# serializers, so data written by one release can still be read by the next.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class io.github.parthibdebnath.openplayer.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.parthibdebnath.openplayer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.parthibdebnath.openplayer.**$$serializer { *; }

# Stack traces from release builds stay readable with the mapping file.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Release builds drop verbose, debug and info logging, which names songs as they play. Warnings
# and errors stay, for bug reports, and are written without file names or paths.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
