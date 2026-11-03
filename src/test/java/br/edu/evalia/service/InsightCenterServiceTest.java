package br.edu.evalia.service;

import br.edu.evalia.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InsightCenterServiceTest {
    @Test
    void usesRatingExpectationAndReliableSamplesInDiagnosis() {
        List<Game> games = List.of(
                game(0, PieceColor.WHITE, GameResult.WIN, "C50", "Abertura Italiana", 1800, 1810),
                game(1, PieceColor.WHITE, GameResult.WIN, "C50", "Abertura Italiana", 1810, 1820),
                game(2, PieceColor.WHITE, GameResult.DRAW, "C50", "Abertura Italiana", 1820, 1830),
                game(3, PieceColor.WHITE, GameResult.WIN, "C50", "Abertura Italiana", 1830, 1840),
                game(4, PieceColor.WHITE, GameResult.WIN, "C50", "Abertura Italiana", 1840, 1900),
                game(5, PieceColor.BLACK, GameResult.LOSS, "B90", "Defesa Siciliana", 1845, 1800),
                game(6, PieceColor.BLACK, GameResult.LOSS, "B90", "Defesa Siciliana", 1835, 1800),
                game(7, PieceColor.BLACK, GameResult.DRAW, "B90", "Defesa Siciliana", 1825, 1810),
                game(8, PieceColor.BLACK, GameResult.LOSS, "B90", "Defesa Siciliana", 1815, 1780),
                game(9, PieceColor.BLACK, GameResult.LOSS, "B90", "Defesa Siciliana", 1805, 1760));
        InsightCenterService service = new InsightCenterService();
        List<PerformanceScore> fallback = List.of(
                new PerformanceScore(PerformanceMetric.STRATEGY, 55),
                new PerformanceScore(PerformanceMetric.TACTICS, 45));

        PlayerDiagnosis diagnosis = service.diagnose(games, fallback);

        assertTrue(service.expectedScoreRate(games) > 0);
        assertFalse(service.actionableInsights(games).isEmpty());
        assertTrue(diagnosis.mainStrength().contains("Brancas")
                || diagnosis.mainStrength().contains("Italiana")
                || diagnosis.mainStrength().contains("rating"));
        assertTrue(diagnosis.mainWeakness().contains("Pretas")
                || diagnosis.mainWeakness().contains("Siciliana")
                || diagnosis.mainWeakness().contains("rating"));
        assertTrue(diagnosis.bestOpening().contains("Italiana"));
        assertTrue(diagnosis.worstOpening().contains("Siciliana"));
    }

    private Game game(int day, PieceColor color, GameResult result, String eco, String opening,
                      int playerRating, int opponentRating) {
        return new Game(UUID.randomUUID(), LocalDate.of(2026, 10, 1).plusDays(day), "Rival " + day,
                playerRating, opponentRating, color, result, opening, day < 3 ? 24 : 42,
                SourcePlatform.PGN, eco, "");
    }
}
