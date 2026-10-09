# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------
# Logs legibles en PRO (ver FileLoggingTree y LoggingInitializer)
# ---------------------------------------------------------------------

# Trazas de excepción con fichero y número de línea. Para las líneas de métodos
# inlineados por R8 hace falta el mapping.txt de la versión (build/outputs/mapping/pro/).
-keepattributes SourceFile,LineNumberTable

# Timber infiere el tag del nombre de la clase que llama: sin esto los tags
# saldrían ofuscados ("a", "b"...). Solo conserva nombres; R8 sigue eliminando
# el código no usado.
-keepnames class com.redur.electra.** { *; }

# DebugTree (Timber 4) localiza a quien llama en una posición fija de la pila:
# si R8 inlineara los métodos de Timber, el tag apuntaría a otra clase.
-keep class timber.log.** { *; }

# En PRO el nivel mínimo es INFO: se eliminan las llamadas v/d del bytecode.
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
}
