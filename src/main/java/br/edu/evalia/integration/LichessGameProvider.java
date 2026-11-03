package br.edu.evalia.integration;

import br.edu.evalia.model.Game;
import br.edu.evalia.model.SourcePlatform;
import br.edu.evalia.service.PgnParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class LichessGameProvider implements GameProvider {
    private static final String API = "https://lichess.org/api/games/user/";
    private final HttpGateway http;
    private final PgnParser parser;

    public LichessGameProvider() {
        this(new JdkHttpGateway(), new PgnParser());
    }

    public LichessGameProvider(HttpGateway http, PgnParser parser) {
        this.http = http;
        this.parser = parser;
    }

    @Override public SourcePlatform platform() { return SourcePlatform.LICHESS; }

    @Override
    public List<Game> fetchGames(String username, int limit)
            throws IOException, InterruptedException, PlatformApiException {
        validate(username, limit);
        String encoded = URLEncoder.encode(username.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(API + encoded + "?max=" + limit
                + "&opening=true&clocks=false&evals=false");
        HttpResult response = http.get(uri, "application/x-chess-pgn");
        requireSuccess(response, username);
        if (response.body().isBlank()) return List.of();
        return parser.parse(response.body(), username).stream().limit(limit).toList();
    }

    private void validate(String username, int limit) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_-]{2,30}"))
            throw new IllegalArgumentException("Informe um usuário válido do Lichess.");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("O limite deve ficar entre 1 e 500.");
    }

    private void requireSuccess(HttpResult response, String username) throws PlatformApiException {
        if (response.statusCode() == 200) return;
        if (response.statusCode() == 404)
            throw new PlatformApiException("Usuário '" + username + "' não encontrado no Lichess.");
        if (response.statusCode() == 429)
            throw new PlatformApiException("O Lichess limitou as consultas. Aguarde um minuto e tente novamente.");
        throw new PlatformApiException("Lichess respondeu com o codigo HTTP " + response.statusCode() + ".");
    }
}
