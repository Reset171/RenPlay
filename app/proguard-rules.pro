-keep class org.renpy.** { *; }
-keep class org.libsdl.** { *; }
-keep class org.jnius.** { *; }

# Google ML Kit & Firebase Components (Translation)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep public class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}

# Keep Python JNI bridge classes
-keep class ru.reset.renplay.utils.RenPlayTranslator { *; }