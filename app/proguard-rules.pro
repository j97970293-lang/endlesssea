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

# --- Contrat ABI des extensions compilées (.esx) -----------------------------
# Le loader instancie chaque extension avec le ClassLoader de l'app comme parent
# (ExtensionLoader.instantiate → PathClassLoader(apk, EsExtension::class.java.classLoader)).
# Les extensions déclarent donc kotlin-stdlib, kotlinx-coroutines et jsoup en
# compileOnly et ne les embarquent pas (1,2 Mo → ~97 Ko par .esx). R8 ne doit
# pas élaguer ni renommer ces classes, sinon toute extension compilée casse.
-keep class org.jsoup.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn org.jsoup.**
