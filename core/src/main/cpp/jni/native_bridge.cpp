#include <jni.h>

#include <cstdint>
#include <exception>
#include <string>
#include <vector>

#include "cp/engine.hpp"

namespace {

std::vector<std::uint8_t> read_bytes(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) {
        return {};
    }

    const jsize length = env->GetArrayLength(array);
    std::vector<std::uint8_t> bytes(static_cast<std::size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(
            array,
            0,
            length,
            reinterpret_cast<jbyte*>(bytes.data())
        );
    }
    return bytes;
}

void wipe(std::vector<std::uint8_t>& bytes) noexcept {
    volatile std::uint8_t* ptr = bytes.empty() ? nullptr : bytes.data();
    for (std::size_t index = 0; index < bytes.size(); ++index) {
        ptr[index] = 0;
    }
}

jbyteArray make_response(
    JNIEnv* env,
    bool success,
    const std::vector<std::uint8_t>& payload
) {
    std::vector<std::uint8_t> response;
    response.reserve(payload.size() + 1U);
    response.push_back(success ? 0U : 1U);
    response.insert(response.end(), payload.begin(), payload.end());

    auto result = env->NewByteArray(static_cast<jsize>(response.size()));
    if (result != nullptr && !response.empty()) {
        env->SetByteArrayRegion(
            result,
            0,
            static_cast<jsize>(response.size()),
            reinterpret_cast<const jbyte*>(response.data())
        );
    }
    wipe(response);
    return result;
}

jbyteArray make_error(JNIEnv* env, const std::string& message) {
    const std::vector<std::uint8_t> bytes(message.begin(), message.end());
    return make_response(env, false, bytes);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_io_github_huzeifaumar34_cipherpocket_core_NativeBridge_nativeApiVersion(
    JNIEnv* /* env */,
    jobject /* receiver */
) {
    return cp::kApiVersion;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_huzeifaumar34_cipherpocket_core_NativeBridge_nativeExecute(
    JNIEnv* env,
    jobject /* receiver */,
    jint operation,
    jbyteArray input,
    jbyteArray argument1,
    jbyteArray argument2
) {
    std::vector<std::uint8_t> input_bytes;
    std::vector<std::uint8_t> argument1_bytes;
    std::vector<std::uint8_t> argument2_bytes;

    try {
        input_bytes = read_bytes(env, input);
        argument1_bytes = read_bytes(env, argument1);
        argument2_bytes = read_bytes(env, argument2);

        auto result = cp::execute(
            static_cast<cp::Operation>(operation),
            input_bytes,
            argument1_bytes,
            argument2_bytes
        );

        wipe(input_bytes);
        wipe(argument1_bytes);
        wipe(argument2_bytes);
        auto response = make_response(env, result.ok, result.output);
        wipe(result.output);
        return response;
    } catch (const std::exception& error) {
        wipe(input_bytes);
        wipe(argument1_bytes);
        wipe(argument2_bytes);
        return make_error(env, error.what());
    } catch (...) {
        wipe(input_bytes);
        wipe(argument1_bytes);
        wipe(argument2_bytes);
        return make_error(env, "Unknown native engine error.");
    }
}
