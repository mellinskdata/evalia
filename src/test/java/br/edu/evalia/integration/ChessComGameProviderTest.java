package br.edu.evalia.integration;

import br.edu.evalia.model.GameResult;
import br.edu.evalia.model.SourcePlatform;
import br.edu.evalia.service.PgnParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChessComGameProviderTest {
    @Test
    void downloadsMostRecentMonthlyGames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode archives = mapper.createObjectNode();
        archives.putArray("archives")
                .add("https://api.chess.com/pub/player/eduardo/games/2026/08")
                .add("https://api.chess.com/pub/player/eduardo/games/2026/09");

        ObjectNode month = mapper.createObjectNode();
        ArrayNode games = month.putArray("games");
        games.addObject()
                .put("rules", "chess")
                .put("pgn", pgn("Chess.com", "Eduardo", "Rival", "1-0"));

        HttpGateway fakeHttp = (URI uri, String accept) -> {
            if (uri.toString().endsWith("/archives"))
                return new HttpResult(200, mapper.writeValueAsString(archives));
            if (uri.toString().endsWith("/2026/09"))
                return new HttpResult(200, mapper.writeValueAsString(month));
            return new HttpResult(200, "{\"games\":[]}");
        };

        var provider = new ChessComGameProvider(fakeHttp, mapper, new PgnParser());
        var result = provider.fetchGames("Eduardo", 10);

        assertEquals(1, result.size());
        assertEquals(SourcePlatform.CHESS_COM, result.get(0).getSource());
        assertEquals(GameResult.WIN, result.get(0).getResult());
    }

    private String pgn(String site, String white, String black, String result) {
        return """
                [Event "Rapid"]
                [Site "%s"]
                [Date "2026.09.14"]
                [White "%s"]
                [Black "%s"]
                [WhiteElo "1804"]
                [BlackElo "1790"]
                [Result "%s"]
                [Opening "Abertura Italiana"]

                1. e4 e5 2. Nf3 Nc6 3. Bc4  %s
                """.formatted(site, white, black, result, result);
    }
}
