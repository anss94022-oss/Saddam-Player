# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
# Room
-keep class com.sdm.videoplayer.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
