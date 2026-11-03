package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.*;

public final class GameReviewService {
    private final ChessGameReplay replay = new ChessGameReplay();
    private final OpeningBook openingBook = new OpeningBook();

    public GameReview review(Game game) {
        List<String> moves = replay.moves(game);
        List<BoardPosition> positions;
        try { positions = replay.positions(game); }
        catch (IllegalArgumentException ex) {
            return new GameReview(openingBook.classify(game), List.of(), List.of(), List.of(), List.of(),
                    "PGN invalido", List.of("Revisao indisponivel: " + ex.getMessage()));
        }
        BoardPosition finalPosition = positions.get(positions.size() - 1);
        OpeningFamily family = openingBook.classify(game);
        List<String> tacticalThemes = tacticalThemes(moves);
        List<String> strategicThemes = strategicThemes(game, family, moves, finalPosition);
        String endgameType = classifyEndgame(finalPosition);
        List<String> endgameThemes = endgameThemes(endgameType, finalPosition);

        return new GameReview(family, keyMoves(game, moves), tacticalThemes, strategicThemes,
                endgameThemes, endgameType, recommendations(game, family, tacticalThemes, strategicThemes, endgameType));
    }

    public Map<String, Long> countTacticalThemes(List<Game> games) {
        return countThemes(games, "TACTICS");
    }

    public Map<String, Long> countStrategicThemes(List<Game> games) {
        return countThemes(games, "STRATEGY");
    }

    public Map<String, Long> countEndgameThemes(List<Game> games) {
        return countThemes(games, "ENDGAME");
    }

    private Map<String, Long> countThemes(List<Game> games, String type) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Game game : games) {
            GameReview review = review(game);
            List<String> themes = switch (type) {
                case "TACTICS" -> review.tacticalThemes();
                case "STRATEGY" -> review.strategicThemes();
                default -> review.endgameThemes();
            };
            for (String theme : themes) counts.merge(theme, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    private List<MoveFeedback> keyMoves(Game game, List<String> moves) {
        List<MoveFeedback> feedback = new ArrayList<>();
        Set<String> usedThemes = new HashSet<>();
        for (int i = 0; i < moves.size(); i++) {
            String move = moves.get(i);
            int moveNumber = i / 2 + 1;
            PieceColor side = i % 2 == 0 ? PieceColor.WHITE : PieceColor.BLACK;
            boolean playerMove = side == game.getColor();
            String clean = clean(move);

            if (move.contains("#")) {
                add(feedback, usedThemes, moveNumber, side, move, "Tatica", "Rede de mate",
                        "Lance decisivo: a partida chegou a uma rede de mate.", "feedback-critical");
            } else if (clean.startsWith("N") && move.contains("+")) {
                add(feedback, usedThemes, moveNumber, side, move, "Tatica", "Garfo de cavalo",
                        "Cheque de cavalo costuma atacar rei e outra peca ao mesmo tempo.", "feedback-tactic");
            } else if (linePiece(clean) && move.contains("+")) {
                add(feedback, usedThemes, moveNumber, side, move, "Tatica", "Ataque descoberto ou raio-X",
                        "Peca de linha deu cheque; vale conferir se uma coluna ou diagonal abriu.", "feedback-tactic");
            } else if (linePiece(clean) && move.contains("x")) {
                add(feedback, usedThemes, moveNumber, side, move, "Tatica", "Captura em linha",
                        "Bispos, torres e damas em capturas costumam revelar cravadas, raios-X ou sobrecarga.", "feedback-tactic");
            } else if (move.contains("=Q") || move.contains("=R")) {
                add(feedback, usedThemes, moveNumber, side, move, "Final", "Promocao de peao",
                        "Um peao chegou na ultima fileira; esse e um tema central de finais.", "feedback-endgame");
            } else if (clean.equals("O-O") || clean.equals("O-O-O")) {
                add(feedback, usedThemes, moveNumber, side, move, "Estrategia", "Seguranca do rei",
                        "Roque feito: conecta torres e tira o rei do centro.", "feedback-strategy");
            } else if (i < 12 && clean.matches("[a-h][34]")) {
                add(feedback, usedThemes, moveNumber, side, move, "Abertura", "Controle central",
                        "Peao central ou semi-central no inicio define espaco e casas para as pecas.", "feedback-opening");
            } else if (i < 16 && clean.startsWith("Q")) {
                add(feedback, usedThemes, moveNumber, side, move, "Abertura", "Dama cedo",
                        "Saida antecipada da dama pode ganhar tempo, mas tambem vira alvo de desenvolvimento.", "feedback-warning");
            }

            if (feedback.size() >= 9) break;
            if (playerMove && move.contains("?")) {
                add(feedback, usedThemes, moveNumber, side, move, "Revisao", "Lance marcado no PGN",
                        "O PGN marcou esse lance com anotacao. Vale revisar a posicao no tabuleiro.", "feedback-warning");
            }
        }
        if (feedback.isEmpty()) {
            feedback.add(new MoveFeedback(1, game.getColor(), "-",
                    "Resumo", "Sem lances criticos marcados",
                    "A partida nao trouxe marcadores taticos claros no PGN; use o replay para revisar planos e estruturas.",
                    "feedback-neutral"));
        }
        return List.copyOf(feedback);
    }

    private void add(List<MoveFeedback> feedback, Set<String> usedThemes, int moveNumber,
                     PieceColor side, String san, String category, String theme,
                     String description, String cssClass) {
        if (usedThemes.add(theme)) {
            feedback.add(new MoveFeedback(moveNumber, side, san, category, theme, description, cssClass));
        }
    }

    private List<String> tacticalThemes(List<String> moves) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        for (String move : moves) {
            String clean = clean(move);
            if (move.contains("#")) themes.add("mate e rede de mate");
            if (clean.startsWith("N") && move.contains("+")) themes.add("garfo de cavalo");
            if (linePiece(clean) && move.contains("+")) themes.add("ataque descoberto / raio-X");
            if (linePiece(clean) && move.contains("x")) themes.add("cravada, raio-X ou sobrecarga");
            if ((clean.startsWith("R") || clean.startsWith("Q")) && move.contains("#")) themes.add("tema de mate na ultima fileira");
            if (move.contains("=Q") || move.contains("=R")) themes.add("promocao tática");
        }
        if (themes.isEmpty()) themes.add("sem tema tático forte detectado");
        return List.copyOf(themes);
    }

    private List<String> strategicThemes(Game game, OpeningFamily family, List<String> moves, BoardPosition finalPosition) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        themes.add("plano de abertura: " + family.name());
        if (moves.stream().map(this::clean).anyMatch(move -> move.equals("O-O") || move.equals("O-O-O"))) {
            themes.add("seguranca do rei e conexao das torres");
        }
        themes.addAll(pawnStructureThemes(finalPosition));
        if (hasBishopPair(finalPosition, true) || hasBishopPair(finalPosition, false)) themes.add("par de bispos");
        if (hasOpenFile(finalPosition)) themes.add("coluna aberta ou semiaberta");
        if (game.getResult() == GameResult.LOSS && game.getMoves() < 30) themes.add("desenvolvimento/rei vulneravel em partida curta");
        return List.copyOf(themes);
    }

    private List<String> pawnStructureThemes(BoardPosition position) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        char[][] board = position.piecesCopy();
        for (boolean white : List.of(true, false)) {
            int[] pawnsByFile = new int[8];
            List<int[]> pawns = new ArrayList<>();
            char pawn = white ? 'P' : 'p';
            for (int row = 0; row < 8; row++) {
                for (int column = 0; column < 8; column++) {
                    if (board[row][column] == pawn) {
                        pawnsByFile[column]++;
                        pawns.add(new int[] {row, column});
                    }
                }
            }
            if (Arrays.stream(pawnsByFile).anyMatch(count -> count > 1)) themes.add("estrutura de peoes: peoes dobrados");
            if (hasIsolatedPawn(pawns, pawnsByFile)) themes.add("estrutura de peoes: peao isolado");
            if (hasPassedPawn(board, pawns, white)) themes.add("estrutura de peoes: peao passado");
        }
        if (themes.isEmpty()) themes.add("estrutura de peoes equilibrada");
        return List.copyOf(themes);
    }

    private boolean hasIsolatedPawn(List<int[]> pawns, int[] pawnsByFile) {
        for (int[] pawn : pawns) {
            int file = pawn[1];
            boolean left = file > 0 && pawnsByFile[file - 1] > 0;
            boolean right = file < 7 && pawnsByFile[file + 1] > 0;
            if (!left && !right) return true;
        }
        return false;
    }

    private boolean hasPassedPawn(char[][] board, List<int[]> pawns, boolean white) {
        char enemyPawn = white ? 'p' : 'P';
        for (int[] pawn : pawns) {
            int direction = white ? -1 : 1;
            boolean blocked = false;
            for (int row = pawn[0] + direction; row >= 0 && row < 8; row += direction) {
                for (int file = Math.max(0, pawn[1] - 1); file <= Math.min(7, pawn[1] + 1); file++) {
                    if (board[row][file] == enemyPawn) blocked = true;
                }
            }
            if (!blocked) return true;
        }
        return false;
    }

    private String classifyEndgame(BoardPosition position) {
        char[][] board = position.piecesCopy();
        int queens = count(board, 'Q') + count(board, 'q');
        int rooks = count(board, 'R') + count(board, 'r');
        int minors = count(board, 'B') + count(board, 'b') + count(board, 'N') + count(board, 'n');
        int pieces = queens + rooks + minors;
        if (pieces == 0) return "Final de peoes";
        if (queens > 0 && pieces <= 4) return "Final de damas";
        if (rooks > 0 && queens == 0 && minors == 0) return "Final de torre";
        if (minors > 0 && queens == 0 && rooks == 0) return "Final de pecas menores";
        if (pieces <= 5) return "Final misto";
        return "Meio-jogo ou final ainda nao simplificado";
    }

    private List<String> endgameThemes(String endgameType, BoardPosition position) {
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        themes.add(endgameType);
        if (endgameType.contains("peoes")) themes.add("oposicao, rei ativo e peao passado");
        if (endgameType.contains("torre")) themes.add("torre ativa, corte do rei e peoes passados");
        if (endgameType.contains("pecas menores")) themes.add("bispo bom/ruim, casas fortes e zugzwang");
        if (endgameType.contains("damas")) themes.add("xeques perpetuos e seguranca do rei");
        if (hasPassedPawn(position.piecesCopy(), allPawns(position.piecesCopy(), true), true)
                || hasPassedPawn(position.piecesCopy(), allPawns(position.piecesCopy(), false), false)) {
            themes.add("peao passado no final");
        }
        return List.copyOf(themes);
    }

    private List<String> recommendations(Game game, OpeningFamily family, List<String> tacticalThemes,
                                         List<String> strategicThemes, String endgameType) {
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Revisar plano típico: " + family.mainPlan());
        if (game.getResult() == GameResult.LOSS && game.getMoves() < 30) {
            recommendations.add("Prioridade: revisar os primeiros 15 lances e checar rei no centro/desenvolvimento.");
        }
        if (tacticalThemes.stream().anyMatch(theme -> theme.contains("garfo"))) {
            recommendations.add("Treino sugerido: temas de garfo e casas duplas atacadas.");
        }
        if (strategicThemes.stream().anyMatch(theme -> theme.contains("peao isolado"))) {
            recommendations.add("Treino sugerido: jogar com e contra o peao isolado da dama.");
        }
        if (endgameType.contains("torre")) recommendations.add("Treino sugerido: finais de torre com torre ativa e peoes passados.");
        if (recommendations.size() == 1) recommendations.add("Use o replay para marcar onde o plano da abertura mudou.");
        return List.copyOf(recommendations);
    }

    private boolean hasBishopPair(BoardPosition position, boolean white) {
        return count(position.piecesCopy(), white ? 'B' : 'b') >= 2;
    }

    private boolean hasOpenFile(BoardPosition position) {
        char[][] board = position.piecesCopy();
        for (int column = 0; column < 8; column++) {
            boolean hasPawn = false;
            boolean hasHeavyPiece = false;
            for (int row = 0; row < 8; row++) {
                char piece = board[row][column];
                if (piece == 'P' || piece == 'p') hasPawn = true;
                if (piece == 'R' || piece == 'r' || piece == 'Q' || piece == 'q') hasHeavyPiece = true;
            }
            if (!hasPawn && hasHeavyPiece) return true;
        }
        return false;
    }

    private List<int[]> allPawns(char[][] board, boolean white) {
        List<int[]> pawns = new ArrayList<>();
        char pawn = white ? 'P' : 'p';
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                if (board[row][column] == pawn) pawns.add(new int[] {row, column});
            }
        }
        return pawns;
    }

    private int count(char[][] board, char piece) {
        int count = 0;
        for (char[] row : board) {
            for (char value : row) {
                if (value == piece) count++;
            }
        }
        return count;
    }

    private boolean linePiece(String cleanMove) {
        return cleanMove.startsWith("B") || cleanMove.startsWith("R") || cleanMove.startsWith("Q");
    }

    private String clean(String move) {
        return move.replace("0", "O").replaceAll("[+#?!]", "");
    }
}
