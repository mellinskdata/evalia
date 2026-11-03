package br.edu.evalia.integration;

import br.edu.evalia.model.Game;
import br.edu.evalia.model.SourcePlatform;

import java.io.IOException;
import java.util.List;

public interface GameProvider {
    SourcePlatform platform();
    List<Game> fetchGames(String username, int limit)
            throws IOException, InterruptedException, PlatformApiException;
}
