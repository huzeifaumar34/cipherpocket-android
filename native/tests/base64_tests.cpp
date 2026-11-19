#include <cstdint>
#include <string>
#include <vector>

#include <gtest/gtest.h>

#include "cp/engine.hpp"

namespace {

std::vector<std::uint8_t> bytes(const std::string& value) {
    return {value.begin(), value.end()};
}

std::string text(const std::vector<std::uint8_t>& value) {
    return {value.begin(), value.end()};
}

TEST(Base64, EncodesKnownVector) {
    const auto result = cp::execute(cp::Operation::B64Enc, bytes("Hello, world!"));
    ASSERT_TRUE(result.ok);
    EXPECT_EQ(text(result.output), "SGVsbG8sIHdvcmxkIQ==");
}

TEST(Base64, DecodesKnownVector) {
    const auto result = cp::execute(cp::Operation::B64Dec, bytes("SGVsbG8sIHdvcmxkIQ=="));
    ASSERT_TRUE(result.ok);
    EXPECT_EQ(text(result.output), "Hello, world!");
}

TEST(Base64, PreservesUtf8Bytes) {
    const std::string original = "Olá, mundo 🌍";
    const auto encoded = cp::execute(cp::Operation::B64Enc, bytes(original));
    ASSERT_TRUE(encoded.ok);
    const auto decoded = cp::execute(cp::Operation::B64Dec, encoded.output);
    ASSERT_TRUE(decoded.ok);
    EXPECT_EQ(text(decoded.output), original);
}

TEST(Base64, SupportsEmptyInput) {
    const auto encoded = cp::execute(cp::Operation::B64Enc, {});
    ASSERT_TRUE(encoded.ok);
    EXPECT_TRUE(encoded.output.empty());

    const auto decoded = cp::execute(cp::Operation::B64Dec, {});
    ASSERT_TRUE(decoded.ok);
    EXPECT_TRUE(decoded.output.empty());
}

TEST(Base64, RejectsInvalidLength) {
    const auto result = cp::execute(cp::Operation::B64Dec, bytes("abc"));
    ASSERT_FALSE(result.ok);
    EXPECT_EQ(text(result.output), "Invalid Base64 input length.");
}

TEST(Base64, RejectsInvalidCharacters) {
    const auto result = cp::execute(cp::Operation::B64Dec, bytes("ab?="));
    ASSERT_FALSE(result.ok);
    EXPECT_EQ(text(result.output), "Invalid Base64 character.");
}

TEST(Base64, RejectsNonCanonicalPaddingBits) {
    const auto result = cp::execute(cp::Operation::B64Dec, bytes("AB=="));
    ASSERT_FALSE(result.ok);
    EXPECT_EQ(text(result.output), "Invalid Base64 padding bits.");
}

}  // namespace
