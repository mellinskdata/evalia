package br.edu.evalia.service;

import br.edu.evalia.model.BoardPosition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockfishServiceTest {
    @Test
    void parsesSinglePvAndMultiPvAndRestoresDefault() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java")
                .toAbsolutePath().toString();
        String testClasses = Path.of("target", "test-classes").toAbsolutePath().toString();
        List<String> command = List.of(java, "-cp", testClasses, FakeUciEngine.class.getName());

        try (StockfishService engine = new StockfishService(command, Duration.ofSeconds(3))) {
            var single = engine.analyze(BoardPosition.INITIAL_FEN, 12, Duration.ofSeconds(3), () -> false);
            assertEquals(35, single.centipawns());
            assertTrue(single.bestMove().isPresent());
            assertEquals("e2e4", single.bestMove().orElseThrow().uci());

            var lines = engine.analyzeMultiPv(BoardPosition.INITIAL_FEN, 10, 2,
                    Duration.ofSeconds(3), () -> false);
            assertEquals(2, lines.size());
            assertEquals(180, lines.get(0).centipawns());
            assertEquals(20, lines.get(1).centipawns());

            var afterReset = engine.analyze(BoardPosition.INITIAL_FEN, 8, Duration.ofSeconds(3), () -> false);
            assertEquals(35, afterReset.centipawns());
        }
    }
}
