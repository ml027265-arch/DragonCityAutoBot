# Validação local — 9 de outubro de 2026

## Resultado

A versão 0.5-alpha foi compilada e testada localmente no Windows com Temurin JDK 21, Gradle 8.13, AGP 8.13.0, SDK Platform 36 e Build Tools 35.0.0.

Comando final executado:

```text
gradle assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
BUILD SUCCESSFUL in 47s
```

| Verificação | Resultado |
|---|---|
| APK debug ARM64 para Galaxy A34 | Gerado; pacote com.dragoncity.autobot, versionCode 2, targetSdk 36, minSdk 30 |
| Testes JVM de política de segurança | 12 passaram |
| Testes Robolectric API 36 de visão, armazenamento e engine | 18 passaram |
| Testes de substituição atômica e falha de gravação | 2 passaram |
| Testes instrumentados no emulador Android 16/API 36 | 5 passaram, nenhum ignorado |
| Android Lint | Sem erros; 22 avisos de estilo/localização/versões disponíveis |
| apksigner verify --verbose | Passou, assinatura APK v2 válida |
| zipalign -c -P 16 4 | Passou, código de saída 0 |
| aapt dump badging | Confirmou API 36, versão 0.5-alpha e ABI arm64-v8a |

Os cinco testes instrumentados executados:

- `launcherOpensWithoutCrash`: abertura da interface sem crash.
- `bundledOcrDetectsPurchaseAndAcceptsAttack`: OCR real incluído no APK reconheceu compra e ataque em imagens sintéticas.
- `purchaseScreenPreventsTrainingGesture`: texto de compra na tela sintética impediu o toque; contador de ações permaneceu zero.
- `unknownScreenPreventsReplayGesture`: contexto desconhecido interrompeu o replay; nenhum toque.
- `teachesCapturesPersistsAndAdaptivelyReplaysTwoActions`: captura do emulador, encaminhamento de dois gestos, gravação de duas demonstrações inicialmente bloqueadas, aprovação, replay com alvo deslocado em 8 px, coordenada real adaptada e verificação de ambos os resultados.

## APK entregue

`DragonCityAutoBot-0.5-debug-arm64.apk` (aproximadamente 22 MB, ARM64).

SHA-256:

```text
E0D7670FD79D92816F2F6078F0A77F5BA4CAE36B271C173845802F1884973C94
```

É um APK debug assinado com chave de desenvolvimento, sem finalidade de publicação em loja. O APK x86_64 foi instalado e executado no emulador; o ARM64 teve compilação, assinatura, alinhamento e metadados verificados, mas não foi instalado em um aparelho ARM64 nesta sessão. O alinhamento ZIP de 16 KB não prova execução em um aparelho com páginas de 16 KB.

## Correções motivadas pelos testes

- Correspondência local passou a localizar posições exatas e rejeitar alvos duplicados em vez de tocar a posição original.
- Gravação usa sincronização e substituição atômica portável; um erro mantém o último índice completo.
- Avisos de sessões anteriores são cancelados; o OCR não deve interpretar um aviso antigo do próprio bot como texto de compra da nova sessão.
- Os testes esperam a desconexão do serviço na limpeza, evitando usar uma instância antiga.
- Foram corrigidos o caminho do SDK no Windows, o SDK alvo, a configuração de backups, recursos e Gradle Wrapper.

## Limites da validação

Não houve teste no Dragon City e não houve teste no Samsung Galaxy A34 físico: nenhum A34 estava conectado/autorizado no ADB. O emulador utilizado foi criado nesta sessão com imagem SDK Google APIs API 36/x86_64; a tela de batalha é uma fixture de teste sem conta do jogo.

Não há evidência de que o replay conclua batalhas reais. Não há decisão de ataque, classificador de vitória/derrota, ramificações aprendidas ou validação semântica dos controles do Dragon City. O bloqueio de gastos depende de revisão humana, OCR e contexto visual, e pode falhar com ícones/textos não reconhecidos ou mudanças posteriores à captura. As assinaturas e os thresholds também podem interromper ações legítimas durante animações.

Relatórios completos de testes JVM, testes instrumentados e Lint, junto do log final e da verificação de assinatura, foram reunidos na entrega local. O workflow GitHub foi atualizado para repetir build/testes e publicar artefatos; sua execução remota depende da disponibilidade do Actions na conta.

## Bloqueio remoto confirmado

Após o envio do código à branch main, o workflow [37879537756](https://github.com/ml027265-arch/DragonCityAutoBot/actions/runs/37879537756) terminou sem iniciar qualquer etapa dos jobs. A anotação do GitHub informou: `The job was not started because your account is locked due to a billing issue.`

Consequência: o GitHub Actions não executou testes nem publicou artefatos nessa execução. Os resultados acima são exclusivamente os resultados locais. O APK compilado, os relatórios completos, o checksum e o código-fonte foram disponibilizados em `DragonCityAutoBot-entrega` na Área de Trabalho. Nenhuma configuração de faturamento foi alterada.
