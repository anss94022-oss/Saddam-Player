# Keep Media3 components and Room-generated code safe for release builds.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
