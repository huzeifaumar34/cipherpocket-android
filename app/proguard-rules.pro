# JNI resolves these method names directly. Keep the bridge and its native methods.
-keep class io.github.huzeifaumar34.cipherpocket.core.NativeBridge { *; }

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
