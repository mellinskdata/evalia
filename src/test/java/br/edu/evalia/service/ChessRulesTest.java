package br.edu.evalia.service;

import br.edu.evalia.model.BoardPosition;
import br.edu.evalia.model.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChessRulesTest {
    private final ChessRules rules = new ChessRules();

    @Test
    void supportsCastlingWithTheSameApiUsedByTheInteractiveBoard() {
        BoardPosition p = BoardPosition.initial();
        for (String uci : new String[]{"e2e4", "e7e5", "g1f3", "b8c6", "f1e2", "g8f6", "e1g1"}) {
            p = rules.play(p, new Move(uci));
        }
        assertEquals('K', p.pieceAt(7, 6));
        assertEquals('R', p.pieceAt(7, 5));
        assertEquals(' ', p.pieceAt(7, 4));
        assertEquals(' ', p.pieceAt(7, 7));
    }

    @Test
    void supportsEnPassant() {
        BoardPosition p = new BoardPosition("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "", 0, null);
        p = rules.play(p, new Move("e5d6"));
        assertEquals('P', p.pieceAt(2, 3));
        assertEquals(' ', p.pieceAt(3, 3));
    }

    @Test
    void supportsPromotion() {
        BoardPosition p = new BoardPosition("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "", 0, null);
        p = rules.play(p, new Move("a7a8q"));
        assertEquals('Q', p.pieceAt(0, 0));
    }
}
