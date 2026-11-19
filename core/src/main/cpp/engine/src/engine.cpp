#include "cp/engine.hpp"

#include <cstdint>
#include <string_view>
#include <utility>
#include <vector>

namespace cp {
namespace {

constexpr std::string_view kAlphabet =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

Result error(const char* message) {
    const auto* begin = reinterpret_cast<const std::uint8_t*>(message);
    const auto* end = begin;
    while (*end != 0) {
        ++end;
    }
    return {false, std::vector<std::uint8_t>(begin, end)};
}

std::vector<std::uint8_t> encode_base64(const std::vector<std::uint8_t>& input) {
    std::vector<std::uint8_t> output;
    output.reserve(((input.size() + 2U) / 3U) * 4U);

    for (std::size_t offset = 0; offset < input.size(); offset += 3U) {
        const std::size_t remaining = input.size() - offset;
        const std::uint32_t first = input[offset];
        const std::uint32_t second = remaining > 1U ? input[offset + 1U] : 0U;
        const std::uint32_t third = remaining > 2U ? input[offset + 2U] : 0U;
        const std::uint32_t block = (first << 16U) | (second << 8U) | third;

        output.push_back(static_cast<std::uint8_t>(kAlphabet[(block >> 18U) & 0x3FU]));
        output.push_back(static_cast<std::uint8_t>(kAlphabet[(block >> 12U) & 0x3FU]));
        output.push_back(remaining > 1U
            ? static_cast<std::uint8_t>(kAlphabet[(block >> 6U) & 0x3FU])
            : static_cast<std::uint8_t>('='));
        output.push_back(remaining > 2U
            ? static_cast<std::uint8_t>(kAlphabet[block & 0x3FU])
            : static_cast<std::uint8_t>('='));
    }

    return output;
}

int decode_value(std::uint8_t character) {
    const auto position = kAlphabet.find(static_cast<char>(character));
    return position == std::string_view::npos ? -1 : static_cast<int>(position);
}

Result decode_base64(const std::vector<std::uint8_t>& input) {
    if (input.size() % 4U != 0U) {
        return error("Invalid Base64 input length.");
    }

    std::vector<std::uint8_t> output;
    output.reserve((input.size() / 4U) * 3U);

    for (std::size_t offset = 0; offset < input.size(); offset += 4U) {
        const bool last_block = offset + 4U == input.size();
        const auto a = input[offset];
        const auto b = input[offset + 1U];
        const auto c = input[offset + 2U];
        const auto d = input[offset + 3U];

        const int va = decode_value(a);
        const int vb = decode_value(b);
        const int vc = c == '=' ? 0 : decode_value(c);
        const int vd = d == '=' ? 0 : decode_value(d);

        if (va < 0 || vb < 0 || vc < 0 || vd < 0) {
            return error("Invalid Base64 character.");
        }
        if (c == '=' && d != '=') {
            return error("Invalid Base64 padding.");
        }
        if ((c == '=' || d == '=') && !last_block) {
            return error("Base64 padding is only allowed at the end.");
        }
        if (c == '=' && (vb & 0x0F) != 0) {
            return error("Invalid Base64 padding bits.");
        }
        if (d == '=' && c != '=' && (vc & 0x03) != 0) {
            return error("Invalid Base64 padding bits.");
        }

        const std::uint32_t block =
            (static_cast<std::uint32_t>(va) << 18U) |
            (static_cast<std::uint32_t>(vb) << 12U) |
            (static_cast<std::uint32_t>(vc) << 6U) |
            static_cast<std::uint32_t>(vd);

        output.push_back(static_cast<std::uint8_t>((block >> 16U) & 0xFFU));
        if (c != '=') {
            output.push_back(static_cast<std::uint8_t>((block >> 8U) & 0xFFU));
        }
        if (d != '=') {
            output.push_back(static_cast<std::uint8_t>(block & 0xFFU));
        }
    }

    return {true, std::move(output)};
}

}  // namespace

Result execute(
    Operation operation,
    const std::vector<std::uint8_t>& input,
    const std::vector<std::uint8_t>&,
    const std::vector<std::uint8_t>&
) {
    switch (operation) {
        case Operation::B64Enc:
            return {true, encode_base64(input)};
        case Operation::B64Dec:
            return decode_base64(input);
        default:
            return error("This operation is scheduled for a later CipherPocket milestone.");
    }
}

}  // namespace cp
