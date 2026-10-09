# Batalhas — demonstração experimental (Android 16)

## O que existe
- No app, escolha Dragon City e habilite manualmente o serviço de acessibilidade.
- **ENSINAR** abre o jogo com uma camada de captura de toques. Toque nos controles da batalha que deseja demonstrar; até **12 toques**. Toque **PARAR** na faixa superior para salvar.
- Antes de cada toque, o serviço captura a tela (AccessibilityService.takeScreenshot) e salva um recorte de 96x96 pixels em armazenamento privado, junto da posição relativa do toque.
- O toque é encaminhado ao jogo por dispatchGesture, sem root.
- **EXECUTAR** abre o jogo e tenta reconhecer a sequência de recortes antes de cada toque. Se não reconhecer, se o jogo perder foco, se a captura falhar ou se ultrapassar 90 segundos, interrompe.
- **PARAR** no app interrompe; o modo ensinar também tem PARAR na faixa superior.
- Sem upload de capturas para servidor. Para apagar os dados, faça novo treinamento ou limpe os dados do aplicativo.

## Limitações relevantes
- **Protótipo não compilado nem validado em aparelho.** O GitHub Actions estava bloqueado por faturamento na conta.
- Não é um modelo de aprendizado de máquina; usa correspondência visual de pixels. Animações, mudança de zoom, orientação, resoluções, transições e atualizações do jogo podem interromper o replay.
- O sistema NÃO lê toques que você faça diretamente no jogo sem a camada de treinamento. Ela intercepta toques e os encaminha.
- Apenas toques simples; não aprende arrastar, pressionar por longo tempo ou decisões condicionais.
- O usuário é responsável por selecionar uma sequência segura; não há proteção semântica garantida contra gastos ou botões de compras. **Nunca demonstre compras ou gasto de gemas.**
- Para batalhas com múltiplos caminhos ou resultados variáveis, não existe garantia de conclusão.
- O jogo precisa estar visível e o serviço de acessibilidade ativo. Não funciona com tela apagada.
- O uso de automação pode violar regras do jogo.

## Próximos passos
- Compilar e corrigir erros de build, validar no Samsung A34 Android 16.
- Adicionar detector de resultado da batalha e verificação visual pós-toque.
- Adicionar gestos, seleção de áreas e treinamento com variações.
- Adicionar bloqueio semântico de compras com confirmação explícita.
