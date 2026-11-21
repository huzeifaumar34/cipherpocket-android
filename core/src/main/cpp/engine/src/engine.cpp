#include "cp/engine.hpp"

#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/rand.h>

#include <algorithm>
#include <array>
#include <cctype>
#include <climits>
#include <cstdint>
#include <iomanip>
#include <limits>
#include <sstream>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace cp {
namespace {
using Bytes = std::vector<std::uint8_t>;
constexpr std::string_view kB64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
constexpr std::string_view kHex = "0123456789ABCDEF";
constexpr int kIterations = 600000;
constexpr std::size_t kHeader = 29U;
constexpr std::size_t kTag = 16U;

Result error(std::string_view message) {
    return {false, Bytes(message.begin(), message.end())};
}
Result ok(Bytes bytes) { return {true, std::move(bytes)}; }
Result ok(std::string_view text) { return {true, Bytes(text.begin(), text.end())}; }
std::string text(const Bytes& bytes) { return {bytes.begin(), bytes.end()}; }
Bytes bytes(std::string_view value) { return {value.begin(), value.end()}; }

void wipe(Bytes& value) {
    if (!value.empty()) OPENSSL_cleanse(value.data(), value.size());
}
void wipe(std::string& value) {
    if (!value.empty()) OPENSSL_cleanse(value.data(), value.size());
}
struct StringWiper {
    std::string& value;
    ~StringWiper() { wipe(value); }
};

std::string trim(std::string_view value) {
    std::size_t first = 0, last = value.size();
    while (first < last && std::isspace(static_cast<unsigned char>(value[first]))) ++first;
    while (last > first && std::isspace(static_cast<unsigned char>(value[last - 1]))) --last;
    return std::string(value.substr(first, last - first));
}

int b64_value(std::uint8_t ch) {
    const auto pos = kB64.find(static_cast<char>(ch));
    return pos == std::string_view::npos ? -1 : static_cast<int>(pos);
}
Bytes b64_encode(const Bytes& input) {
    Bytes out;
    out.reserve(((input.size() + 2U) / 3U) * 4U);
    for (std::size_t i = 0; i < input.size(); i += 3U) {
        const auto n = std::min<std::size_t>(3U, input.size() - i);
        const std::uint32_t a = input[i];
        const std::uint32_t b = n > 1U ? input[i + 1U] : 0U;
        const std::uint32_t c = n > 2U ? input[i + 2U] : 0U;
        const std::uint32_t v = (a << 16U) | (b << 8U) | c;
        out.push_back(static_cast<std::uint8_t>(kB64[(v >> 18U) & 63U]));
        out.push_back(static_cast<std::uint8_t>(kB64[(v >> 12U) & 63U]));
        out.push_back(n > 1U ? static_cast<std::uint8_t>(kB64[(v >> 6U) & 63U]) : '=');
        out.push_back(n > 2U ? static_cast<std::uint8_t>(kB64[v & 63U]) : '=');
    }
    return out;
}
bool b64_decode(const Bytes& raw, Bytes& out, std::string& message) {
    // Ignore ASCII whitespace (pasted text often has a trailing newline or MIME line wrapping).
    Bytes in;
    in.reserve(raw.size());
    for (auto c : raw) {
        if (!std::isspace(static_cast<unsigned char>(c))) in.push_back(c);
    }
    if (in.size() % 4U != 0U) { message = "Invalid Base64 input length."; return false; }
    out.clear();
    out.reserve(in.size() / 4U * 3U);
    for (std::size_t i = 0; i < in.size(); i += 4U) {
        const bool last = i + 4U == in.size();
        const int a = b64_value(in[i]), b = b64_value(in[i + 1U]);
        const int c = in[i + 2U] == '=' ? 0 : b64_value(in[i + 2U]);
        const int d = in[i + 3U] == '=' ? 0 : b64_value(in[i + 3U]);
        if (a < 0 || b < 0 || c < 0 || d < 0) { message = "Invalid Base64 character."; return false; }
        if (in[i + 2U] == '=' && in[i + 3U] != '=') { message = "Invalid Base64 padding."; return false; }
        if ((in[i + 2U] == '=' || in[i + 3U] == '=') && !last) {
            message = "Base64 padding is only allowed at the end."; return false;
        }
        if (in[i + 2U] == '=' && (b & 15) != 0) { message = "Invalid Base64 padding bits."; return false; }
        if (in[i + 3U] == '=' && in[i + 2U] != '=' && (c & 3) != 0) {
            message = "Invalid Base64 padding bits."; return false;
        }
        const std::uint32_t v = (static_cast<std::uint32_t>(a) << 18U) |
            (static_cast<std::uint32_t>(b) << 12U) | (static_cast<std::uint32_t>(c) << 6U) |
            static_cast<std::uint32_t>(d);
        out.push_back(static_cast<std::uint8_t>(v >> 16U));
        if (in[i + 2U] != '=') out.push_back(static_cast<std::uint8_t>(v >> 8U));
        if (in[i + 3U] != '=') out.push_back(static_cast<std::uint8_t>(v));
    }
    return true;
}

int hex_value(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
std::string hex_encode(const Bytes& in) {
    std::string out;
    out.reserve(in.size() * 2U);
    for (auto b : in) { out.push_back(kHex[b >> 4U]); out.push_back(kHex[b & 15U]); }
    return out;
}
bool hex_decode(std::string_view in, Bytes& out, std::string& message) {
    std::string digits;
    for (char c : in) {
        if (!std::isspace(static_cast<unsigned char>(c)) && c != ':' && c != '-') digits.push_back(c);
    }
    if (digits.size() >= 2U && digits[0] == '0' && (digits[1] == 'x' || digits[1] == 'X')) {
        digits.erase(0, 2);
    }
    if (digits.size() % 2U != 0U) { message = "Hexadecimal input must contain an even number of digits."; return false; }
    out.clear(); out.reserve(digits.size() / 2U);
    for (std::size_t i = 0; i < digits.size(); i += 2U) {
        const int a = hex_value(digits[i]), b = hex_value(digits[i + 1U]);
        if (a < 0 || b < 0) { message = "Invalid hexadecimal character."; return false; }
        out.push_back(static_cast<std::uint8_t>((a << 4) | b));
    }
    return true;
}

std::string binary_encode(const Bytes& in) {
    std::string out;
    for (std::size_t i = 0; i < in.size(); ++i) {
        if (i) out.push_back(' ');
        for (int bit = 7; bit >= 0; --bit) out.push_back((in[i] & (1U << bit)) ? '1' : '0');
    }
    return out;
}
bool binary_decode(std::string_view in, Bytes& out, std::string& message) {
    std::string bits;
    for (char c : in) {
        if (c == '0' || c == '1') bits.push_back(c);
        else if (!std::isspace(static_cast<unsigned char>(c)) && c != '_') {
            message = "Binary input may contain only 0 and 1."; return false;
        }
    }
    if (bits.size() % 8U) { message = "Binary input must contain a multiple of 8 bits."; return false; }
    out.clear(); out.reserve(bits.size() / 8U);
    for (std::size_t i = 0; i < bits.size(); i += 8U) {
        std::uint8_t v = 0;
        for (std::size_t j = 0; j < 8U; ++j) v = static_cast<std::uint8_t>((v << 1U) | (bits[i + j] - '0'));
        out.push_back(v);
    }
    return true;
}

struct Decoded {
    std::uint32_t cp;
    std::size_t width;
    bool valid;
};
// Decodes one UTF-8 sequence at `at`. Malformed input yields a single raw byte (valid=false).
Decoded decode_utf8(std::string_view in, std::size_t at) {
    const auto lead = static_cast<unsigned char>(in[at]);
    std::size_t width = 0;
    std::uint32_t cp = 0, minimum = 0;
    if (lead < 0x80U) return {lead, 1U, true};
    if ((lead & 0xE0U) == 0xC0U) { width = 2U; cp = lead & 0x1FU; minimum = 0x80U; }
    else if ((lead & 0xF0U) == 0xE0U) { width = 3U; cp = lead & 0x0FU; minimum = 0x800U; }
    else if ((lead & 0xF8U) == 0xF0U) { width = 4U; cp = lead & 0x07U; minimum = 0x10000U; }
    else return {lead, 1U, false};
    if (at + width > in.size()) return {lead, 1U, false};
    for (std::size_t k = 1; k < width; ++k) {
        const auto next = static_cast<unsigned char>(in[at + k]);
        if ((next & 0xC0U) != 0x80U) return {lead, 1U, false};
        cp = (cp << 6U) | (next & 0x3FU);
    }
    if (cp < minimum || cp > 0x10FFFFU || (cp >= 0xD800U && cp <= 0xDFFFU)) return {lead, 1U, false};
    return {cp, width, true};
}

char latin_letter(std::uint32_t cp) {
    if (cp >= 'A' && cp <= 'Z') return static_cast<char>(cp);
    if (cp >= 'a' && cp <= 'z') return static_cast<char>(cp - 32U);
    if ((cp >= 0x00C0U && cp <= 0x00C5U) || (cp >= 0x00E0U && cp <= 0x00E5U)) return 'A';
    if (cp == 0x00C7U || cp == 0x00E7U) return 'C';
    if ((cp >= 0x00C8U && cp <= 0x00CBU) || (cp >= 0x00E8U && cp <= 0x00EBU)) return 'E';
    if ((cp >= 0x00CCU && cp <= 0x00CFU) || (cp >= 0x00ECU && cp <= 0x00EFU)) return 'I';
    if (cp == 0x00D1U || cp == 0x00F1U) return 'N';
    if ((cp >= 0x00D2U && cp <= 0x00D6U) || (cp >= 0x00F2U && cp <= 0x00F6U)) return 'O';
    if ((cp >= 0x00D9U && cp <= 0x00DCU) || (cp >= 0x00F9U && cp <= 0x00FCU)) return 'U';
    if (cp == 0x00DDU || cp == 0x00FDU || cp == 0x00FFU) return 'Y';
    return '\0';
}
std::string normalize(std::string_view in) {
    std::string out;
    for (std::size_t i = 0; i < in.size();) {
        const auto d = decode_utf8(in, i);
        i += d.width;
        if (!d.valid) continue;
        const char mapped = latin_letter(d.cp);
        if (mapped) out.push_back(mapped);
        else if (d.cp == ' ' || d.cp == '\n' || d.cp == '\r' || d.cp == '\t') out.push_back(' ');
        else if (d.cp < 128U) out.push_back(static_cast<char>(d.cp));
    }
    return out;
}

std::string caesar(std::string_view in, int shift) {
    shift = (shift % 26 + 26) % 26;
    std::string out(in);
    for (char& c : out) {
        if (c >= 'A' && c <= 'Z') c = static_cast<char>('A' + (c - 'A' + shift) % 26);
        else if (c >= 'a' && c <= 'z') c = static_cast<char>('a' + (c - 'a' + shift) % 26);
    }
    return out;
}
bool parse_int(std::string_view in, int& value) {
    const auto clean = trim(in);
    if (clean.empty()) return false;
    try { std::size_t used = 0; const int n = std::stoi(clean, &used); if (used != clean.size()) return false; value = n; return true; }
    catch (...) { return false; }
}

using Morse = std::pair<char, std::string_view>;
constexpr std::array<Morse, 44> kMorse{{
 {'A',".-"},{'B',"-..."},{'C',"-.-."},{'D',"-.."},{'E',"."},{'F',"..-."},{'G',"--."},{'H',"...."},
 {'I',".."},{'J',".---"},{'K',"-.-"},{'L',".-.."},{'M',"--"},{'N',"-."},{'O',"---"},{'P',".--."},
 {'Q',"--.-"},{'R',".-."},{'S',"..."},{'T',"-"},{'U',"..-"},{'V',"...-"},{'W',".--"},{'X',"-..-"},
 {'Y',"-.--"},{'Z',"--.."},{'0',"-----"},{'1',".----"},{'2',"..---"},{'3',"...--"},{'4',"....-"},
 {'5',"....."},{'6',"-...."},{'7',"--..."},{'8',"---.."},{'9',"----."},{'.',".-.-.-"},{',',"--..--"},
 {'?',"..--.."},{'!',"-.-.--"},{'/',"-..-."},{'-',"-....-"},{'(',"-.--."},{')',"-.--.-"}
}};
std::string morse_encode(std::string_view in) {
    const auto src = normalize(in);
    std::string out;
    bool pending_word_break = false;
    for (char c : src) {
        if (c == ' ') { pending_word_break = true; continue; }
        const char u = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
        const auto it = std::find_if(kMorse.begin(), kMorse.end(), [u](const Morse& p){ return p.first == u; });
        if (it == kMorse.end()) continue;
        if (!out.empty()) out += pending_word_break ? " / " : " ";
        pending_word_break = false;
        out.append(it->second);
    }
    return out;
}
bool morse_decode(std::string_view in, std::string& out) {
    std::istringstream stream{std::string(in)}; std::string token; out.clear();
    while (stream >> token) {
        if (token == "/") { if (!out.empty() && out.back() != ' ') out.push_back(' '); continue; }
        const auto it = std::find_if(kMorse.begin(), kMorse.end(), [&token](const Morse& p){ return p.second == token; });
        if (it == kMorse.end()) return false;
        out.push_back(it->first);
    }
    while (!out.empty() && out.back() == ' ') out.pop_back();
    return true;
}

std::string vigenere(std::string_view in, std::string_view password, bool decrypt) {
    std::vector<int> key;
    for (char c : password) {
        if (c >= 'A' && c <= 'Z') key.push_back(c - 'A');
        else if (c >= 'a' && c <= 'z') key.push_back(c - 'a');
    }
    if (key.empty()) return {};
    std::string out(in); std::size_t at = 0;
    for (char& c : out) {
        const bool upper = c >= 'A' && c <= 'Z', lower = c >= 'a' && c <= 'z';
        if (!upper && !lower) continue;
        const int base = upper ? 'A' : 'a', shift = key[at++ % key.size()];
        c = static_cast<char>(base + (c - base + (decrypt ? 26 - shift : shift)) % 26);
    }
    return out;
}

bool base_convert(std::string_view number, std::string_view spec, std::string& out) {
    const auto colon = spec.find(':');
    int from = 0, to = 0;
    if (colon == std::string_view::npos || !parse_int(spec.substr(0, colon), from) ||
        !parse_int(spec.substr(colon + 1U), to) || from < 2 || from > 36 || to < 2 || to > 36) return false;
    const auto clean = trim(number);
    if (clean.empty()) return false;
    std::uint64_t value = 0;
    for (char c : clean) {
        int digit = hex_value(c);
        if (digit < 0 && std::isalpha(static_cast<unsigned char>(c))) digit = std::toupper(static_cast<unsigned char>(c)) - 'A' + 10;
        if (digit < 0 || digit >= from ||
            value > (std::numeric_limits<std::uint64_t>::max() - static_cast<std::uint64_t>(digit)) / static_cast<std::uint64_t>(from)) return false;
        value = value * static_cast<std::uint64_t>(from) + static_cast<std::uint64_t>(digit);
    }
    if (value == 0U) { out = "0"; return true; }
    out.clear();
    while (value) {
        const auto d = static_cast<unsigned int>(value % static_cast<std::uint64_t>(to));
        out.push_back(d < 10U ? static_cast<char>('0' + d) : static_cast<char>('A' + d - 10U));
        value /= static_cast<std::uint64_t>(to);
    }
    std::reverse(out.begin(), out.end());
    return true;
}

void append_utf8(std::string& out, std::uint32_t cp) {
    if (cp <= 0x7FU) out.push_back(static_cast<char>(cp));
    else if (cp <= 0x7FFU) {
        out.push_back(static_cast<char>(0xC0U | (cp >> 6U)));
        out.push_back(static_cast<char>(0x80U | (cp & 63U)));
    } else if (cp <= 0xFFFFU) {
        out.push_back(static_cast<char>(0xE0U | (cp >> 12U)));
        out.push_back(static_cast<char>(0x80U | ((cp >> 6U) & 63U)));
        out.push_back(static_cast<char>(0x80U | (cp & 63U)));
    } else {
        out.push_back(static_cast<char>(0xF0U | (cp >> 18U)));
        out.push_back(static_cast<char>(0x80U | ((cp >> 12U) & 63U)));
        out.push_back(static_cast<char>(0x80U | ((cp >> 6U) & 63U)));
        out.push_back(static_cast<char>(0x80U | (cp & 63U)));
    }
}
std::string case_change(std::string_view in, bool upper) {
    std::string out;
    for (std::size_t i = 0; i < in.size();) {
        const auto d = decode_utf8(in, i);
        if (!d.valid) { out.push_back(in[i]); i += d.width; continue; }  // keep malformed bytes untouched
        std::uint32_t cp = d.cp;
        if (upper && cp >= 'a' && cp <= 'z') cp -= 32U;
        else if (!upper && cp >= 'A' && cp <= 'Z') cp += 32U;
        else if (upper && ((cp >= 0x00E0U && cp <= 0x00F6U) || (cp >= 0x00F8U && cp <= 0x00FEU))) cp -= 32U;
        else if (!upper && ((cp >= 0x00C0U && cp <= 0x00D6U) || (cp >= 0x00D8U && cp <= 0x00DEU))) cp += 32U;
        else if (upper && cp == 0x00FFU) cp = 0x0178U;
        else if (!upper && cp == 0x0178U) cp = 0x00FFU;
        append_utf8(out, cp);
        i += d.width;
    }
    return out;
}
std::string frequency(std::string_view in) {
    std::array<unsigned int,26> counts{}; unsigned int total = 0;
    for (char c : normalize(in)) if (c >= 'A' && c <= 'Z') { ++counts[static_cast<std::size_t>(c-'A')]; ++total; }
    if (!total) return "No letters were found.";
    std::vector<std::pair<char,unsigned int>> ordered;
    for (std::size_t i=0;i<26;++i) if (counts[i]) ordered.emplace_back(static_cast<char>('A'+i),counts[i]);
    std::stable_sort(ordered.begin(),ordered.end(),[](auto a,auto b){return a.second>b.second;});
    std::ostringstream out; out<<std::fixed<<std::setprecision(1);
    for (auto p:ordered) out<<p.first<<": "<<p.second<<" ("<<(100.0*p.second/total)<<"%)\n";
    return out.str();
}
std::string statistics(std::string_view in) {
    std::size_t chars = 0, words = 0, lines = in.empty() ? 0U : 1U;
    bool word = false;
    for (std::size_t i = 0; i < in.size();) {
        const auto d = decode_utf8(in, i);
        ++chars;  // one character per valid sequence, one per malformed byte
        if (in[i] == '\n') ++lines;
        const bool space = d.valid && d.cp < 128U && std::isspace(static_cast<unsigned char>(d.cp)) != 0;
        if (!space && !word) ++words;
        word = !space;
        i += d.width;
    }
    std::ostringstream out;
    out << "Bytes: " << in.size() << "\nCharacters: " << chars << "\nWords: " << words << "\nLines: " << lines;
    return out.str();
}
std::string password_check(std::string_view password) {
    bool lower=false,upper=false,digit=false,symbol=false;
    for (unsigned char c:password) { lower|=c>='a'&&c<='z'; upper|=c>='A'&&c<='Z'; digit|=c>='0'&&c<='9'; symbol|=!std::isalnum(c)&&!std::isspace(c); }
    int score=(password.size()>=12U?2:(password.size()>=8U?1:0))+(lower?1:0)+(upper?1:0)+(digit?1:0)+(symbol?1:0);
    score=std::min(score,5);
    const char* label=score<=1?"Very weak":score==2?"Weak":score==3?"Fair":score==4?"Good":"Strong";
    std::ostringstream out; out<<label<<" ("<<score<<"/5)\n";
    if(password.size()<12U) out<<"Use at least 12 characters.\n";
    if(!lower||!upper) out<<"Mix upper- and lower-case letters.\n";
    if(!digit) out<<"Add a number.\n";
    if(!symbol) out<<"Add a symbol.";
    return out.str();
}

bool aes_encrypt(const Bytes& plain,const Bytes& password,Bytes& out) {
    if(password.empty()||password.size()>static_cast<std::size_t>(INT_MAX)||plain.size()>static_cast<std::size_t>(INT_MAX)) return false;
    out.assign(kHeader,0U); out[0]=1U;
    if(RAND_bytes(out.data()+1,16)!=1||RAND_bytes(out.data()+17,12)!=1){wipe(out);return false;}
    Bytes key(32U,0U);
    if(PKCS5_PBKDF2_HMAC(reinterpret_cast<const char*>(password.data()),static_cast<int>(password.size()),
        out.data()+1,16,kIterations,EVP_sha256(),static_cast<int>(key.size()),key.data())!=1){wipe(key);wipe(out);return false;}
    EVP_CIPHER_CTX* ctx=EVP_CIPHER_CTX_new();
    if(!ctx){wipe(key);wipe(out);return false;}
    Bytes cipher(plain.size()+16U,0U); int n=0,final_n=0;
    bool good=EVP_EncryptInit_ex(ctx,EVP_aes_256_gcm(),nullptr,nullptr,nullptr)==1 &&
        EVP_CIPHER_CTX_ctrl(ctx,EVP_CTRL_GCM_SET_IVLEN,12,nullptr)==1 &&
        EVP_EncryptInit_ex(ctx,nullptr,nullptr,key.data(),out.data()+17)==1 &&
        EVP_EncryptUpdate(ctx,nullptr,&n,out.data(),static_cast<int>(kHeader))==1;
    if(good&&!plain.empty()) good=EVP_EncryptUpdate(ctx,cipher.data(),&n,plain.data(),static_cast<int>(plain.size()))==1;
    else n=0;
    if(good) good=EVP_EncryptFinal_ex(ctx,cipher.data()+n,&final_n)==1;
    if(good) {
        cipher.resize(static_cast<std::size_t>(n+final_n));
        out.insert(out.end(),cipher.begin(),cipher.end());
        const auto tagpos=out.size(); out.resize(tagpos+kTag);
        good=EVP_CIPHER_CTX_ctrl(ctx,EVP_CTRL_GCM_GET_TAG,static_cast<int>(kTag),out.data()+tagpos)==1;
    }
    EVP_CIPHER_CTX_free(ctx); wipe(key); wipe(cipher);
    if(!good) wipe(out);
    return good;
}
bool aes_decrypt(const Bytes& data,const Bytes& password,Bytes& plain) {
    plain.clear();
    if(password.empty()||password.size()>static_cast<std::size_t>(INT_MAX)||data.size()<kHeader+kTag||data[0]!=1U) return false;
    const auto cipher_size=data.size()-kHeader-kTag;
    if(cipher_size>static_cast<std::size_t>(INT_MAX)) return false;
    Bytes key(32U,0U);
    if(PKCS5_PBKDF2_HMAC(reinterpret_cast<const char*>(password.data()),static_cast<int>(password.size()),
        data.data()+1,16,kIterations,EVP_sha256(),static_cast<int>(key.size()),key.data())!=1){wipe(key);return false;}
    EVP_CIPHER_CTX* ctx=EVP_CIPHER_CTX_new();
    if(!ctx){wipe(key);return false;}
    plain.assign(cipher_size+16U,0U); int n=0,final_n=0;
    bool good=EVP_DecryptInit_ex(ctx,EVP_aes_256_gcm(),nullptr,nullptr,nullptr)==1 &&
        EVP_CIPHER_CTX_ctrl(ctx,EVP_CTRL_GCM_SET_IVLEN,12,nullptr)==1 &&
        EVP_DecryptInit_ex(ctx,nullptr,nullptr,key.data(),data.data()+17)==1 &&
        EVP_DecryptUpdate(ctx,nullptr,&n,data.data(),static_cast<int>(kHeader))==1;
    if(good&&cipher_size) good=EVP_DecryptUpdate(ctx,plain.data(),&n,data.data()+kHeader,static_cast<int>(cipher_size))==1;
    else n=0;
    auto* tag=const_cast<std::uint8_t*>(data.data()+data.size()-kTag);
    if(good) good=EVP_CIPHER_CTX_ctrl(ctx,EVP_CTRL_GCM_SET_TAG,static_cast<int>(kTag),tag)==1;
    if(good) good=EVP_DecryptFinal_ex(ctx,plain.data()+n,&final_n)==1;
    EVP_CIPHER_CTX_free(ctx); wipe(key);
    if(!good){wipe(plain);plain.clear();return false;}
    plain.resize(static_cast<std::size_t>(n+final_n)); return true;
}
Result aes(Operation operation,const Bytes& input,const Bytes& password) {
    if(password.empty()) return error("Password is required.");
    if(operation==Operation::AesEnc||operation==Operation::AesEncRaw) {
        Bytes encrypted;
        if(!aes_encrypt(input,password,encrypted)) return error("AES encryption failed.");
        if(operation==Operation::AesEncRaw) return ok(std::move(encrypted));
        auto encoded=b64_encode(encrypted); wipe(encrypted); return ok(std::move(encoded));
    }
    Bytes serialized;
    if(operation==Operation::AesDec) {
        std::string why;
        if(!b64_decode(input,serialized,why)) return error("Unable to decrypt data. Password may be incorrect or data may be damaged.");
    } else serialized=input;
    Bytes plain; const bool good=aes_decrypt(serialized,password,plain); wipe(serialized);
    if(!good) return error("Unable to decrypt data. Password may be incorrect or data may be damaged.");
    return ok(std::move(plain));
}
bool printable(const Bytes& in) {
    if(in.empty()) return false;
    std::size_t good=0;
    for(auto c:in) if(c=='\n'||c=='\r'||c=='\t'||(c>=0x20U&&c<=0x7EU)||c>=0xC2U) ++good;
    return good*100U>=in.size()*85U;
}
std::string detect(std::string_view in) {
    const Bytes source=bytes(in); Bytes decoded; std::string why; std::vector<std::string> candidates;
    if(!in.empty()&&b64_decode(source,decoded,why)&&printable(decoded)) candidates.push_back("Base64 -> "+text(decoded));
    if(!in.empty()&&hex_decode(in,decoded,why)&&printable(decoded)) candidates.push_back("Hexadecimal -> "+text(decoded));
    if(!in.empty()&&binary_decode(in,decoded,why)&&printable(decoded)) candidates.push_back("Binary -> "+text(decoded));
    std::string morse;
    if(!in.empty()&&morse_decode(in,morse)&&!morse.empty()) candidates.push_back("Morse -> "+morse);
    if(candidates.empty()) return "No known encoding was detected.";
    std::ostringstream out; out<<"Possible formats:\n";
    for(const auto& item:candidates) out<<"- "<<item<<"\n";
    return out.str();
}
} // namespace

Result execute(Operation op,const Bytes& input,const Bytes& argument1,const Bytes& argument2) {
    (void)argument2;
    std::string source=text(input), arg=text(argument1);
    StringWiper wipe_source{source};
    StringWiper wipe_arg{arg};
    switch(op) {
        case Operation::BinEnc: return ok(binary_encode(input));
        case Operation::BinDec: { Bytes out; std::string e; return binary_decode(source,out,e)?ok(std::move(out)):error(e); }
        case Operation::HexEnc: return ok(hex_encode(input));
        case Operation::HexDec: { Bytes out; std::string e; return hex_decode(source,out,e)?ok(std::move(out)):error(e); }
        case Operation::B64Enc: return ok(b64_encode(input));
        case Operation::B64Dec: { Bytes out; std::string e; return b64_decode(input,out,e)?ok(std::move(out)):error(e); }
        case Operation::MorseEnc: return ok(morse_encode(source));
        case Operation::MorseDec: { std::string out; return morse_decode(source,out)?ok(out):error("Invalid Morse code."); }
        case Operation::Caesar: { int shift=0; if(!parse_int(arg,shift)) return error("Enter a valid Caesar shift."); return ok(caesar(source,shift)); }
        case Operation::CaesarBrute: { std::ostringstream out; for(int s=1;s<=25;++s) out<<"Shift "<<s<<": "<<caesar(source,-s)<<"\n"; return ok(out.str()); }
        case Operation::Rot13: return ok(caesar(source,13));
        case Operation::Atbash: { std::string out=source; for(char& c:out){if(c>='A'&&c<='Z')c=static_cast<char>('Z'-(c-'A'));else if(c>='a'&&c<='z')c=static_cast<char>('z'-(c-'a'));} return ok(out); }
        case Operation::VigenereEnc: case Operation::VigenereDec: {
            const auto key=std::find_if(arg.begin(),arg.end(),[](char c){return (c>='A'&&c<='Z')||(c>='a'&&c<='z');});
            if(key==arg.end()) return error("Password must contain at least one A-Z letter.");
            return ok(vigenere(source,arg,op==Operation::VigenereDec));
        }
        case Operation::XorEnc: case Operation::XorDec: {
            if(argument1.empty()) return error("Password is required.");
            Bytes data;
            if(op==Operation::XorDec){std::string e;if(!hex_decode(source,data,e))return error(e);} else data=input;
            for(std::size_t i=0;i<data.size();++i)data[i]^=argument1[i%argument1.size()];
            if(op==Operation::XorEnc)return ok(hex_encode(data));
            return ok(std::move(data));
        }
        case Operation::AesEnc: case Operation::AesDec:
        case Operation::AesEncRaw: case Operation::AesDecRaw: return aes(op,input,argument1);
        case Operation::AutoDetect: return ok(detect(source));
        case Operation::Reverse: { std::vector<std::string_view> chars; for(std::size_t i=0;i<source.size();){std::size_t n=1;unsigned char c=static_cast<unsigned char>(source[i]);if((c&0xE0)==0xC0)n=2;else if((c&0xF0)==0xE0)n=3;else if((c&0xF8)==0xF0)n=4;n=std::min(n,source.size()-i);chars.push_back(std::string_view(source).substr(i,n));i+=n;}std::string out;for(auto it=chars.rbegin();it!=chars.rend();++it)out.append(*it);return ok(out); }
        case Operation::Upper: return ok(case_change(source,true));
        case Operation::Lower: return ok(case_change(source,false));
        case Operation::Stats: return ok(statistics(source));
        case Operation::Freq: return ok(frequency(source));
        case Operation::BaseConvert: {std::string out;if(!base_convert(source,arg,out))return error("Invalid value or base. Choose bases from 2 to 36; numbers are limited to 64 bits.");return ok(out);}
        case Operation::PasswordCheck: return ok(password_check(source));
        default: return error("Unknown CipherPocket operation.");
    }
}
} // namespace cp
