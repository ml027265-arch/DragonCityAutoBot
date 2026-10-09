# DragonCity AutoBot

Aplicativo experimental em Kotlin para Android 11+ (minSdk 30), compilado com compileSdk/targetSdk 36 para Android 16. Não exige root. O Samsung Galaxy A34 5G usa o APK `app-arm64-v8a-debug.apk`; o APK x86_64 é destinado ao emulador de testes.

## Implementação

- ENSINAR captura a tela antes de cada toque, encaminha um gesto de acessibilidade e captura/verifica a mudança depois. Até 12 ações e 90 segundos por sessão.
- Demonstração versionada vinculada ao pacote selecionado: recortes visuais, captura completa para revisão, posição relativa, deslocamento do ponto dentro do recorte e assinaturas das telas anteriores/posteriores. Armazenamento privado com substituição atômica; backups desabilitados.
- Cada ação precisa ser revisada como gratuita antes do EXECUTAR. A revisão mostra a tela completa e marca o ponto em vermelho.
- EXECUTAR procura o alvo perto da posição ensinada, adapta o ponto ao deslocamento reconhecido e à escala da resolução com proporção compatível. Exige contexto visual anterior e resultado posterior correspondentes.
- OCR latino incluído no APK e leitura de acessibilidade bloqueiam controles de compras/gemas/lojas próximos ao toque. Pedidos explícitos de compra/gasto bloqueiam a tela inteira. Rótulos de navegação distantes como LOJA/OFERTAS não impedem uma ação gratuita. Falha no OCR também interrompe.
- PARAR disponível sobre o jogo durante ambos os modos. Sem repetição de gesto recusado, cancelado, sem confirmação ou com resultado inesperado. Cancelamento invalida callbacks antigos.

Não há decisão estratégica de batalha, classificação de vitória/derrota ou replay com ramificações. Os modos de coleta de ouro/comida não são disponibilizados pela interface.

## Validação realizada

Veja [TEACHING_FIX_VALIDATION.md](TEACHING_FIX_VALIDATION.md) para a versão 0.5.1 e [VALIDATION.md](VALIDATION.md) para a validação inicial. Compilação debug, 35 testes JVM/Robolectric, sete testes instrumentados em emulador Android 16/API 36 e Lint passaram. Os testes instrumentados usam exclusivamente uma tela sintética de batalha incluída no source set debug.

**Não foi realizado teste no Dragon City nem no Galaxy A34 físico.** Os resultados em emulador não demonstram que a automação funciona no jogo. OCR e comparação visual podem errar; o bloqueio de gastos não é uma garantia contra ícones sem texto, idiomas não cobertos ou mudanças de tela após a captura.

## Build reproduzível

JDK 17 ou 21, SDK Platform 36 e Build Tools 35.0.0. Gradle Wrapper 8.13 com checksum da distribuição; AGP 8.13.0 e Kotlin 2.0.21.

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

O segundo comando requer um emulador SDK com API 30+, preferencialmente API 36. Os testes que ativam o serviço automaticamente se restringem a emuladores `ranchu`/`goldfish` e restauram as configurações ao terminar. Não execute a suíte de integração contra uma conta real do jogo.

O workflow [build-apk.yml](.github/workflows/build-apk.yml) compila os APKs, executa testes e publica APKs/relatórios como artefatos quando o GitHub Actions estiver disponível na conta.

## Uso no aparelho

1. Instale o APK debug ARM64. Ative o serviço de acessibilidade do app nas configurações do Android, após ler a explicação de captura e gestos.
2. Selecione o Dragon City na lista de aplicativos.
3. Abra a batalha no ponto inicial que pretende ensinar. Volte ao app e pressione ENSINAR. Faça somente toques em controles gratuitos de batalha, usando PARAR no painel para encerrar.
4. Volte ao app e revise todas as ações. Uma nova demonstração substitui a anterior; trocar de aplicativo invalida os dados anteriores.
5. Prepare a mesma tela inicial antes de EXECUTAR. Mantenha orientação, zoom e jogo em primeiro plano. Qualquer desvio interrompe sem tentar novamente o último toque.

Em instalações externas, o Android pode exigir a liberação manual de configurações restritas antes de habilitar acessibilidade. Sem essa autorização, o serviço não captura nem injeta gestos.

Referências técnicas: [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [ML Kit OCR incluído](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
