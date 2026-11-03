package br.edu.evalia.repository;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CsvGameRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void firstRealSyncReplacesDemoAndIgnoresDuplicates() throws Exception {
        Path file = tempDir.resolve("games.csv");
        var repository = new CsvGameRepository(file);
        assertTrue(repository.isDemoMode());

        Game realGame = new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 14),
                "Rival", 1804, 1790, PieceColor.WHITE, GameResult.WIN,
                "Abertura Italiana", 31, SourcePlatform.LICHESS);

        assertEquals(1, repository.saveAll(List.of(realGame, realGame)));
        assertFalse(repository.isDemoMode());
        assertTrue(repository.findAll().stream().anyMatch(game -> game.getOpponent().equals("Rival")));
        assertEquals(0, repository.saveAll(List.of(realGame)));
    }

    @Test
    void resetForNewAccountClearsAccountGamesAndNextSyncStartsFresh() throws Exception {
        Path file = tempDir.resolve("games.csv");
        var repository = new CsvGameRepository(file);
        Game firstAccountGame = new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 14),
                "Old", 1804, 1790, PieceColor.WHITE, GameResult.WIN,
                "Abertura Italiana", 31, SourcePlatform.LICHESS);
        Game secondAccountGame = new Game(UUID.randomUUID(), LocalDate.of(2026, 9, 15),
                "New", 1804, 1790, PieceColor.WHITE, GameResult.WIN,
                "Defesa Siciliana", 28, SourcePlatform.CHESS_COM);

        repository.saveAll(List.of(firstAccountGame));
        repository.resetForNewAccount();
        repository.saveAll(List.of(secondAccountGame));

        assertTrue(repository.findAll().stream().noneMatch(game -> game.getOpponent().equals("Old")));
        assertTrue(repository.findAll().stream().anyMatch(game -> game.getOpponent().equals("New")));
    }
}
