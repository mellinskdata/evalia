package br.edu.evalia.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Game {
    private final UUID id;
    private final LocalDate date;
    private final String opponent;
    private final int playerRating;
    private final int opponentRating;
    private final PieceColor color;
    private final GameResult result;
    private final String opening;
    private final int moves;
    private final SourcePlatform source;
    private final String eco;
    private final String pgn;
    private final String event;
    private final String site;
    private final String whitePlayer;
    private final String blackPlayer;
    private final String timeControl;
    private final boolean featured;

    public Game(UUID id, LocalDate date, String opponent, int playerRating, int opponentRating,
                PieceColor color, GameResult result, String opening, int moves,
                SourcePlatform source) {
        this(id, date, opponent, playerRating, opponentRating, color, result, opening, moves, source, "", "");
    }

    public Game(UUID id, LocalDate date, String opponent, int playerRating, int opponentRating,
                PieceColor color, GameResult result, String opening, int moves,
                SourcePlatform source, String eco, String pgn) {
        this(id, date, opponent, playerRating, opponentRating, color, result, opening, moves, source,
                eco, pgn, "Partida sem evento", "", color == PieceColor.WHITE ? "Jogador" : opponent,
                color == PieceColor.WHITE ? opponent : "Jogador", "", false);
    }

    public Game(UUID id, LocalDate date, String opponent, int playerRating, int opponentRating,
                PieceColor color, GameResult result, String opening, int moves,
                SourcePlatform source, String eco, String pgn, String event, String site,
                String whitePlayer, String blackPlayer, String timeControl, boolean featured) {
        this.id = Objects.requireNonNull(id);
        this.date = Objects.requireNonNull(date);
        this.opponent = requireText(opponent, "Adversario");
        this.playerRating = Math.max(0, playerRating);
        this.opponentRating = Math.max(0, opponentRating);
        this.color = Objects.requireNonNull(color);
        this.result = Objects.requireNonNull(result);
        this.opening = requireText(opening, "Abertura");
        this.moves = Math.max(1, moves);
        this.source = Objects.requireNonNull(source);
        this.eco = eco == null ? "" : eco.trim();
        this.pgn = pgn == null ? "" : pgn.trim();
        this.event = textOrDefault(event, "Partida sem evento");
        this.site = site == null ? "" : site.trim();
        this.whitePlayer = textOrDefault(whitePlayer, color == PieceColor.WHITE ? "Jogador" : opponent);
        this.blackPlayer = textOrDefault(blackPlayer, color == PieceColor.WHITE ? opponent : "Jogador");
        this.timeControl = timeControl == null ? "" : timeControl.trim();
        this.featured = featured;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " e obrigatorio.");
        return value.trim();
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getOpponent() { return opponent; }
    public int getPlayerRating() { return playerRating; }
    public int getOpponentRating() { return opponentRating; }
    public PieceColor getColor() { return color; }
    public GameResult getResult() { return result; }
    public String getOpening() { return opening; }
    public int getMoves() { return moves; }
    public SourcePlatform getSource() { return source; }
    public String getEco() { return eco; }
    public String getPgn() { return pgn; }
    public String getEvent() { return event; }
    public String getSite() { return site; }
    public String getWhitePlayer() { return whitePlayer; }
    public String getBlackPlayer() { return blackPlayer; }
    public String getTimeControl() { return timeControl; }
    public boolean isFeatured() { return featured; }
    public boolean hasPgn() { return !pgn.isBlank(); }

    public String fingerprint() {
        String identity = String.join("|", date.toString(), whitePlayer, blackPlayer,
                color.name(), result.name(), hasPgn() ? pgn.replace("\r\n", "\n") : opening,
                String.valueOf(moves), source.name());
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
