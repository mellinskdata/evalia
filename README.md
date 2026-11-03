# EVALIA — Chess Analysis Platform

**See beyond the moves.**

A Java 17 / JavaFX desktop application for chess game review and player-performance analysis. Evalia supports public Chess.com and Lichess account synchronization, PGN import, offline opening references, game replay, player insights, and Stockfish-powered move analysis.

**Tech stack:** Java, JavaFX, CSS, Maven, JUnit 5, Stockfish (UCI), Jackson, and public Chess.com/Lichess APIs.

**Quick start**

```bash
mvn clean test
mvn javafx:run
```

Requires JDK 17+ and Maven 3.9+. The application runs on Windows, Linux and macOS; automatic Stockfish download is supported on Windows, with manual engine configuration for other platforms. Windows packaging scripts are included.

**Project status:** v2.1 — source distribution. See the detailed documentation in Portuguese below. Build and runtime behavior should be verified in your local environment; this repository does not contain an embedded Stockfish binary.

---

# Evalia 2.1

**See beyond the moves.**

Evalia é um aplicativo desktop em **Java + JavaFX + CSS** para importar, revisar e analisar partidas de xadrez. Ele combina dados públicos de Chess.com/Lichess, PGNs locais, replay, diagnóstico do jogador, árvore/livro de aberturas, treino e análise com Stockfish.

## O que está integrado nesta versão

- Sincronização pública com **Chess.com** e **Lichess** em tarefa de background, com indicador de progresso e cancelamento.
- Importação de PGN colado ou por arquivo; arquivos grandes são lidos de forma incremental, sem carregar o arquivo inteiro antes de começar.
- Deduplicação por fingerprint da partida e persistência local em CSV.
- Bases separadas:
  - `data/account-games.csv`: partidas da conta atual/importações do jogador;
  - `data/historical-games.csv`: acervo histórico e partidas especiais.
- **Alterar conta** apaga somente a base da conta e preserva a base histórica.
- Migração automática da antiga `data/games.csv` para o novo formato na primeira execução.
- Database pesquisável por jogador, lance/FEN, abertura/ECO, torneio e resultado.
- Replay com **←/→**, **Home/End**, destaque da última jogada e abertura no ply exato de um exercício.
- Janela normal/maximizada preserva a barra nativa do sistema; **F11** ou o botão da barra superior ativam fullscreen real e **Esc** sai do fullscreen.
- Tabuleiro responsivo com casas `#f0d9b5` / `#b58863` e peças Chessnut fornecidas como SVG e renderizadas no app a partir de derivados PNG.
- Tabuleiro de análise livre usando a mesma API de regras do replay, incluindo roque, en passant, promoção e validação de xeque.
- Opening Tree da própria conta e **livro mestre offline CC0** (`opening-book.json`) na mesma tela, indexado por posição para reconhecer transposições.
- Comparação do livro de referência com a base local em background e com cancelamento.
- **Game Review por Stockfish**: calcula a perda de avaliação de cada lance, detecta mudanças críticas e testa posições candidatas com MultiPV para reconhecer ideias vencedoras únicas.
- Classificação dos lances em **Livro, Brilhante (!!), Ótimo (!), Bom (!?), Duvidoso (?!), Ruim (?) e Erro grave (??)**.
- No Windows, o Stockfish é **baixado e configurado automaticamente na primeira análise** a partir da release estável oficial; seleção manual continua disponível como fallback.
- Central de Insights com filtros realmente aplicados ao período, resultado esperado pelo rating, desempenho acima/abaixo do esperado, tendência recente, gap por cor, rating e prioridades de repertório.
- Aba **Imortal** mantida na base histórica, independente da conta conectada.

## Requisitos para executar pelo código-fonte

- JDK 17 ou superior (JDK 21 recomendado para empacotamento)
- Apache Maven 3.9+
- Internet apenas para baixar dependências na primeira compilação e para sincronizar contas

No Windows:

```powershell
java -version
mvn -version
mvn clean test
mvn javafx:run
```

Também é possível usar `run.bat`. Em Linux/macOS, use `./run.sh` depois de dar permissão de execução.

## Stockfish e Game Review

No **Windows**, você não precisa baixar Stockfish manualmente. Ao executar a primeira análise, o Evalia consulta a release estável oficial, baixa o binário Windows x64, extrai para `engine/stockfish.exe` e salva o caminho em `data/engine.properties`. Em execuções seguintes ele reutiliza a mesma engine.

O botão **Selecionar manualmente** continua disponível como fallback. Também continuam válidos `STOCKFISH_PATH`, o `PATH` do sistema e os diretórios `engine/` e `runtime/engine/`. Em Linux/macOS, esta versão ainda usa a descoberta/seleção manual.

O **Game Review** analisa a posição antes e depois de cada lance. A perda de avaliação orienta as categorias; em posições críticas, uma segunda linha MultiPV é comparada com a principal para detectar casos em que só uma continuação preserva uma vantagem vencedora. Os lances-chave exibidos após a revisão vêm desses dados da engine, não das antigas heurísticas de texto do PGN.

> O projeto não embute o executável do Stockfish no ZIP: ele é obtido diretamente da distribuição oficial na primeira utilização da engine. O Stockfish é software livre sob GPL; respeite a licença ao redistribuir binários.

## Gerar um `.exe` portátil para Windows

Em uma máquina Windows com JDK 21+ e Maven, execute:

```bat
build-windows-portable.bat
```

O script roda os testes, monta as dependências e usa `jpackage` para criar um app autocontido. O resultado fica em:

```text
dist\Evalia\Evalia.exe
```

Essa pasta já inclui um runtime Java; **o computador onde o Evalia será usado não precisa ter Java, Maven nem IDE instalados**. Distribua a pasta `dist\Evalia` inteira (ou compacte-a em ZIP), não apenas o `.exe` isolado.

Para gerar também um instalador `.exe`, use:

```bat
build-windows-installer.bat
```

O instalador Windows pode exigir o **WiX Toolset**, conforme a versão do JDK/jpackage instalada. O script portátil acima não depende do WiX.

Há ainda `.github/workflows/windows-release.yml`: ao executar o workflow no GitHub Actions, ele compila/testa em Windows e publica um ZIP do app autocontido como artefato.

## Conectar uma conta

Abra **Sincronizar contas**, informe o usuário e escolha Chess.com ou Lichess. Não é necessário fornecer senha: são usadas apenas APIs públicas.

A sincronização roda fora da thread da interface. Se for cancelada, o app mantém o que já havia sido salvo com sucesso. Ao trocar completamente de jogador, use **Alterar conta**; a base histórica não é removida.

## Importação manual de PGN

Na tela de sincronização, informe o nome do jogador exatamente como aparece no cabeçalho `White` ou `Black`. Você pode colar PGN ou escolher um `.pgn`.

A importação por arquivo usa `PgnStreamReader`, que enquadra uma partida por vez e limita cada partida individual a 2 MiB. Os lotes já persistidos são preservados em caso de cancelamento ou erro posterior. Um exemplo está em `examples/partidas-exemplo.pgn`.

## Replay, treino e tabuleiro

No replay:

- `←` / `→`: lance anterior/próximo;
- `Home` / `End`: início/fim da partida;
- a última jogada é destacada;
- o tabuleiro redimensiona mantendo proporção quadrada.

No Treino, `←` / `→` navegam pelos exercícios. **Abrir no replay** leva ao ply associado ao exercício.

No tabuleiro de análise livre, clique/arraste uma peça para a casa de destino. Os lances passam por `ChessRules`, que delega a legalidade ao chesslib; promoção por interação direta usa dama como padrão.

## Livro de aberturas

A tela **Aberturas** combina:

- árvore calculada a partir das partidas da conta;
- livro mestre offline em `src/main/resources/openings/opening-book.json`, proveniente de `lichess-org/chess-openings` e licenciado em CC0.

O catálogo possui milhares de linhas ECO e é indexado por posição, portanto transposições podem chegar à mesma entrada. Ele funciona sem login e sem rede. O mesmo livro é usado pelo Game Review para marcar um lance como **de livro** quando a continuação está no catálogo e não há perda relevante de avaliação.

A comparação com a base local usa leitura streaming do repositório e pode ser cancelada.

## Database e separação dos dados

O Database mostra uma visão combinada das bases de conta e histórica. Importar/exportar pela tela do Database atua sobre a **base histórica**, evitando misturar um acervo com os dados usados para calcular o perfil da conta.

Arquivos principais criados em runtime:

```text
data/account-games.csv
data/historical-games.csv
data/accounts.properties
data/annotations.properties
data/engine.properties
```

Se uma instalação antiga possuir `data/games.csv`, o Evalia tenta migrá-la e preserva o arquivo anterior como `data/games.legacy.csv`.

## Memória e desempenho

Foram eliminados os piores picos da importação: PGNs em arquivo e CSV histórico são processados incrementalmente/em lotes, e varreduras de referência podem usar `GameRepository.forEach(...)`.

Ainda existem operações de interface/analytics que criam snapshots com `findAll()` para ordenar, filtrar e desenhar tabelas/gráficos. Além disso, a deduplicação exata do repositório mantém fingerprints em memória, portanto o uso cresce com o número de partidas. Isso é adequado para bases pessoais e acervos moderados, mas **não há promessa de importação instantânea nem de uso constante de memória para bases de milhões de partidas**. Para esse cenário, o próximo passo arquitetural seria trocar o CSV/índice em memória por SQLite ou outro armazenamento indexado.

## Testes

A suíte inclui testes de parser/streaming de PGN, integrações Chess.com/Lichess com gateway falso, persistência CSV, busca no Database, Opening Tree/Book, replay e regras especiais do tabuleiro (roque, en passant e promoção).

Execute:

```powershell
mvn clean test
```

## Estrutura

```text
src/main/java/br/edu/evalia/
  EvaliaApp.java
  integration/       # Chess.com, Lichess e HTTP
  model/             # entidades/records imutáveis
  repository/        # persistência CSV
  service/           # parser, regras, replay, analytics, openings, engine, relatórios
src/main/resources/
  css/evalia.css
  openings/opening-book.json
  pgn/imortal-criador.pgn
  pieces/chessnut/   # SVG + derivados PNG usados pela interface
src/test/java/       # testes JUnit
examples/            # PGNs de demonstração
```

## Licenças de assets/dados

Os avisos que acompanham os assets/livro estão em `licenses/`. Consulte também as licenças das dependências e do Stockfish antes de redistribuir binários de terceiros.
