add_library(cp_engine STATIC
    src/engine.cpp
)

target_include_directories(cp_engine
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

set_target_properties(cp_engine PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

target_compile_options(cp_engine PRIVATE
    -Wall
    -Wextra
    -Wpedantic
)

if(ANDROID AND DEFINED CP_OPENSSL_ROOT)
    set(CP_OPENSSL_ABI_DIR "${CP_OPENSSL_ROOT}/${ANDROID_ABI}")
    if(EXISTS "${CP_OPENSSL_ABI_DIR}/lib/libcrypto.a")
        add_library(cp_openssl_crypto STATIC IMPORTED GLOBAL)
        set_target_properties(cp_openssl_crypto PROPERTIES
            IMPORTED_LOCATION "${CP_OPENSSL_ABI_DIR}/lib/libcrypto.a"
            INTERFACE_INCLUDE_DIRECTORIES "${CP_OPENSSL_ABI_DIR}/include"
        )
        target_link_libraries(cp_engine PUBLIC cp_openssl_crypto)
    endif()
endif()
