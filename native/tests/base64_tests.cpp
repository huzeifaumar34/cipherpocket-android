#include <cstdint>
#include <string>
#include <string_view>
#include <vector>
#include <gtest/gtest.h>
#include "cp/engine.hpp"

namespace {
using cp::Operation;
std::vector<std::uint8_t> bytes(std::string_view s) { return {s.begin(), s.end()}; }
std::string text(const std::vector<std::uint8_t>& b) { return {b.begin(), b.end()}; }
cp::Result run(Operation op, std::string_view input, std::string_view arg = {}) {
    return cp::execute(op, bytes(input), bytes(arg));
}

TEST(Base64, KnownVectorAndUtf8RoundTrip) {
    auto encoded=run(Operation::B64Enc,"Hello, world!");
    ASSERT_TRUE(encoded.ok);
    EXPECT_EQ(text(encoded.output),"SGVsbG8sIHdvcmxkIQ==");
    auto decoded=run(Operation::B64Dec,text(encoded.output));
    ASSERT_TRUE(decoded.ok);
    EXPECT_EQ(text(decoded.output),"Hello, world!");
    const std::string original="Olá, mundo 🌍";
    auto utf8=run(Operation::B64Enc,original);
    auto back=run(Operation::B64Dec,text(utf8.output));
    ASSERT_TRUE(back.ok);
    EXPECT_EQ(text(back.output),original);
}
TEST(Base64, RejectsMalformedInput) {
    EXPECT_FALSE(run(Operation::B64Dec,"abc").ok);
    EXPECT_FALSE(run(Operation::B64Dec,"ab?=").ok);
    EXPECT_FALSE(run(Operation::B64Dec,"AB==").ok);
    EXPECT_TRUE(run(Operation::B64Enc,"").ok);
}
TEST(BinaryAndHex, RoundTripText) {
    const std::string original="AΩ";
    auto binary=run(Operation::BinEnc,original);
    ASSERT_TRUE(binary.ok);
    EXPECT_NE(text(binary.output).find("01000001"),std::string::npos);
    auto binaryBack=run(Operation::BinDec,text(binary.output));
    ASSERT_TRUE(binaryBack.ok);
    EXPECT_EQ(text(binaryBack.output),original);
    auto hex=run(Operation::HexEnc,original);
    auto hexBack=run(Operation::HexDec,text(hex.output));
    ASSERT_TRUE(hexBack.ok);
    EXPECT_EQ(text(hexBack.output),original);
}
TEST(ClassicalCiphers, CaesarRot13AtbashAndBruteForce) {
    EXPECT_EQ(text(run(Operation::Caesar,"Abc XYZ","3").output),"Def ABC");
    EXPECT_EQ(text(run(Operation::Caesar,"Def ABC","-3").output),"Abc XYZ");
    EXPECT_EQ(text(run(Operation::Rot13,"Hello").output),"Uryyb");
    EXPECT_EQ(text(run(Operation::Atbash,"Svool").output),"Hello");
    EXPECT_FALSE(run(Operation::Caesar,"test","nope").ok);
    EXPECT_NE(text(run(Operation::CaesarBrute,"abc").output).find("Shift 25:"),std::string::npos);
}
TEST(ClassicalCiphers, VigenereKnownVector) {
    auto encrypted=run(Operation::VigenereEnc,"ATTACKATDAWN","LEMON");
    ASSERT_TRUE(encrypted.ok);
    EXPECT_EQ(text(encrypted.output),"LXFOPVEFRNHR");
    auto decrypted=run(Operation::VigenereDec,text(encrypted.output),"LEMON");
    ASSERT_TRUE(decrypted.ok);
    EXPECT_EQ(text(decrypted.output),"ATTACKATDAWN");
    EXPECT_FALSE(run(Operation::VigenereEnc,"hello","123").ok);
}
TEST(ClassicalCiphers, XorHexRoundTrip) {
    auto encrypted=run(Operation::XorEnc,"secret message","pocket-key");
    ASSERT_TRUE(encrypted.ok);
    auto decrypted=run(Operation::XorDec,text(encrypted.output),"pocket-key");
    ASSERT_TRUE(decrypted.ok);
    EXPECT_EQ(text(decrypted.output),"secret message");
    EXPECT_FALSE(run(Operation::XorEnc,"x").ok);
}
TEST(Morse, AccentsAndWordSeparators) {
    auto encoded=run(Operation::MorseEnc,"Olá mundo");
    ASSERT_TRUE(encoded.ok);
    auto decoded=run(Operation::MorseDec,text(encoded.output));
    ASSERT_TRUE(decoded.ok);
    EXPECT_EQ(text(decoded.output),"OLA MUNDO");
    EXPECT_FALSE(run(Operation::MorseDec,"...-.-.-").ok);
}
TEST(TextTools, CaseReverseStatisticsAndFrequency) {
    EXPECT_EQ(text(run(Operation::Upper,"Olá mundo").output),"OLÁ MUNDO");
    EXPECT_EQ(text(run(Operation::Lower,"OLÁ MUNDO").output),"olá mundo");
    EXPECT_EQ(text(run(Operation::Reverse,"A🌍B").output),"B🌍A");
    EXPECT_NE(text(run(Operation::Stats,"one two").output).find("Words: 2"),std::string::npos);
    EXPECT_NE(text(run(Operation::Freq,"ABBA").output).find("A: 2"),std::string::npos);
}
TEST(Conversion, BasesTwoToThirtySix) {
    EXPECT_EQ(text(run(Operation::BaseConvert,"FF","16:10").output),"255");
    EXPECT_EQ(text(run(Operation::BaseConvert,"255","10:16").output),"FF");
    EXPECT_EQ(text(run(Operation::BaseConvert,"11111111","2:16").output),"FF");
    EXPECT_FALSE(run(Operation::BaseConvert,"18446744073709551616","10:16").ok);
    EXPECT_FALSE(run(Operation::BaseConvert,"10","1:10").ok);
}
TEST(DetectAndPassword, FindsEncodingAndScoresPassword) {
    auto detected=run(Operation::AutoDetect,"SGVsbG8=");
    ASSERT_TRUE(detected.ok);
    EXPECT_NE(text(detected.output).find("Base64 -> Hello"),std::string::npos);
    EXPECT_NE(text(run(Operation::PasswordCheck,"Longer-Secure-2026!").output).find("Strong"),std::string::npos);
}
TEST(Utf8, CaseChangeKeepsEmojiAndAccents) {
    EXPECT_EQ(text(run(Operation::Upper,"a\xC3\xA7\xC3\xA3o \xF0\x9F\x98\x80 teste").output),
              "A\xC3\x87\xC3\x83O \xF0\x9F\x98\x80 TESTE");
    EXPECT_EQ(text(run(Operation::Lower,"A\xC3\x87\xC3\x83O \xF0\x9F\x98\x80 TESTE").output),
              "a\xC3\xA7\xC3\xA3o \xF0\x9F\x98\x80 teste");
}
TEST(Utf8, MalformedBytesPassThroughUnchanged) {
    const std::string broken="ab\xFF\xC3 cd\xF0\x9F";
    EXPECT_EQ(text(run(Operation::Lower,broken).output),broken);
    EXPECT_EQ(text(run(Operation::Upper,broken).output),"AB\xFF\xC3 CD\xF0\x9F");
}
TEST(Utf8, StatisticsCountCharactersNotBytes) {
    const auto out=text(run(Operation::Stats,"Ol\xC3\xA1 \xF0\x9F\x98\x80").output);
    EXPECT_NE(out.find("Bytes: 9"),std::string::npos);
    EXPECT_NE(out.find("Characters: 5"),std::string::npos);
    EXPECT_NE(out.find("Words: 2"),std::string::npos);
}
TEST(Utf8, FrequencyAndMorseIgnoreEmoji) {
    const auto freq=text(run(Operation::Freq,"\xC3\x81" "a \xF0\x9F\x98\x80").output);
    EXPECT_NE(freq.find("A: 2 (100.0%)"),std::string::npos);
    EXPECT_EQ(text(run(Operation::MorseEnc,"sos \xF0\x9F\x98\x80").output),"... --- ...");
}
TEST(Aes256Gcm, ConversorV2CompatibilityVector) {
    const std::string password="Frase-Longa-e-Segura-2026!";
    const std::string cipher="ARc3Kn4rIjbuTO+zEUqTiMvEGmtOBKCpDWd9KeUIq5MOIp1yGOVj36qE68BJHSVLew==";
    auto result=run(Operation::AesDec,cipher,password);
    ASSERT_TRUE(result.ok) << text(result.output);
    EXPECT_EQ(text(result.output),"ZIKA");
}
TEST(Aes256Gcm, RawBytesRoundTripAndTamperRejection) {
    const std::vector<std::uint8_t> original{0U,1U,2U,127U,128U,255U};
    const std::vector<std::uint8_t> password{'p','a','s','s'};
    auto encrypted=cp::execute(Operation::AesEncRaw,original,password);
    ASSERT_TRUE(encrypted.ok);
    auto decrypted=cp::execute(Operation::AesDecRaw,encrypted.output,password);
    ASSERT_TRUE(decrypted.ok);
    EXPECT_EQ(decrypted.output,original);
    auto damaged=encrypted.output;
    damaged[29]^=1U;
    auto rejected=cp::execute(Operation::AesDecRaw,damaged,password);
    EXPECT_FALSE(rejected.ok);
    EXPECT_EQ(text(rejected.output),"Unable to decrypt data. Password may be incorrect or data may be damaged.");
}
} // namespace
