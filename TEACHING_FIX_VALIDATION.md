# Correção do painel Ensinar — versão 0.5.1-alpha

## Problema observado

O usuário relatou que o painel ENSINAR desaparecia definitivamente no primeiro toque. A imagem enviada mostra a ilha inicial com rótulos LOJA/OFERTAS distantes do botão BATALHA. Na versão 0.5, o OCR bloqueava qualquer palavra de gasto na tela inteira, incluindo esses rótulos de navegação. Como o painel era removido antes da captura e só retornava após sucesso, uma interrupção parecia um desaparecimento sem explicação. A imagem não contém o último status do aparelho; não houve log ADB do A34 para confirmar qual condição interrompeu aquela sessão específica.

## Correção

- LOJA/OFERTAS/GEMAS e outros rótulos passivos são verificados perto da coordenada do toque, usando as caixas reconhecidas pelo OCR e as posições dos elementos acessíveis. Margem de 96 px.
- Pedidos explícitos de compra/gasto (COMPRAR, GASTAR, BUY, PURCHASE, etc.) continuam bloqueando a tela inteira. Falta de geometria confiável no OCR falha de forma conservadora.
- O painel mostra Capturando e verificando, Verificando resultado e Toque salvo. Durante o encaminhamento, aguarde a indicação de que o toque foi salvo antes de tocar novamente.
- Ao interromper, mantém um painel com o motivo, que pode ser fechado por toque. O aplicativo mostra Último status. Recusa de captura exibe o código retornado pelo Android quando disponível.
- Eventos de janela de outro pacote consultam o foco real após 300 ms, evitando tratar um aviso ou painel do sistema como troca definitiva do jogo. Captura, OCR e gestos continuam exigindo que o aplicativo selecionado esteja ativo.
- Nova versão com versionCode 3. Mesma chave debug local, permitindo instalar como atualização da versão entregue anteriormente.

## Verificação realizada em 9 de outubro de 2026

Comando: `gradle assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`.
Resultado: `BUILD SUCCESSFUL in 1m 12s`.

| Verificação | Resultado |
|---|---|
| Política de segurança e regressões | 15 testes JVM passaram |
| Visão/armazenamento/engine, Robolectric API 36 | 18 testes passaram |
| Substituição atômica | 2 testes passaram |
| Integração no emulador Android 16, fixture em landscape | 7 testes passaram, nenhum ignorado |
| Lint | Sem erros; avisos não bloqueantes no relatório |
| Assinatura APK e alinhamento ZIP 16 KB | Verificados |
| Pacote/versão/alvo | com.dragoncity.autobot, 0.5.1-alpha, versionCode 3, targetSdk 36 |

Os sete testes de integração verificaram abertura do app; OCR incluído; tela desconhecida sem gesto; confirmação de compra sem gesto; rótulo LOJA perto do toque sem gesto e com motivo visível; ausência de mudança visual sem repetição e com motivo visível; e ensino/replay de duas ações com LOJA/OFERTAS/GEMAS em regiões distantes, painel visível e coordenada adaptada ao alvo deslocado.

APK ARM64: `DragonCityAutoBot-0.5.1-debug-arm64.apk`.

SHA-256: `C6CCB33C6007BB5081070EB8C7F52184C0C8032846BEA9EB3255E6DDD196FE66`.

## Limites

Não houve teste desta versão no Galaxy A34 ou no Dragon City real; nenhum aparelho físico estava conectado ao ADB. O teste usa uma tela sintética, reproduzindo os rótulos relevantes da imagem enviada e a orientação horizontal. Não há garantia de sucesso em batalhas nem proteção semântica completa contra gastos. Ícones sem texto, rótulos distantes do controle, idiomas não cobertos e mudanças após uma captura continuam sendo limitações.

A validação foi local. O GitHub Actions permanece dependente da disponibilidade da conta, cujo bloqueio de faturamento foi confirmado na entrega anterior.
