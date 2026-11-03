package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import br.edu.evalia.repository.GameRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GameDatabaseService {
    private final ChessGameReplay replay = new ChessGameReplay();

    public BatchImportResult importPgn(Path path, GameRepository repository, String username,
                                       BooleanSupplier cancelled, Consumer<BatchImportResult> progress) throws IOException {
        long read = 0, added = 0, rejected = 0, accepted = 0;
        List<Game> batch = new ArrayList<>(250);
        List<String> warnings = new ArrayList<>();
        PgnParser parser = new PgnParser();
        boolean stopped = false;
        try (var reader = new PgnStreamReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            String block;
            while (!cancelled.getAsBoolean() && (block = reader.nextGame()) != null) {
                read++;
                try {
                    Game game = parser.parseSingle(block, username);
                    replay.positions(game); // Reject corrupt/illegal games before making them searchable.
                    batch.add(game);
                    accepted++;
                } catch (IllegalArgumentException ex) {
                    rejected++;
                    if (warnings.size() < 20) warnings.add("Partida " + read + ": " + ex.getMessage());
                }
                if (batch.size() == 250) {
                    added += repository.saveAll(batch);
                    batch.clear();
                }
                if (read % 250 == 0) progress.accept(new BatchImportResult(read, added,
                        accepted - added - batch.size(), rejected, false, warnings));
            }
            stopped = cancelled.getAsBoolean();
        }
        if (!stopped) { added += repository.saveAll(batch); batch.clear(); }
        BatchImportResult result = new BatchImportResult(read, added,
                accepted - added - batch.size(), rejected, stopped, warnings);
        progress.accept(result);
        return result;
    }

    public record SearchPage(List<Game> games, long matches) {
        public SearchPage { games = List.copyOf(games); }
    }

    public SearchPage search(GameRepository repository, GameSearchCriteria criteria, int limit) {
        List<Game> found = new ArrayList<>();
        long[] matches = {0};
        repository.forEach(game -> {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            if (matchesPlayer(game, criteria.player())
                    && (contains(game.getOpening(), criteria.opening()) || contains(game.getEco(), criteria.opening()))
                    && contains(game.getEvent(), criteria.tournament()) && matchesResult(game, criteria.result())
                    && matchesPosition(game, criteria.position())) {
                matches[0]++;
                if (found.size() < limit) found.add(game);
            }
        });
        return new SearchPage(found, matches[0]);
    }

    public List<Game> search(List<Game> games, GameSearchCriteria criteria) {
        return games.stream()
                .filter(game -> matchesPlayer(game, criteria.player()))
                .filter(game -> contains(game.getOpening(), criteria.opening())
                        || contains(game.getEco(), criteria.opening()))
                .filter(game -> contains(game.getEvent(), criteria.tournament()))
                .filter(game -> matchesResult(game, criteria.result()))
                .filter(game -> matchesPosition(game, criteria.position()))
                .sorted(Comparator.comparing(Game::isFeatured).reversed()
                        .thenComparing(Game::getDate).reversed())
                .toList();
    }

    private boolean matchesPlayer(Game game, String query) {
        if (blank(query)) return true;
        return contains(game.getWhitePlayer(), query)
                || contains(game.getBlackPlayer(), query)
                || contains(game.getOpponent(), query);
    }

    private boolean matchesResult(Game game, String result) {
        if (blank(result) || "Todos".equalsIgnoreCase(result)) return true;
        return contains(game.getResult().getLabel(), result);
    }

    private boolean matchesPosition(Game game, String query) {
        if (blank(query)) return true;
        String normalized = normalize(query);
        if (normalize(game.getPgn()).contains(normalized)) return true;
        List<BoardPosition> positions;
        try { positions = replay.positions(game); }
        catch (IllegalArgumentException ex) { return false; }
        for (int i = 0; i < positions.size(); i++) {
            BoardPosition position = positions.get(i);
            String fen = replay.fen(position, i % 2 == 0);
            if (normalize(fen).contains(normalized)) return true;
            if (normalize(fen.split(" ")[0]).contains(normalized)) return true;
        }
        return false;
    }

    private boolean contains(String value, String query) {
        return blank(query) || normalize(value).contains(normalize(query));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
