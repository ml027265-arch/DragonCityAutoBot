# Ensinar e executar batalhas

A demonstração contém até 12 toques simples. Não grava gestos longos/arrastos nem observa toques feitos fora da camada de treinamento. A camada intercepta o toque, se oculta, obtém a captura, verifica texto de risco, encaminha o gesto e só salva após confirmar uma mudança visual.

Cada passo armazena: imagem completa anterior, recorte de 96x96 px em torno do toque, coordenadas relativas, posição exata do toque dentro do recorte (inclusive perto das bordas), dimensão original, amostras visuais da tela antes/depois e aprovação manual. O pacote do jogo é registrado no índice. Demonstrações antigas sem esse esquema são rejeitadas.

No replay, a proporção deve coincidir com a demonstração (tolerância 0,025); escala aceita pelo matcher entre 0,5 e 2. A comparação global exige similaridade mínima 0,94, o alvo local 0,96. A busca é limitada a 64 px multiplicados pela escala ao redor da posição prevista. Recortes uniformes e alvos múltiplos são rejeitados. O ponto executado acompanha o recorte identificado e seu deslocamento interno, em vez de reutilizar a posição original.

Após cada gesto, espera 1,7 s e exige uma mudança visual (similaridade abaixo de 0,985 com a tela anterior) e um resultado parecido com o resultado ensinado (mínimo 0,94). Não tenta repetir o gesto se a confirmação falhar. O limite total da sessão é 90 s; captura/OCR/busca têm limite de 8 s e confirmação do gesto, 3 s. Alteração de foco, configuração, resolução, orientação, perda de acessibilidade e captura protegida interrompem.

A faixa superior e bordas são excluídas das ações permitidas. O painel PARAR permanece disponível no replay; durante o ensino a camada precisa se ocultar brevemente para capturar e encaminhar o toque. O botão PARAR no aplicativo também cancela uma abertura pendente do jogo. Mudanças de configuração invalidam a sessão.

## Proteção contra gastos

- Revisão obrigatória de cada captura completa, com marcação do toque, para identificar somente ações gratuitas.
- OCR latino local e texto de acessibilidade procuram termos de compra/gasto em português e inglês antes dos gestos; falha de OCR bloqueia a ação.
- Limites de ações, tempo e frequência; uma única ação pendente por vez.
- Contexto completo e alvo local verificados antes, contexto e mudança verificados depois.

**Proteção parcial:** ícones, textos ilegíveis, moedas sem rótulo e mudanças rápidas após uma captura podem escapar. Não existe um classificador semântico validado do Dragon City nem garantia de ausência de gastos. Uma revisão manual incorreta também permite uma ação perigosa. O aplicativo pode bloquear batalhas legítimas se houver texto relacionado a gemas/loja em outra região da tela ou se animações alterarem o contexto.

## Testes e limitações

A integração foi testada com AccessibilityService, captura real do emulador, OCR incluído e dispatchGesture em uma tela sintética Android 16. O teste de duas etapas verifica o deslocamento efetivo da coordenada enviada e a confirmação pós-ação. Telas de compra e desconhecidas foram bloqueadas sem gestos.

Não houve teste real no Dragon City ou no Samsung Galaxy A34. Não há escolha de melhor ataque, detecção semântica de resultado ou ramificações de batalha. Mudanças de interface, resolução, proporção, zoom, duração de animações e resultados variáveis podem interromper a sequência. Uma sequência concluída significa somente que os passos visuais foram verificados.
