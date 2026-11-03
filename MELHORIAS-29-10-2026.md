# Melhorias implementadas — 29/10/2026

## Janela e fullscreen

- A aplicação abre maximizada, mas continua sendo uma janela normal do Windows, com minimizar/maximizar/fechar.
- `F11` ou o botão **Tela cheia** entram em fullscreen real.
- `Esc` ou o próprio botão saem do fullscreen e retornam à janela normal/maximizada.

## Game Review e lances-chave

A revisão completa analisa a posição antes e depois de cada lance com Stockfish.
O valor principal usado é a **perda em centipawns (CPL)** do lance em relação à melhor continuação encontrada pela engine, mas a anotação também considera **o estado resultante da posição**. Isso evita chamar de `??` qualquer perda de avaliação em uma partida que já estava perdida.

Os **lances-chave** não são preenchidos artificialmente. Eles aparecem quando:

- há mudança grande de avaliação (aproximadamente 0,90 peão ou mais);
- ocorre um erro claro (`?`) ou colapso decisivo (`??`);
- existe uma continuação muito superior às alternativas (`!`);
- ou o Stockfish detecta uma **única ideia vencedora** (`!!`).

Para distinguir um bom lance comum de um lance realmente especial, o Evalia usa `MultiPV=2` em posições selecionadas. Ser simplesmente o primeiro lance do Stockfish **não é suficiente** para receber `!`.

## Classificação dos lances

A filosofia agora é a mesma de uma anotação enxadrística normal: **a maioria dos lances corretos fica sem símbolo**.

- **De livro `📖`**: continuação presente no livro offline e sem perda relevante (CPL <= 0,35);
- **Brilhante `!!`**: melhor lance, praticamente sem perda, que é detectado como a única continuação capaz de manter uma vitória clara/mate; a segunda opção precisa cair de forma muito significativa;
- **Ótimo `!`**: melhor lance muito preciso em uma posição importante, em que a segunda opção da engine é pelo menos ~0,70 peão pior. Um simples “best move” não recebe `!` automaticamente;
- **Normal (sem símbolo)**: lances bons, naturais, precisos ou aceitáveis que não merecem destaque especial;
- **Duvidoso `?!`**: concessão perceptível, em geral a partir de ~0,55 peão de perda, mas ainda sem caráter de erro grave;
- **Ruim `?`**: erro claro, em geral a partir de ~1,25 peão, ou lance que transforma uma posição saudável em uma posição claramente pior;
- **Erro grave `??`**: reservado para **colapso imediato** — permitir mate, jogar uma posição vencedora para perdida, sair de uma posição equilibrada para uma posição decisivamente perdida, ou sofrer uma queda enorme que já deixa a posição perdida.

Esses limites são deliberadamente mais conservadores para que `!`, `!!` e `??` sejam raros e tenham significado.

## Livro mestre offline

O Evalia usa o `opening-book.json` embarcado, derivado do projeto `lichess-org/chess-openings`, licenciado em CC0. O catálogo possui milhares de linhas ECO e é indexado por posição, então reconhece transposições. Ele não exige conta, token ou internet.

O mesmo catálogo agora é usado diretamente pelo Game Review para reconhecer lances **de livro**.

## Stockfish automático

No Windows, se nenhuma engine estiver configurada quando uma análise for iniciada, o Evalia:

1. consulta a release estável oficial do Stockfish no GitHub;
2. escolhe o pacote Windows x64 compatível;
3. baixa o ZIP;
4. extrai o executável para `engine/stockfish.exe`;
5. salva o caminho em `data/engine.properties`;
6. reutiliza essa instalação nas próximas execuções.

A seleção manual continua disponível como fallback.

## Insights

A tela de Insights agora recalcula as métricas para o período realmente selecionado, em vez de reaproveitar parte do snapshot global.

Foram acrescentados:

- resultado esperado pela diferença de rating (expectativa Elo logística);
- desempenho observado menos esperado, em pontos percentuais;
- comparação do bloco recente com o bloco anterior;
- variação de rating no período;
- gap de desempenho entre brancas e pretas;
- prioridade de repertório somente com amostra mínima;
- ranking de força/fraqueza com regularização de amostras pequenas: o score é suavizado em direção a 50% com um prior equivalente a 4 partidas;
- detecção de concentração de derrotas curtas;
- vitórias contra adversários 100+ pontos acima;
- aviso explícito quando a amostra é pequena.

As aberturas deixam de ser declaradas melhor/pior com somente duas partidas: o diagnóstico exige pelo menos 3 e os “buracos de repertório” pelo menos 4.
