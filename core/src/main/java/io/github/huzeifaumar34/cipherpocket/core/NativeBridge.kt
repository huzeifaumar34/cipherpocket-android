package io.github.huzeifaumar34.cipherpocket.core

/**
 * JNI boundary. Only byte arrays cross this layer; never pass Kotlin String to JNI.
 */
internal class NativeBridge {
    init {
        System.loadLibrary("cipherpocket_jni")
    }

    external fun nativeApiVersion(): Int

    external fun nativeExecute(
        operation: Int,
        input: ByteArray,
        argument1: ByteArray,
        argument2: ByteArray
    ): ByteArray
}
