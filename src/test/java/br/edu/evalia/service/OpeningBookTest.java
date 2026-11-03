package br.edu.evalia.service;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpeningBookTest {
    @Test
    void groupsDifferentSicilianEcoCodesIntoSameFamily() {
        OpeningBook book = new OpeningBook();
        Game najdorf = game("Defesa Siciliana Najdorf", "B90", GameResult.WIN);
        Game dragon = game("Defesa Siciliana Dragao", "B76", GameResult.LOSS);

        List<OpeningStat> stats = book.summarize(List.of(najdorf, dragon));

        assertEquals(1, stats.size());
        assertEquals("Defesa Siciliana", stats.get(0).family().name());
        assertEquals("B20-B99", stats.get(0).family().ecoRange());
        assertEquals(2, stats.get(0).games());
    }

    private Game game(String opening, String eco, GameResult result) {
        return new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 14), "Rival",
                1800, 1800, PieceColor.WHITE, result, opening, 30, SourcePlatform.PGN, eco, "");
    }
}
