package br.edu.evalia.service;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class PgnStreamReaderTest {
    @Test
    void streamsGamesWithoutSplittingHeadersInsideComments() throws Exception {
        String pgn = """
                [Event "One"]
                [White "A"]
                [Black "B"]
                [Result "1-0"]

                1. e4 { comentario [nao e cabecalho] } e5 1-0
                [Event "Two"]
                [White "C"]
                [Black "D"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;

        try (PgnStreamReader reader = new PgnStreamReader(new StringReader(pgn))) {
            String first = reader.nextGame();
            String second = reader.nextGame();
            assertNotNull(first);
            assertNotNull(second);
            assertTrue(first.contains("[Event \"One\"]"));
            assertTrue(first.contains("[nao e cabecalho]"));
            assertTrue(second.contains("[Event \"Two\"]"));
            assertNull(reader.nextGame());
        }
    }
}
