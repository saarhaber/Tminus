# R8 (uses ProGuard rule format) — release shrinking & obfuscation

# Readable crash reports when deobfuscating with mapping.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization ships META-INF/proguard rules with the dependency; no extra keeps needed
# unless you use @Serializable classes with named companion objects (see library README).

# Glance widget tap actions instantiate this by class name from a PendingIntent.
-keep class com.saarlabs.tminus.android.widget.WidgetRefreshActionCallback { <init>(); }
