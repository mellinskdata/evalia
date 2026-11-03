package br.edu.evalia.integration;

import br.edu.evalia.model.SourcePlatform;
import br.edu.evalia.service.PgnParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LichessGameProviderTest {
    @Test
    void requestsPgnAndParsesPublicGames() throws Exception {
        String pgn = """
                [Event "Rated Rapid game"]
                [Site "https://lichess.org/abc123"]
                [Date "2026.09.14"]
                [White "Rival"]
                [Black "eduardo"]
                [WhiteElo "1790"]
                [BlackElo "1804"]
                [Result "0-1"]
                [Opening "Defesa Caro-Kann"]

                1. e4 c6 2. d4 d5 3. e5 Bf5 0-1
                """;
        HttpGateway fakeHttp = (uri, accept) -> {
            assertTrue(uri.toString().contains("max=25"));
            assertEquals("application/x-chess-pgn", accept);
            return new HttpResult(200, pgn);
        };

        var result = new LichessGameProvider(fakeHttp, new PgnParser())
                .fetchGames("eduardo", 25);

        assertEquals(1, result.size());
        assertEquals(SourcePlatform.LICHESS, result.get(0).getSource());
        assertEquals(1804, result.get(0).getPlayerRating());
    }
}
