# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# FUTO Swipe: classes and members are used from native code via JNI
-keep class org.futo.ml.inference.** { *; }

# ONNX Runtime (voice typing): its native code finds these Java classes and methods by name
-keep class ai.onnxruntime.** { *; }
