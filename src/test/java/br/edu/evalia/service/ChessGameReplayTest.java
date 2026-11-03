package br.edu.evalia.service;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChessGameReplayTest {
    @Test
    void replaysBasicOpeningMovesOnBoard() {
        String pgn = """
                [Event "Replay"]
                [Site "https://lichess.org/demo"]
                [Date "2026.09.14"]
                [White "Eduardo"]
                [Black "Rival"]
                [Result "1-0"]
                [ECO "C50"]
                [Opening "Abertura Italiana"]

                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 1-0
                """;
        Game game = new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 14), "Rival",
                1800, 1800, PieceColor.WHITE, GameResult.WIN, "Abertura Italiana",
                3, SourcePlatform.PGN, "C50", pgn);

        BoardPosition finalPosition = new ChessGameReplay().positions(game).get(6);

        assertEquals('P', finalPosition.pieceAt(4, 4));
        assertEquals('p', finalPosition.pieceAt(3, 4));
        assertEquals('N', finalPosition.pieceAt(5, 5));
        assertEquals('n', finalPosition.pieceAt(2, 2));
        assertEquals('B', finalPosition.pieceAt(4, 2));
        assertEquals('b', finalPosition.pieceAt(3, 2));
    }
}
