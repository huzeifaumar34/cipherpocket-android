# CipherPocket — Android app (Kotlin + C++)

> Versão 2 do documento · 2026-11-19 · Estado: **especificação** (nada foi compilado nem escrito em código)
>
> Este documento só **define**: decisões, regras, formatos, processos e critérios. O código vem depois, seguindo isto.

CipherPocket é a versão Android do `conversor` (programa C++ de terminal): um canivete suíço **offline** de bolso para codificar, decifrar e cifrar texto e ficheiros. A interface é em Kotlin (Jetpack Compose), o motor é em C++17 (chamado por JNI), o build corre no GitHub Actions e o APK sai assinado.

**Sobre o nome.** "CipherPocket" quer dizer "cifras de bolso": cabe no telemóvel e funciona sem rede. É inglês, descreve a função e é fácil de dizer. Antes de publicar, convém procurar no GitHub e nas lojas se já existe um app com o mesmo nome.

---

## 1. Visão geral

| | |
|---|---|
| **Nome** | CipherPocket |
| **Repositório** | `cipherpocket-android` |
| **applicationId / namespace** | `io.github.huzeifaumar34.cipherpocket` (fixar **antes** da primeira instalação real: depois de publicado não muda) |
| **Prefixo interno** | `CP` (variáveis, segredos do CI, namespace C++, nome da biblioteca nativa) |
| **Licença** | MIT para o código próprio + avisos de terceiros (OpenSSL, Apache-2.0) dentro da app |
| **Idiomas da UI** | Inglês (padrão) e Português |
| **Dispositivo de teste** | Infinix Smart 7 HD (Android 12 Go, Unisoc SC9863A1) |

### Princípios

1. **Offline total.** A app não declara a permissão `INTERNET`. O CI falha se ela aparecer.
2. **Sem criptografia feita à mão.** AES-256-GCM e PBKDF2 vêm do OpenSSL; o código próprio só os chama.
3. **Mesmo motor no PC e no telemóvel.** O motor C++ compila também como programa de terminal e como testes no PC. O formato cifrado é idêntico ao do `conversor` v2.
4. **Privacidade por defeito.** Sem histórico gravado, sem backup na nuvem, ecrã protegido nas telas de senha.
5. **Honestidade na UI.** Cifras clássicas são marcadas como "para brincar, não protegem segredos".

### Não-objetivos (v1)

Sincronização na nuvem, contas, anúncios, gestor de senhas, e cifra de ficheiros maiores que o limite da v1 (ver roadmap).

---

## 2. Decisões de arquitetura

| Tema | Decisão | Porquê |
|---|---|---|
| UI | Jetpack Compose + Material 3 (cor dinâmica no Android 12+) | Menos código que Views; o telemóvel de teste é Android 12 |
| Linguagem da UI | Kotlin 2.4.x | Pedido; o suporte a Kotlin já vem embutido no AGP 9 |
| Motor | C++17 puro, sem JNI lá dentro | Testável no PC, reutilizável no programa de terminal |
| Ponte | JNI só com **ByteArray**, nunca String | A conversão de String do JNI usa "modified UTF-8" e corrompe emojis (4 bytes) |
| Cripto | OpenSSL 3.5.x LTS compilado em estático, uma cópia por ABI | Velocidade (PBKDF2 com 600 000 iterações) e formato idêntico ao do PC |
| Estado | ViewModel + StateFlow; histórico só em memória | Privacidade |
| Navegação | `navigation-compose` | Poucas telas |
| minSdk / targetSdk | 26 / 36 | Cobre o Android 12 Go com folga |
| ABIs | `armeabi-v7a`, `arm64-v8a`, `x86_64` | Telemóveis "Go" costumam correr userland de 32 bits mesmo com CPU de 64; `x86_64` serve para emulador |
| Distribuição | APK universal assinado no GitHub Releases | Instalação direta (sideload) |
| Build | Gradle (Kotlin DSL) + version catalog | Padrão atual |

**Verificar no telemóvel** qual ABI ele usa, lendo a propriedade `ro.product.cpu.abilist` via `adb shell getprop`. Se aparecer só `armeabi-v7a`, o build de 32 bits é obrigatório (já está incluído).

---

## 3. Versões fixadas

| Componente | Versão | Estado |
|---|---|---|
| Android Gradle Plugin (AGP) | **9.5.0** | ✔ confirmada na documentação do Android |
| Gradle | **9.6.0** (mínimo exigido pelo AGP 9.5) | ✔ |
| JDK | **17** (Temurin; mínimo do AGP 9.5) | ✔ |
| SDK Build Tools | **36.0.0** | ✔ |
| compileSdk / targetSdk | **36** (o AGP 9.5 aceita até 37) | ✔ |
| NDK | **28.2.13676358** (padrão do AGP 9.5; gera ELF alinhado a 16 KB) | ✔ |
| CMake (SDK) | **3.22.1** | ✔ pacote oficial do SDK Manager |
| Kotlin | **2.4.21** | ✔ |
| Compose UI / Material 3 | **1.13.0 / 1.5.0** (estáveis) | ✔ |
| Compose BOM | — | ⚠ não usado; fixamos as bibliotecas diretamente |
| OpenSSL | **3.5.x LTS** | ⚠ escolher a última 3.5.x e gravar o SHA-256 |
| Ações do GitHub | `checkout`, `setup-java`, `cache`, `upload-artifact`, `gradle/actions/*` na major **v4** | ⚠ confirmar se há major mais recente |
| Restantes `androidx.*` | valores mínimos válidos no catálogo | ⚠ o Dependabot atualiza |

O NDK r30 já saiu, mas **não** é o padrão do AGP 9.5. Ficamos na r28.2 até haver motivo para subir.

---

## 4. Funcionalidades

### 4.1 Mapa do `conversor` v2 → app

| # | Operação | Ecrã | Notas |
|---|---|---|---|
| 1–2 | Texto ↔ Binário | Tools | |
| 3–4 | Texto ↔ Hexadecimal | Tools | |
| 5–6 | Texto ↔ Base64 | Tools | |
| 7–8 | Texto ↔ Morse | Tools | Com acentos; á/à/â/ã partilham o mesmo código |
| 9–10 | César e César por força bruta | Tools | 25 deslocamentos em lista |
| 11–12 | ROT13 e Atbash | Tools | |
| 13–14 | Vigenère (cifrar/decifrar) | Tools | Só as letras A–Z da senha contam |
| 15–16 | XOR com senha (hex) | Tools | Aviso "só para brincar" |
| 17–18 | **AES-256-GCM** | **Vault** | Senha com avisos de força; saída em Base64 |
| 19 | Detetar formato | **Detect** | Também recebe texto partilhado (Share) |
| 20–24 | Inverter, maiúsculas, minúsculas, estatísticas, frequência de letras | Tools | Acentos tratados |
| 25 | Conversor de bases 2–36 | Tools | Até 64 bits |
| 26 | Ficheiros: Base64, Hex, AES | **Files** | Seletor do sistema (SAF), sem permissão de armazenamento |

### 4.2 Telas

1. **Home** — grelha de ferramentas por categoria + atalho para Detect.
2. **Tool** — entrada, parâmetros (deslocamento, senha, bases), saída; botões Copiar, Partilhar e Trocar (saída → entrada).
3. **Detect** — cola uma mensagem; mostra os formatos prováveis e o texto decifrado. Abre também via "Partilhar → CipherPocket".
4. **Vault** — AES com senha (pedida duas vezes ao cifrar), indicador de força, aviso para senhas fracas com "Continuar mesmo assim".
5. **Files** — escolher ficheiro → operação → guardar com o seletor do sistema. **Limite inicial de 10 MB** (ver §11); só sobe depois de medido no telemóvel de teste.
6. **History** — da sessão atual, em memória, com botão "Limpar".
7. **Settings** — tema, idioma, tempo para limpar a área de transferência (padrão 30 s), licenças open source.

---

## 5. Arquitetura

### 5.1 Camadas (de cima para baixo)

| Camada | Conteúdo | Regra |
|---|---|---|
| **app** (Kotlin, Compose) | Telas, ViewModels, casos de uso | Só fala com o `Engine` |
| **core** (biblioteca Android) | `Engine` (corrotinas, thread de fundo) e `NativeBridge` (funções externas) | Nunca chama o motor na thread principal |
| **JNI** (C++) | Uma única função de entrada | Apanha **todas** as exceções C++ e devolve-as como erro |
| **engine** (C++17 puro) | codec, ciphers, morse, detect, text, bases, aes, dispatch | Sem dependência de Android |
| **OpenSSL** (`libcrypto` estática) | Uma cópia por ABI | Escondida: não exporta símbolos |

### 5.2 Contrato da ponte

- A ponte tem duas funções: **versão da API** e **executar** (operação, entrada, argumento 1, argumento 2).
- Tudo vai como bytes UTF-8 em `ByteArray`. Ficheiros e saídas binárias usam o mesmo caminho.
- A resposta é um `ByteArray`: o **primeiro byte é o estado** (0 = OK, 1 = erro) e o resto é o resultado, ou a mensagem de erro em UTF-8.
- Os argumentos dependem da operação (deslocamento, senha, "10:2" para bases). Os que não se usam vão vazios.
- Nenhuma exceção C++ atravessa o JNI.
- O PBKDF2 não é cancelável a meio: a UI mostra progresso indeterminado.
- **Senhas e temporários:** qualquer cópia de senha feita no lado nativo (incluindo a que o JNI cria ao converter o argumento) é **zerada** assim que a operação termina. No lado Kotlin, a senha vive em `CharArray`/`ByteArray` e é zerada depois de usada.

### 5.3 Ids das operações

Iguais em Kotlin e C++; um teste confere os dois.

| Id | Operação | Id | Operação | Id | Operação |
|---|---|---|---|---|---|
| 1 | BIN_ENC | 10 | CAESAR_BRUTE | 19 | AUTO_DETECT |
| 2 | BIN_DEC | 11 | ROT13 | 20 | REVERSE |
| 3 | HEX_ENC | 12 | ATBASH | 21 | UPPER |
| 4 | HEX_DEC | 13 | VIGENERE_ENC | 22 | LOWER |
| 5 | B64_ENC | 14 | VIGENERE_DEC | 23 | STATS |
| 6 | B64_DEC | 15 | XOR_ENC | 24 | FREQ |
| 7 | MORSE_ENC | 16 | XOR_DEC | 25 | BASE_CONVERT |
| 8 | MORSE_DEC | 17 | AES_ENC (saída Base64) | 26 | PASSWORD_CHECK |
| 9 | CAESAR | 18 | AES_DEC (entrada Base64) | 27 / 28 | AES_ENC_RAW / AES_DEC_RAW (bytes) |

### 5.4 Formato AES (idêntico ao `conversor` v2)

| Campo | Tamanho |
|---|---|
| Versão (= 1) | 1 byte |
| Sal aleatório | 16 bytes |
| IV aleatório | 12 bytes |
| Texto cifrado | variável |
| Etiqueta GCM | 16 bytes |

Em texto, o conjunto é codificado em Base64.

- Chave de 256 bits = PBKDF2-HMAC-SHA256 da senha, com o sal acima e **600 000** iterações.
- O IV é aleatório e nunca reutilizado. Como o sal também muda a cada cifra, a chave muda a cada mensagem.
- O cabeçalho (versão + sal + IV) entra como dados autenticados (AAD): alterar qualquer byte faz a decifra falhar.
- Senha errada e dados adulterados dão a **mesma** mensagem de erro.
- **Vetor de teste** (gerado pelo `conversor` v2, deve decifrar na app):
  - Senha: `Frase-Longa-e-Segura-2026!`
  - Texto cifrado: `ARc3Kn4rIjbuTO+zEUqTiMvEGmtOBKCpDWd9KeUIq5MOIp1yGOVj36qE68BJHSVLew==`
  - Texto claro: `ZIKA`
- **Formato versão 2 (reserva):** se o PBKDF2 passar de ~3 s no telemóvel de teste, cria-se um formato com o byte de versão = 2 e menos iterações. Nesse caso o `conversor` de PC, o script de compatibilidade e os testes **têm de passar a entender a versão 2 ao mesmo tempo**, para não quebrar o princípio 3.

---

## 6. Estrutura do repositório

Pasta raiz: `cipherpocket-android/`

- **`.github/`**
  - `actions/prepare-android/` — passos partilhados (JDK, Gradle, SDK, OpenSSL)
  - `workflows/` — `ci` e `release`
  - `dependabot.yml`
- **`app/`** — Kotlin + Compose: configuração de build, regras do R8, recursos (incluindo as **regras de extração de dados vazias** referenciadas pelo manifest), código principal, testes unitários e testes de instrumentação
- **`core/`** — biblioteca: Kotlin (`Engine`, `NativeBridge`, `Op`) e C++ (build Android, JNI, motor com cabeçalhos públicos)
- **`native/`** — build no PC: testes (GoogleTest) e o programa de terminal sobre o motor
- **`third_party/openssl/`** — versão, SHA-256 e saída compilada (a saída não vai para o Git)
- **`scripts/`** — compilar o OpenSSL, gerar a chave de assinatura, teste de compatibilidade do AES em Python
- **`gradle/`** — catálogo de versões e wrapper
- **Ficheiros da raiz** — configuração do Gradle, licença, changelog, este documento e o `.gitignore` (que exclui `*.jks`, `local.properties`, pastas de build e a saída do OpenSSL)

---

## 7. Definições de build (Gradle, NDK, CMake)

> Nada disto foi compilado. Se o primeiro sync reclamar da DSL do AGP 9, usar o *Upgrade Assistant* do Android Studio; a válvula de escape é desligar a nova DSL nas propriedades do Gradle (enquanto o AGP 9 permitir).

**Módulos:** `:app` (aplicação) e `:core` (biblioteca).

**Plugins:** AGP para aplicação e biblioteca; o plugin do compilador do Compose (versão igual à do Kotlin) só no `:app`. **Não há plugin `kotlin-android`**: o AGP 9 compila Kotlin sozinho.

**`:core`**
- namespace `io.github.huzeifaumar34.cipherpocket.core`; compileSdk 36; minSdk 26
- NDK 28.2.13676358; CMake 3.22.1
- ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64`
- STL do C++ estático; recebe do Gradle o caminho da pasta do OpenSSL compilado
- Dependências: corrotinas do Android; JUnit nos testes

**`:app`**
- applicationId e namespace `io.github.huzeifaumar34.cipherpocket`; compileSdk/targetSdk 36; minSdk 26; build tools 36.0.0; Java 17
- `versionName` e `versionCode` vêm de propriedades passadas pelo CI (por omissão: `0.1.0-dev` e `1`)
- **Release:** R8 e redução de recursos ligados. A assinatura só é aplicada quando as variáveis de ambiente do keystore existem (CI de release ou build local assinado)
- Regra do R8: não renomear a classe `NativeBridge`, porque o C++ procura os métodos pelo nome
- Dependências: core, activity-compose, lifecycle (viewmodel e runtime), navigation-compose, compose ui/foundation/material3, tooling só em debug

**`gradle.properties`:** memória do daemon de ~3 GB, UTF-8, cache do Gradle ligado, AndroidX ligado.

**`settings`:** repositórios apenas `google()` e `mavenCentral()`; projetos não podem declarar repositórios próprios.

**Manifest (pontos que importam)**
- **Nenhum** `uses-permission`. Em especial, sem `INTERNET`.
- `allowBackup=false` + regras de extração de dados vazias.
- Atividade principal exportada, com o filtro de lançamento e o filtro "Partilhar texto simples → abre o Detect".

**Build nativo (Android)**
- C++17 sem extensões. O motor é uma biblioteca estática; a ponte JNI é a única biblioteca partilhada (`cipherpocket_jni`).
- A ponte é compilada com avisos ligados e visibilidade escondida, e linkada com **página de 16 KB** (exigência do Android 15+ / Google Play).
- **Esconder os símbolos do OpenSSL:** linkar a biblioteca partilhada com a opção que impede a exportação de símbolos das bibliotecas estáticas (`--exclude-libs,ALL`). Assim a `.so` fica menor e não expõe a API do OpenSSL.
- No Android, o motor usa a `libcrypto.a` da pasta da ABI atual. No PC, usa o OpenSSL 3.0 ou mais novo do sistema.

---

## 8. OpenSSL estático

Script: compila a `libcrypto.a` para as 3 ABIs com o NDK. Requisitos:

- **Entradas fixas:** a versão (só o número, ex.: 3.5.x) e o SHA-256 do `.tar.gz`, tirado da página oficial de lançamentos. **O CI recusa-se a compilar se o hash não bater.**
- **Nível de API 26** em todas as ABIs.
- **Opções:** sem bibliotecas partilhadas, sem testes, sem apps, sem SSLv3, sem compressão, sem DSO, sem engines.
- **Código independente de posição (`-fPIC`) explícito.** Com "sem partilhadas", o OpenSSL pode não o ativar, e a ligação à `.so` falharia com erro de relocação (sobretudo em `x86_64` e `armeabi-v7a`).
- **Saída:** só cabeçalhos e `libcrypto.a`, uma pasta por ABI.
- **Toolchain:** LLVM do NDK, na máquina Linux x86_64 do CI.

---

## 9. Assinatura do APK

### 9.1 Criar a chave (uma vez, no teu computador)

- Keystore PKCS12, alias `cipherpocket`, RSA 4096, validade de 10 000 dias.
- O `.jks` **nunca** entra no Git.
- **Faz duas cópias de segurança** (por exemplo, pen drive e gestor de senhas). Se perderes a chave ou a senha, nenhuma atualização futura consegue assinar sobre as instalações existentes: os utilizadores teriam de desinstalar a app.
- Em PKCS12 a senha da chave é a mesma do keystore.
- Anota a impressão digital SHA-256 do certificado, que o CI vai comparar.

### 9.2 Guardar no GitHub

Criar um *Environment* chamado `release` (com aprovação obrigatória) contendo:

| Tipo | Nome | Conteúdo |
|---|---|---|
| Secret | `CP_KEYSTORE_B64` | keystore em Base64 numa linha só |
| Secret | `CP_KEYSTORE_PASSWORD` | senha do keystore |
| Secret | `CP_KEY_ALIAS` | `cipherpocket` |
| Secret | `CP_KEY_PASSWORD` | igual à do keystore |
| Variable | `CP_CERT_SHA256` | impressão digital SHA-256 (hex, sem `:`) |

### 9.3 Esquemas de assinatura

Com minSdk 26, o AGP assina com v2/v3 e dispensa o v1. Nada a configurar. No fim, o CI confirma a assinatura e compara o certificado com `CP_CERT_SHA256`.

### 9.4 Build assinado local (opcional)

Definir as variáveis de ambiente do keystore (caminho, senha, alias, senha da chave) e correr o assemble de release.

---

## 10. CI/CD (GitHub Actions)

### 10.1 Passos partilhados (`prepare-android`)

1. JDK 17 (Temurin).
2. Configuração do Gradle.
3. Aceitar licenças e instalar no SDK: plataforma 36, build tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1.
4. Cache do OpenSSL por ABI. **A chave do cache inclui:** o script, o ficheiro de versão, o de SHA-256 **e a versão do NDK**.
5. Se não houver cache, compilar o OpenSSL.

### 10.2 Workflow `ci` (push no `main` e pull requests)

- Cancela execuções antigas do mesmo ramo. Permissões mínimas (só leitura).
- **Job `native`:** configurar, compilar e correr os testes do motor no PC (`ctest`).
  - *Decisão v1:* usa o OpenSSL 3.0 do Ubuntu, enquanto o Android usa o 3.5. O formato é o mesmo; a diferença é coberta pelo teste de compatibilidade em Python. Se houver divergência, o job passa a compilar o mesmo tarball fixado.
- **Job `android`** (depende do `native`): validar o wrapper do Gradle → preparar → lint + testes unitários → APK debug → **falhar se a permissão `INTERNET` aparecer** → guardar o APK debug como artefacto.

### 10.3 Workflow `release` (tag `vX.Y.Z`)

1. **Só continua se o commit da tag estiver no ramo `main`.** Caso contrário, falha.
2. Corre no Environment `release` (aprovação manual).
3. Validar o wrapper → preparar.
4. Calcular a versão a partir da tag: `versionName` = `X.Y.Z`; `versionCode` = X×10000 + Y×100 + Z (ex.: 1.2.3 → 10203). É sempre crescente enquanto a versão subir.
5. Decodificar o keystore para um ficheiro temporário (falha se o segredo faltar).
6. Build de release assinado.
7. **Verificações:** assinatura válida; certificado igual a `CP_CERT_SHA256`; alinhamento a 16 KB; **sem `INTERNET`**.
8. Nomear o APK `CipherPocket-X.Y.Z.apk` e gerar o ficheiro de SHA-256.
9. Publicar a GitHub Release com notas automáticas.
10. **Sempre**, mesmo em falha: apagar o keystore temporário.

Para lançar: criar a tag e enviá-la ao GitHub.

### 10.4 Dependabot

Atualização semanal de dependências Gradle e de ações do GitHub.
**Endurecimento opcional:** fixar cada ação pelo SHA completo do commit (o Dependabot continua a atualizar).

---

## 11. Segurança e privacidade

| Tema | Regra |
|---|---|
| Rede | Sem permissão `INTERNET`; verificada no CI (debug e release) |
| Backup | `allowBackup=false` + regras de extração vazias |
| Capturas de ecrã | Proteção contra captura nas telas Vault e Files |
| Senhas | Campo mascarado, autofill desligado; `CharArray`/`ByteArray` zerados depois de usar, **incluindo as cópias temporárias no JNI** (§5.2) |
| Área de transferência | Marcar como sensível (Android 13+) e limpar após o tempo definido |
| Histórico | Só em memória; botão "Limpar"; nunca gravado em disco |
| Erros de AES | Mensagem única para "senha errada" e "dados adulterados" |
| Senhas fracas | Aviso na hora de cifrar; nunca bloqueia |
| Cifras clássicas | Marcadas como "para brincar, não protegem segredos" |
| Dependências | Dependabot + validação do wrapper + hash do tarball do OpenSSL |
| Releases | Assinatura verificada contra impressão digital fixa; só a partir de tags no `main` |
| Visibilidade nativa | Símbolos do OpenSSL não exportados pela `.so` |

### Memória e tamanho de ficheiros

- Um ficheiro passa por várias cópias: `ByteArray` (heap Java), cópia nativa, saída, e Base64 (+33%) quando se usa o caminho de texto. Em Android Go o heap Java pode ser de 128–192 MB.
- **Regras:** limite inicial de **10 MB**; ficheiros usam as operações **RAW** (27/28), sem Base64; só subir o limite depois de medir o consumo no Smart 7 HD.

---

## 12. Testes

| Camada | Ferramenta | O que cobre |
|---|---|---|
| Motor C++ (PC) | GoogleTest + `ctest` | Vetores conhecidos de todas as operações (ex.: "Quantos corações tem um polvo?" → `UXVhbnRvcyBjb3Jhw6fDtWVzIHRlbSB1bSBwb2x2bz8=`), acentos, ida e volta de AES, senha errada, byte adulterado, o vetor da §5.4 |
| Compatibilidade | Script Python (biblioteca `cryptography`) | O C++ decifra o que o Python cifra e vice-versa |
| Kotlin | JUnit | Ids de `Op` iguais ao enum C++; mapeamento do protocolo estado + resultado |
| App | Testes de UI do Compose | Fluxos principais, no emulador (fase 4) |
| Manual | Smart 7 HD | Tempo do AES (600 000 iterações); consumo de memória com 10 MB |

---

## 13. Roadmap

| Fase | Entrega | Critério de aceitação |
|---|---|---|
| **M0 — Andaimes** | Repo, Gradle, módulos, motor com uma operação, CI verde | O `ci` passa; o APK debug instala e mostra Base64 a funcionar |
| **M1 — Motor completo** | Operações 1–26 em C++ com testes; programa de terminal sobre o motor | `ctest` verde; terminal igual ao `conversor` v2 nos vetores |
| **M2 — App de texto** | Home, Tool, Detect, History, Settings, i18n EN/PT | Todas as ferramentas de texto na app; Share → Detect funciona |
| **M3 — Vault e ficheiros** | AES com senha, Files via seletor do sistema, proteção de ecrã, limpeza da área de transferência | O vetor da §5.4 decifra na app; ficheiro de **10 MB** ida e volta sem erro |
| **M4 — Release 1.0** | Ícone, licenças, chave de assinatura, workflow de release | A tag `v1.0.0` publica APK assinado; assinatura e alinhamento de 16 KB passam |
| **M5 — Depois** | AES em *streaming* para ficheiros grandes, subir o limite de ficheiros, atalho no ecrã inicial, F-Droid/Play | — |

---

## 14. Pontos a confirmar no primeiro build

1. **DSL do AGP 9:** os blocos de configuração do Android (incluindo o build nativo) compilam sem desligar a nova DSL? Se não, usar o Upgrade Assistant.
2. **Plugin do Compose:** a documentação do Kotlin embutido não o menciona; mantemo-lo aplicado, com a versão igual à do Kotlin.
3. **Ações do GitHub:** confirmar se há major mais recente que a v4 e se suporta o Gradle 9.6.
4. **OpenSSL:** fixar a última 3.5.x e o SHA-256; o script segue as notas oficiais para Android mas **ainda não foi corrido**. Confirmar que o `-fPIC` resolve a ligação nas 3 ABIs.
5. **Símbolos:** confirmar que a `.so` final não exporta nada do OpenSSL.
6. **PBKDF2 no Unisoc SC9863A1:** se passar de ~3 s, avançar para o formato versão 2 (§5.4).
7. **Memória:** medir o consumo real com ficheiros de 10 MB e decidir se o limite sobe.
8. **ABI do telemóvel** (`armeabi-v7a` vs `arm64-v8a`).
9. **Nome:** procurar conflitos de "CipherPocket" antes de fixar o applicationId.

---

## 15. Checklist de arranque

- [ ] Procurar conflitos do nome "CipherPocket" (GitHub e lojas)
- [ ] Criar o repositório `cipherpocket-android` no GitHub
- [ ] Gerar e commitar o wrapper do Gradle 9.6.0
- [ ] Criar os ficheiros descritos nas secções 6 a 8 e 10
- [ ] Preencher a versão e o SHA-256 do OpenSSL
- [ ] Mover o código do `conversor` (v2 como base) para o motor
- [ ] Abrir no Android Studio e fazer o primeiro sync
- [ ] Fazer push e ver o `ci` passar
- [ ] Gerar a chave (§9.1), criar o Environment `release` com os segredos e a variável
- [ ] Marcar `v0.1.0` para testar o workflow de release antes da 1.0

---

## 16. O que mudou em relação à versão 1

| Mudança | Onde |
|---|---|
| Nome do app: CipherKit → **CipherPocket**; prefixo interno `CK` → `CP` | Todo o documento |
| Documento passa a conter **só definições**, sem blocos de código | Todo o documento |
| OpenSSL com `-fPIC` explícito | §8 |
| Símbolos do OpenSSL escondidos na `.so` | §7, §11 |
| Limite de ficheiros de 25 MB → **10 MB** inicial, com RAW e medição | §4.2, §11, §13 |
| Cópias temporárias de senha no JNI são zeradas | §5.2, §11 |
| Release só a partir de tags no `main` | §10.3 |
| Cache do OpenSSL inclui a versão do NDK | §10.1 |
| Diferença OpenSSL 3.0 (PC) vs 3.5 (Android) registada como decisão | §10.2 |
| Regras de extração de dados vazias listadas na estrutura | §6 |
| Formato AES versão 2 exige atualizar o PC e os testes juntos | §5.4 |