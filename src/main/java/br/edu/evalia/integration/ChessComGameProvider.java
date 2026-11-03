package br.edu.evalia.integration;

import br.edu.evalia.model.Game;
import br.edu.evalia.model.SourcePlatform;
import br.edu.evalia.service.PgnParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChessComGameProvider implements GameProvider {
    private static final String API = "https://api.chess.com/pub/player/";
    private final HttpGateway http;
    private final ObjectMapper json;
    private final PgnParser parser;

    public ChessComGameProvider() {
        this(new JdkHttpGateway(), new ObjectMapper(), new PgnParser());
    }

    public ChessComGameProvider(HttpGateway http, ObjectMapper json, PgnParser parser) {
        this.http = http;
        this.json = json;
        this.parser = parser;
    }

    @Override public SourcePlatform platform() { return SourcePlatform.CHESS_COM; }

    @Override
    public List<Game> fetchGames(String username, int limit)
            throws IOException, InterruptedException, PlatformApiException {
        validate(username, limit);
        String encoded = URLEncoder.encode(username.trim().toLowerCase(), StandardCharsets.UTF_8);
        HttpResult archivesResponse = http.get(URI.create(API + encoded + "/games/archives"), "application/json");
        requireSuccess(archivesResponse, username);

        JsonNode archivesNode = json.readTree(archivesResponse.body()).path("archives");
        if (!archivesNode.isArray() || archivesNode.isEmpty()) return List.of();

        List<String> archives = new ArrayList<>();
        archivesNode.forEach(node -> archives.add(node.asText()));
        Collections.reverse(archives);

        List<Game> imported = new ArrayList<>();
        for (String archiveUrl : archives) {
            HttpResult monthResponse = http.get(URI.create(archiveUrl), "application/json");
            requireSuccess(monthResponse, username);
            JsonNode games = json.readTree(monthResponse.body()).path("games");
            for (int i = games.size() - 1; i >= 0 && imported.size() < limit; i--) {
                JsonNode game = games.get(i);
                if (!"chess".equals(game.path("rules").asText("chess"))) continue;
                String pgn = game.path("pgn").asText("");
                if (!pgn.isBlank()) imported.addAll(parser.parse(pgn, username));
            }
            if (imported.size() >= limit) break;
        }
        return imported.stream().limit(limit).toList();
    }

    private void validate(String username, int limit) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_-]{2,30}"))
            throw new IllegalArgumentException("Informe um usuário válido do Chess.com.");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("O limite deve ficar entre 1 e 500.");
    }

    private void requireSuccess(HttpResult response, String username) throws PlatformApiException {
        if (response.statusCode() == 200) return;
        if (response.statusCode() == 404)
            throw new PlatformApiException("Usuário '" + username + "' não encontrado no Chess.com.");
        if (response.statusCode() == 429)
            throw new PlatformApiException("O Chess.com limitou as consultas. Aguarde um minuto e tente novamente.");
        throw new PlatformApiException("Chess.com respondeu com o codigo HTTP " + response.statusCode() + ".");
    }
}
