# Endless Sea — ProGuard/R8 rules

# Public extension contract (loaded reflectively from .esx plugins)
-keep class dev.endlesssea.extensions.api.** { *; }
-keepclassmembers class dev.endlesssea.extensions.api.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ** { @kotlinx.serialization.Serializable <fields>; }
-keep,includedescriptorclasses class **$$serializer { *; }

# Room / SQLDelight annotations handled by KSP — nothing to keep by hand here.
# Media3 / OkHttp ship their own consumer rules.

# Keep line numbers in crash reports, strip debug noise otherwise.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
