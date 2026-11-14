# ML Kit bundled models reflect over generated classes.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep the pure-Kotlin intelligence models (used in serialized prefs).
-keep class com.aiphotographer.intelligence.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
