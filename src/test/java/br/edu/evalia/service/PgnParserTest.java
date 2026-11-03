package br.edu.evalia.service;

import br.edu.evalia.model.GameResult;
import br.edu.evalia.model.PieceColor;
import br.edu.evalia.model.SourcePlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PgnParserTest {
    @Test
    void parsesGameFromPlayersPerspective() {
        String pgn = """
                [Event "Rapid"]
                [Site "https://lichess.org/abc"]
                [Date "2026.09.14"]
                [White "Eduardo"]
                [Black "Rival"]
                [BlackElo "1750"]
                [Result "1-0"]
                [ECO "C50"]
                [Opening "Abertura Italiana"]

                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 1-0
                """;

        var games = new PgnParser().parse(pgn, "eduardo");

        assertEquals(1, games.size());
        assertEquals("Rival", games.get(0).getOpponent());
        assertEquals(1750, games.get(0).getOpponentRating());
        assertEquals(PieceColor.WHITE, games.get(0).getColor());
        assertEquals(GameResult.WIN, games.get(0).getResult());
        assertEquals(SourcePlatform.LICHESS, games.get(0).getSource());
        assertEquals(5, games.get(0).getMoves());
        assertEquals("C50", games.get(0).getEco());
        assertTrue(games.get(0).getPgn().contains("1. e4 e5"));
    }

    @Test
    void rejectsUnknownPlayer() {
        String pgn = "[White \"A\"]\n[Black \"B\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0";
        assertThrows(IllegalArgumentException.class, () -> new PgnParser().parse(pgn, "C"));
    }
}
