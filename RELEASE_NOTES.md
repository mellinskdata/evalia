# Atualização de 29/10/2026 — revisão de análise e UX

- **Fullscreen corrigido:** janela normal/maximizada usa a barra nativa do sistema; F11 e botão dedicado ativam fullscreen real; Esc retorna à janela maximizada.
- **Game Review por avaliação:** todos os lances são avaliados antes/depois pelo Stockfish e recebem CPL (centipawn loss).
- **Lances-chave reais:** selecionados por grande mudança de avaliação, erro grave ou por uma continuação vencedora única confirmada com MultiPV.
- **Classificação:** Livro, Brilhante `!!`, Ótimo `!`, Bom `!?`, Duvidoso `?!`, Ruim `?` e Erro grave `??`.
- **Livro mestre offline:** o catálogo CC0 de `lichess-org/chess-openings` passa a ser apresentado explicitamente como livro offline e é usado pela classificação “de livro”.
- **Stockfish automático no Windows:** a primeira análise baixa a release estável oficial para `engine/stockfish.exe`; não é necessário instalar a engine manualmente.
- **Insights reformulados:** filtros de 10/30/90 dias agora recalculam as métricas do próprio período; foram adicionados resultado esperado pelo rating, desvio observado, tendência recente, variação de rating, gap por cor e recomendações acionáveis; força/fraqueza e repertório agora usam tamanho mínimo e suavização de amostras pequenas em direção a 50%.

# Evalia 2.1 — notas da entrega

## Pendências anteriores resolvidas

1. **Compilação/API do tabuleiro**
   - removida a chamada antiga `chessRules.move(...)`;
   - o tabuleiro interativo agora usa `ChessRules.play(BoardPosition, Move)`, a mesma camada de regras do replay;
   - suporte de legalidade fica centralizado no chesslib, incluindo roque, en passant, promoção e xeque.

2. **Stockfish, importação e livro JSON na interface**
   - painel Stockfish/UCI no replay, com seleção de executável, profundidade, background, timeout e cancelamento;
   - descoberta/persistência do executável em `EngineSettings`;
   - sincronização Chess.com/Lichess em background com progresso/cancelamento;
   - importação PGN colada ou em arquivo em background;
   - arquivo PGN é processado incrementalmente por `PgnStreamReader`;
   - importação da base histórica em background, com cancelamento e preservação dos lotes concluídos;
   - catálogo `opening-book.json` integrado à tela Aberturas, com continuacões/estatísticas e comparação com base local.

3. **Visual do tabuleiro**
   - casas claras `#f0d9b5` e escuras `#b58863`;
   - destaque da última jogada;
   - tabuleiro quadrado e responsivo, sem casas fixas de 56 px;
   - assets Chessnut SVG preservados e 12 derivados PNG adicionados para renderização direta no JavaFX.

4. **Teclado**
   - Replay: `←`, `→`, `Home`, `End`;
   - Treino: `←` e `→`;
   - exercício abre o replay no ply correspondente.

5. **Conta x histórico**
   - `data/account-games.csv` para dados da conta;
   - `data/historical-games.csv` para acervo/Imortal;
   - Alterar conta limpa somente a conta;
   - a Imortal é semeada na base histórica;
   - migração da antiga `data/games.csv` com backup `data/games.legacy.csv`.

6. **Memória/desempenho**
   - PGN de arquivo é streaming;
   - CSV histórico é importado em lotes;
   - comparação do livro pode usar `GameRepository.forEach(...)`;
   - Database reutiliza um snapshot por tela em vez de reler os CSVs a cada filtro;
   - permanece documentado que analytics/tabelas ainda usam snapshots `findAll()` e que a deduplicação exata mantém fingerprints em memória. Não há promessa de memória constante para bases de milhões de partidas.

7. **Entrega/empacotamento**
   - versão do projeto atualizada para 2.1.0;
   - `build-windows-portable.bat`: gera `dist\\Evalia\\Evalia.exe` autocontido via jpackage;
   - `build-windows-installer.bat`: gera instalador `.exe` quando o WiX exigido pelo jpackage estiver disponível;
   - workflow `.github/workflows/windows-release.yml` gera o app Windows portátil em GitHub Actions;
   - README refeito com execução, Stockfish, empacotamento, dados e limites de escala.

## Validação executada neste ambiente

Passou:

- compilação `javac` e execução de smoke test das classes independentes de JavaFX/chesslib;
- deduplicação e reset da base de conta sem afetar base histórica;
- enquadramento streaming de múltiplos PGNs sem dividir cabeçalhos falsos dentro de comentários;
- persistência de caminho da engine;
- conversa UCI do `StockfishService` com engine falsa, incluindo parse de depth/centipawns/PV/bestmove;
- checagem sintática geral com `javac` (os erros restantes no ambiente são apenas dependências externas ausentes);
- `pom.xml` bem-formado;
- `opening-book.json` válido;
- 12 PNGs de peças válidos;
- `run.sh` com sintaxe válida;
- YAML do workflow válido;
- busca por chamada antiga `chessRules.move`, cores antigas e tamanho fixo de 56 px sem ocorrências.

## Limitação da validação local

Este ambiente não possui Maven nem os JARs de JavaFX/chesslib/Jackson em cache, e não oferece o ambiente Windows necessário para o `jpackage --type exe`. Por isso, **não foi possível executar aqui `mvn clean test` nem produzir fisicamente o `.exe` de Windows**.

O ZIP inclui os testes JUnit e os scripts/workflow que executam o build completo em um Windows com as dependências disponíveis. O app portátil produzido por `build-windows-portable.bat` inclui o runtime Java para a máquina de destino; deve-se distribuir a pasta `dist\\Evalia` inteira.
