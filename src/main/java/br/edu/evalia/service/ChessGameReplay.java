package br.edu.evalia.service;

import br.edu.evalia.model.BoardPosition;
import br.edu.evalia.model.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ChessGameReplay {
    private static final Pattern FEN_TAG = Pattern.compile("(?m)^\\[FEN\\s+\"([^\"]+)\"\\]");
    private final PgnMoveExtractor extractor = new PgnMoveExtractor();
    private final ChessRules rules = new ChessRules();

    public List<String> moves(Game game) { return extractor.extractSan(game.getPgn()); }

    public List<BoardPosition> positions(Game game) {
        var fen = FEN_TAG.matcher(game.getPgn());
        BoardPosition current = fen.find()
                ? new BoardPosition(fen.group(1), "Posicao inicial", 0, null) : BoardPosition.initial();
        List<BoardPosition> result = new ArrayList<>();
        result.add(current);
        for (String san : moves(game)) {
            try {
                current = rules.playSan(current, san);
                result.add(current);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("PGN invalido no meio-lance " + result.size()
                        + " (" + san + "): " + ex.getMessage(), ex);
            }
        }
        return List.copyOf(result);
    }

    public String fen(BoardPosition position, boolean ignored) { return position.fen(); }
}
