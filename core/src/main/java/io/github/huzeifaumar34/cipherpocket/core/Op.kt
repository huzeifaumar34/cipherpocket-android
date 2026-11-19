package io.github.huzeifaumar34.cipherpocket.core

/**
 * Operation IDs are part of the Kotlin/C++ wire contract.
 * Keep these values synchronized with cp::Operation in engine.hpp.
 */
enum class Op(val id: Int) {
    BIN_ENC(1),
    BIN_DEC(2),
    HEX_ENC(3),
    HEX_DEC(4),
    B64_ENC(5),
    B64_DEC(6),
    MORSE_ENC(7),
    MORSE_DEC(8),
    CAESAR(9),
    CAESAR_BRUTE(10),
    ROT13(11),
    ATBASH(12),
    VIGENERE_ENC(13),
    VIGENERE_DEC(14),
    XOR_ENC(15),
    XOR_DEC(16),
    AES_ENC(17),
    AES_DEC(18),
    AUTO_DETECT(19),
    REVERSE(20),
    UPPER(21),
    LOWER(22),
    STATS(23),
    FREQ(24),
    BASE_CONVERT(25),
    PASSWORD_CHECK(26),
    AES_ENC_RAW(27),
    AES_DEC_RAW(28)
}
