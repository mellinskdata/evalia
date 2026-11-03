package br.edu.evalia.service;

import br.edu.evalia.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Builds an immutable position index once; transpositions do not depend on move order. */
public final class JsonOpeningCatalog {
    private final Map<String, List<OpeningEntry>> positions;
    private final Map<String, List<BookContinuation>> continuations;
    private final Map<String, List<ReferenceMoveStat>> statistics;
    private final int size;
    private final ChessRules rules = new ChessRules();

    public JsonOpeningCatalog(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Livro de aberturas nao encontrado.");
        JsonNode root = new ObjectMapper().readTree(stream);
        if (root.path("schemaVersion").asInt() != 1 || !root.path("entries").isArray()) {
            throw new IOException("Formato do livro JSON invalido.");
        }
        Map<String, List<OpeningEntry>> names = new HashMap<>();
        Map<String, Map<String, BoardPosition>> edges = new HashMap<>();
        Map<String, BoardPosition> prefixCache = new HashMap<>();
        PgnMoveExtractor extractor = new PgnMoveExtractor();
        int count = 0;
        for (JsonNode row : root.path("entries")) {
            String name = row.path("name").asText(), eco = row.path("eco").asText();
            if (name.isBlank() || !eco.matches("[A-E]\\d{2}")) throw new IOException("Nome/ECO invalido no livro.");
            List<String> moves = extractor.extractSan(row.path("pgn").asText());
            BoardPosition position = BoardPosition.initial();
            StringBuilder prefix = new StringBuilder();
            try {
                for (String san : moves) {
                    prefix.append(san).append(' ');
                    BoardPosition next = prefixCache.get(prefix.toString());
                    if (next == null) {
                        next = rules.playSan(position, san);
                        prefixCache.put(prefix.toString(), next);
                    }
                    edges.computeIfAbsent(rules.positionKey(position), key -> new HashMap<>())
                            .putIfAbsent(next.move().uci(), next);
                    position = next;
                }
                if (row.hasNonNull("fen")) {
                    BoardPosition declared = new BoardPosition(row.path("fen").asText(), "", 0, null);
                    if (!moves.isEmpty() && !rules.positionKey(position).equals(rules.positionKey(declared))) {
                        throw new IOException("FEN e lances divergem: " + name);
                    }
                    position = declared;
                } else if (moves.isEmpty()) throw new IOException("Abertura sem FEN ou lances: " + name);
            } catch (RuntimeException ex) { throw new IOException("Linha de abertura invalida: " + name, ex); }
            names.computeIfAbsent(rules.positionKey(position), key -> new ArrayList<>())
                    .add(new OpeningEntry(name, eco, moves, position.fen()));
            count++;
        }
        Map<String, List<OpeningEntry>> immutableNames = new HashMap<>();
        names.forEach((key, values) -> immutableNames.put(key, List.copyOf(values)));
        positions = Map.copyOf(immutableNames);
        Map<String, List<BookContinuation>> nextMoves = new HashMap<>();
        edges.forEach((key, destinations) -> nextMoves.put(key, destinations.values().stream().map(p -> {
            Optional<OpeningEntry> entry = lookup(p);
            return new BookContinuation(p.move(), p.lastMove(), entry.map(OpeningEntry::name).orElse("Continuacao do catalogo"),
                    entry.map(OpeningEntry::eco).orElse(""));
        }).sorted(Comparator.comparing(BookContinuation::san)).toList()));
        continuations = Map.copyOf(nextMoves);
        Map<String, List<ReferenceMoveStat>> stats = new HashMap<>();
        for (JsonNode row : root.path("statistics")) {
            BoardPosition p = new BoardPosition(row.path("fen").asText(), "", 0, null);
            String source = row.path("source").asText();
            List<Move> legal = rules.legalMoves(p);
            List<ReferenceMoveStat> rows = new ArrayList<>();
            for (JsonNode m : row.path("moves")) {
                Move move = new Move(m.path("uci").asText());
                if (!legal.contains(move)) throw new IOException("Estatistica com lance ilegal: " + move);
                for (String field : List.of("whiteWins", "draws", "blackWins")) {
                    if (!m.path(field).isIntegralNumber() || !m.path(field).canConvertToLong()) {
                        throw new IOException("Contagem ausente/invalida: " + field);
                    }
                }
                rows.add(new ReferenceMoveStat(move.uci(), m.path("whiteWins").asLong(),
                        m.path("draws").asLong(), m.path("blackWins").asLong(), source));
            }
            stats.put(rules.positionKey(p), List.copyOf(rows));
        }
        statistics = Map.copyOf(stats);
        size = count;
    }

    public static JsonOpeningCatalog bundled() { return Holder.INSTANCE; }
    private static final class Holder {
        static final JsonOpeningCatalog INSTANCE = load();
        private static JsonOpeningCatalog load() {
            Path custom = Path.of("data", "opening-book.json");
            try (var stream = Files.exists(custom) ? Files.newInputStream(custom)
                    : JsonOpeningCatalog.class.getResourceAsStream("/openings/opening-book.json")) {
                return new JsonOpeningCatalog(stream);
            } catch (IOException ex) { throw new UncheckedIOException(ex); }
        }
    }

    public int size() { return size; }
    public Optional<OpeningEntry> lookup(BoardPosition position) {
        return positions.getOrDefault(rules.positionKey(position), List.of()).stream().findFirst();
    }
    public List<BookContinuation> nextMoves(BoardPosition position) {
        return continuations.getOrDefault(rules.positionKey(position), List.of());
    }
    public List<ReferenceMoveStat> statistics(BoardPosition position) {
        return statistics.getOrDefault(rules.positionKey(position), List.of());
    }
}
