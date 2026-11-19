#pragma once

#include <cstdint>
#include <vector>

namespace cp {

inline constexpr int kApiVersion = 1;

enum class Operation : std::int32_t {
    BinEnc = 1,
    BinDec = 2,
    HexEnc = 3,
    HexDec = 4,
    B64Enc = 5,
    B64Dec = 6,
    MorseEnc = 7,
    MorseDec = 8,
    Caesar = 9,
    CaesarBrute = 10,
    Rot13 = 11,
    Atbash = 12,
    VigenereEnc = 13,
    VigenereDec = 14,
    XorEnc = 15,
    XorDec = 16,
    AesEnc = 17,
    AesDec = 18,
    AutoDetect = 19,
    Reverse = 20,
    Upper = 21,
    Lower = 22,
    Stats = 23,
    Freq = 24,
    BaseConvert = 25,
    PasswordCheck = 26,
    AesEncRaw = 27,
    AesDecRaw = 28
};

struct Result {
    bool ok;
    std::vector<std::uint8_t> output;
};

Result execute(
    Operation operation,
    const std::vector<std::uint8_t>& input,
    const std::vector<std::uint8_t>& argument1 = {},
    const std::vector<std::uint8_t>& argument2 = {}
);

}  // namespace cp
