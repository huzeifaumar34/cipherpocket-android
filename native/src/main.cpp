#include <cstdlib>
#include <iostream>
#include <iterator>
#include <string>
#include <utility>
#include <vector>
#include "cp/engine.hpp"

namespace {
bool find_operation(const std::string& name, cp::Operation& operation) {
    using Pair=std::pair<const char*,cp::Operation>;
    static const Pair operations[]={
        {"bin-encode",cp::Operation::BinEnc},{"bin-decode",cp::Operation::BinDec},
        {"hex-encode",cp::Operation::HexEnc},{"hex-decode",cp::Operation::HexDec},
        {"b64-encode",cp::Operation::B64Enc},{"b64-decode",cp::Operation::B64Dec},
        {"morse-encode",cp::Operation::MorseEnc},{"morse-decode",cp::Operation::MorseDec},
        {"caesar",cp::Operation::Caesar},{"caesar-brute",cp::Operation::CaesarBrute},
        {"rot13",cp::Operation::Rot13},{"atbash",cp::Operation::Atbash},
        {"vigenere-encode",cp::Operation::VigenereEnc},{"vigenere-decode",cp::Operation::VigenereDec},
        {"xor-encode",cp::Operation::XorEnc},{"xor-decode",cp::Operation::XorDec},
        {"aes-encode",cp::Operation::AesEnc},{"aes-decode",cp::Operation::AesDec},
        {"detect",cp::Operation::AutoDetect},{"reverse",cp::Operation::Reverse},
        {"upper",cp::Operation::Upper},{"lower",cp::Operation::Lower},
        {"stats",cp::Operation::Stats},{"frequency",cp::Operation::Freq},
        {"base-convert",cp::Operation::BaseConvert},{"password-check",cp::Operation::PasswordCheck},
        {"aes-encode-raw",cp::Operation::AesEncRaw},{"aes-decode-raw",cp::Operation::AesDecRaw}
    };
    for(const auto& item:operations) if(name==item.first){operation=item.second;return true;}
    return false;
}
void usage(const char* program) {
    std::cerr<<"CipherPocket native CLI\nUsage: "<<program<<" <operation>\n"
      <<"Reads input bytes from stdin and writes result bytes to stdout.\n"
      <<"Optional parameters: CP_ARGUMENT1 and CP_ARGUMENT2 environment variables.\n"
      <<"Operations include bin-encode/decode, hex-encode/decode, b64-encode/decode,\n"
      <<"morse-encode/decode, caesar, caesar-brute, rot13, atbash, vigenere-encode/decode,\n"
      <<"xor-encode/decode, aes-encode/decode, detect, reverse, upper, lower, stats,\n"
      <<"frequency, base-convert, password-check, aes-encode-raw and aes-decode-raw.\n";
}
}
int main(int argc,char** argv) {
    if(argc!=2){usage(argv[0]);return 2;}
    cp::Operation operation{};
    if(!find_operation(argv[1],operation)){usage(argv[0]);return 2;}
    std::string input(std::istreambuf_iterator<char>(std::cin),{});
    const char* p1=std::getenv("CP_ARGUMENT1");
    const char* p2=std::getenv("CP_ARGUMENT2");
    const std::vector<std::uint8_t> in(input.begin(),input.end());
    const std::vector<std::uint8_t> a1=p1?std::vector<std::uint8_t>(p1,p1+std::char_traits<char>::length(p1)):std::vector<std::uint8_t>{};
    const std::vector<std::uint8_t> a2=p2?std::vector<std::uint8_t>(p2,p2+std::char_traits<char>::length(p2)):std::vector<std::uint8_t>{};
    auto result=cp::execute(operation,in,a1,a2);
    if(!result.ok){
        std::cerr.write(reinterpret_cast<const char*>(result.output.data()),static_cast<std::streamsize>(result.output.size()));
        std::cerr<<'\n';return 1;
    }
    if(!result.output.empty())
        std::cout.write(reinterpret_cast<const char*>(result.output.data()),static_cast<std::streamsize>(result.output.size()));
    return 0;
}
