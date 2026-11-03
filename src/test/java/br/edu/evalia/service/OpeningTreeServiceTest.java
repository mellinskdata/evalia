package br.edu.evalia.service;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpeningTreeServiceTest {
    @Test
    void returnsNextMoveStatisticsFromCurrentLine() {
        Game game = new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 14),
                "Rival", 1800, 1800, PieceColor.WHITE, GameResult.WIN,
                "Abertura Italiana", 5, SourcePlatform.PGN, "C50",
                "[White \"Eduardo\"]\n[Black \"Rival\"]\n[Result \"1-0\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 1-0");

        List<OpeningTreeRow> root = new OpeningTreeService().nextMoves(List.of(game), List.of());
        List<OpeningTreeRow> afterE4 = new OpeningTreeService().nextMoves(List.of(game), List.of("e4"));

        assertEquals("e4", root.get(0).move());
        assertEquals("e5", afterE4.get(0).move());
        assertEquals(1, afterE4.get(0).games());
    }
}
