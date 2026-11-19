#include <cstdint>
#include <iostream>
#include <iterator>
#include <string>
#include <vector>

#include "cp/engine.hpp"

namespace {

void print_usage(const char* program) {
    std::cerr
        << "CipherPocket native CLI (M0)\n"
        << "Usage: " << program << " <b64-encode|b64-decode> [text]\n"
        << "If text is omitted, input is read from standard input.\n";
}

}  // namespace

int main(int argc, char** argv) {
    if (argc < 2 || argc > 3) {
        print_usage(argv[0]);
        return 2;
    }

    const std::string command = argv[1];
    cp::Operation operation;
    if (command == "b64-encode") {
        operation = cp::Operation::B64Enc;
    } else if (command == "b64-decode") {
        operation = cp::Operation::B64Dec;
    } else {
        print_usage(argv[0]);
        return 2;
    }

    std::string input;
    if (argc == 3) {
        input = argv[2];
    } else {
        input.assign(std::istreambuf_iterator<char>(std::cin), {});
    }

    const std::vector<std::uint8_t> bytes(input.begin(), input.end());
    const auto result = cp::execute(operation, bytes);
    if (!result.ok) {
        std::cerr << std::string(result.output.begin(), result.output.end()) << '\n';
        return 1;
    }

    std::cout.write(
        reinterpret_cast<const char*>(result.output.data()),
        static_cast<std::streamsize>(result.output.size())
    );
    std::cout << '\n';
    return 0;
}
