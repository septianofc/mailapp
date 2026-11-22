# R8 Full Mode / ProGuard configuration for Mail Septian Support
# Note: R8 shrinking, minification, and obfuscation raise the bar against static analysis,
# but no client-side obfuscation makes an APK impossible to reverse-engineer or decompile.

# Repackage obfuscated classes into a short flat package and allow access modification
-repackageclasses 'o'
-allowaccessmodification
-optimizationpasses 5

# Strip source file names and line tables or keep minimal obfuscated attributes
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*

# Remove debug and verbose logging calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Strip internal SafeLogger debug calls in release
-assumenosideeffects class com.example.security.SafeLogger {
    public static void d(...);
    public static void w(...);
    public static void i(...);
}

# Keep BiometricPrompt & FragmentActivity callbacks intact
-keepclassmembers class * extends androidx.biometric.BiometricPrompt$AuthenticationCallback {
    *;
}

# Keep DataStore Preferences Protobuf/Lite classes if referenced via reflection
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
