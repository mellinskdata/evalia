package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PgnParser {
    private static final Pattern TAG = Pattern.compile("(?m)^\\s*\\[([A-Za-z0-9_]+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\]");
    private static final Pattern MOVE_NUMBER = Pattern.compile("(?m)(\\d+)\\.(?:\\.\\.)?");

    public List<Game> parse(String content, String username) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Cole ao menos uma partida PGN.");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Informe seu usuario no PGN.");

        List<Game> games = new ArrayList<>();
        try (var reader = new PgnStreamReader(new java.io.StringReader(content))) {
            String block;
            while ((block = reader.nextGame()) != null) games.add(parseSingle(block, username.trim()));
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
        if (games.isEmpty()) throw new IllegalArgumentException("Nenhuma partida PGN valida foi encontrada.");
        return List.copyOf(games);
    }

    /** A null username is the database perspective (White), never a personal account. */
    public Game parseSingle(String block, String username) {
        Map<String, String> tags = readTags(block);
        if (!tags.containsKey("White") || !tags.containsKey("Black")) {
            throw new IllegalArgumentException("Tags White e Black obrigatorias.");
        }
        String variant = tags.getOrDefault("Variant", "Standard");
        if (!variant.equalsIgnoreCase("Standard") && !variant.equalsIgnoreCase("From Position")) {
            throw new IllegalArgumentException("Variante nao suportada: " + variant);
        }
        String result = tags.getOrDefault("Result", "*");
        if (!List.of("1-0", "0-1", "1/2-1/2").contains(result)) {
            throw new IllegalArgumentException("Partida sem resultado concluido.");
        }
        return toGame(block, tags, username == null ? tags.get("White") : username);
    }

    private Map<String, String> readTags(String block) {
        Map<String, String> tags = new HashMap<>();
        Matcher matcher = TAG.matcher(block);
        while (matcher.find()) tags.put(matcher.group(1), matcher.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        return tags;
    }

    private Game toGame(String block, Map<String, String> tags, String username) {
        boolean isWhite = username.equalsIgnoreCase(tags.get("White"));
        boolean isBlack = username.equalsIgnoreCase(tags.get("Black"));
        if (!isWhite && !isBlack) {
            throw new IllegalArgumentException("O usuario '" + username + "' nao aparece em uma das partidas.");
        }
        PieceColor color = isWhite ? PieceColor.WHITE : PieceColor.BLACK;
        String opponent = isWhite ? tags.get("Black") : tags.get("White");
        int playerRating = parseInt(isWhite ? tags.get("WhiteElo") : tags.get("BlackElo"));
        int opponentRating = parseInt(isWhite ? tags.get("BlackElo") : tags.get("WhiteElo"));
        GameResult result = perspectiveResult(tags.getOrDefault("Result", "1/2-1/2"), color);
        String eco = tags.getOrDefault("ECO", "");
        String opening = tags.getOrDefault("Opening", OpeningBook.nameForEco(eco)
                .orElse(tags.getOrDefault("ECO", "Abertura nao informada")));
        SourcePlatform source = detectSource(tags.getOrDefault("Site", ""));

        return new Game(UUID.randomUUID(), parseDate(tags.getOrDefault("Date", tags.get("UTCDate"))),
                opponent, playerRating, opponentRating,
                color, result, opening, countMoves(block), source, eco, block.trim(),
                tags.getOrDefault("Event", tags.getOrDefault("StudyName", "Partida PGN")),
                tags.getOrDefault("Site", tags.getOrDefault("ChapterURL", "")),
                tags.getOrDefault("White", "Brancas"),
                tags.getOrDefault("Black", "Pretas"),
                tags.getOrDefault("TimeControl", ""),
                false);
    }

    private GameResult perspectiveResult(String result, PieceColor color) {
        if ("1/2-1/2".equals(result) || "*".equals(result)) return GameResult.DRAW;
        boolean whiteWon = "1-0".equals(result);
        return whiteWon == (color == PieceColor.WHITE) ? GameResult.WIN : GameResult.LOSS;
    }

    private SourcePlatform detectSource(String site) {
        String value = site.toLowerCase(Locale.ROOT);
        if (value.contains("chess.com")) return SourcePlatform.CHESS_COM;
        if (value.contains("lichess")) return SourcePlatform.LICHESS;
        if (value.contains("fide")) return SourcePlatform.FIDE;
        return SourcePlatform.PGN;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.contains("?")) return LocalDate.now();
        try { return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy.MM.dd")); }
        catch (DateTimeParseException ex) { return LocalDate.now(); }
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ex) { return 0; }
    }

    private int countMoves(String block) {
        Matcher tagMatcher = TAG.matcher(block);
        int moveTextStart = 0;
        while (tagMatcher.find()) moveTextStart = tagMatcher.end();
        String moveText = block.substring(moveTextStart)
                .replaceAll("(?s)\\{.*?}", " ")
                .replaceAll("(?m);.*$", " ");
        Matcher matcher = MOVE_NUMBER.matcher(moveText);
        int max = 1;
        while (matcher.find()) max = Math.max(max, Integer.parseInt(matcher.group(1)));
        return max;
    }
}
