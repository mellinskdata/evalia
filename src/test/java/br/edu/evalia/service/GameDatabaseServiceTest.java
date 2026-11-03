package br.edu.evalia.service;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameDatabaseServiceTest {
    @Test
    void searchesByPlayerTournamentOpeningAndMoveText() {
        Game game = new Game(UUID.randomUUID(), LocalDate.of(2020, 6, 12),
                "Nathan Filgueiras", 2134, 2405, PieceColor.WHITE, GameResult.WIN,
                "King's Indian Defense", 47, SourcePlatform.PGN, "E98",
                "[Event \"Campeonato Brasileiro sub 18\"]\n[White \"Eduardo Mello da Costa\"]\n[Black \"Nathan Filgueiras\"]\n\n1. d4 Nf6 2. Nf3 g6 3. c4 Bg7",
                "Campeonato Brasileiro sub 18", "https://lichess.org/study/demo",
                "Eduardo Mello da Costa", "Nathan Filgueiras", "120+45", true);

        List<Game> found = new GameDatabaseService().search(List.of(game),
                new GameSearchCriteria("Eduardo", "Nf3", "E98", "Brasileiro", "Vitoria"));

        assertEquals(1, found.size());
    }
}
