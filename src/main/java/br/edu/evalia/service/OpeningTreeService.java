package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.*;
import br.edu.evalia.repository.GameRepository;

public final class OpeningTreeService {
    private final PgnMoveExtractor extractor = new PgnMoveExtractor();

    public List<BookContinuation> bookMoves(BoardPosition position) {
        return new OpeningBook().nextMoves(position);
    }

    public List<ReferenceMoveStat> referenceMoves(GameRepository repository, BoardPosition target) {
        ChessRules rules = new ChessRules();
        ChessGameReplay replay = new ChessGameReplay();
        String key = rules.positionKey(target);
        Map<String, long[]> counts = new HashMap<>();
        repository.forEach(game -> {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            List<BoardPosition> positions;
            try { positions = replay.positions(game); }
            catch (IllegalArgumentException ex) { return; }
            for (int i = 0; i < Math.min(positions.size() - 1, 60); i++) {
                if (!rules.positionKey(positions.get(i)).equals(key)) continue;
                String uci = positions.get(i + 1).move().uci();
                long[] values = counts.computeIfAbsent(uci, m -> new long[3]);
                int result = game.getResult() == GameResult.DRAW ? 1
                        : (game.getResult() == GameResult.WIN) == (game.getColor() == PieceColor.WHITE) ? 0 : 2;
                values[result]++;
                break; // A repeated position counts once per game, not once per occurrence.
            }
        });
        return counts.entrySet().stream().map(e -> new ReferenceMoveStat(e.getKey(), e.getValue()[0],
                e.getValue()[1], e.getValue()[2], "Base PGN local"))
                .sorted(Comparator.comparingLong(ReferenceMoveStat::games).reversed()).toList();
    }

    public List<OpeningTreeRow> nextMoves(List<Game> games, List<String> line) {
        Map<String, List<Game>> grouped = new LinkedHashMap<>();
        for (Game game : games) {
            List<String> moves = extractor.extractSan(game.getPgn());
            if (moves.size() <= line.size()) continue;
            if (!matchesLine(moves, line)) continue;
            grouped.computeIfAbsent(moves.get(line.size()), key -> new ArrayList<>()).add(game);
        }
        return grouped.entrySet().stream()
                .map(entry -> row(entry.getKey(), entry.getValue(), line))
                .sorted(Comparator.comparingInt(OpeningTreeRow::games).reversed()
                        .thenComparing(OpeningTreeRow::move))
                .toList();
    }

    public String lineText(List<String> line) {
        if (line.isEmpty()) return "Posição inicial";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < line.size(); i++) {
            if (i % 2 == 0) text.append(i / 2 + 1).append(". ");
            else text.append(i / 2 + 1).append("... ");
            text.append(line.get(i)).append(' ');
        }
        return text.toString().trim();
    }

    private boolean matchesLine(List<String> moves, List<String> line) {
        if (moves.size() < line.size()) return false;
        for (int i = 0; i < line.size(); i++) {
            if (!moves.get(i).equals(line.get(i))) return false;
        }
        return true;
    }

    private OpeningTreeRow row(String move, List<Game> games, List<String> line) {
        int wins = count(games, GameResult.WIN);
        int draws = count(games, GameResult.DRAW);
        int losses = count(games, GameResult.LOSS);
        double score = games.isEmpty() ? 0 : (wins + draws * 0.5) * 100.0 / games.size();
        List<String> nextLine = new ArrayList<>(line);
        nextLine.add(move);
        return new OpeningTreeRow(move, lineText(nextLine), games.size(), wins, draws, losses, score);
    }

    private int count(List<Game> games, GameResult result) {
        return (int) games.stream().filter(game -> game.getResult() == result).count();
    }
}
