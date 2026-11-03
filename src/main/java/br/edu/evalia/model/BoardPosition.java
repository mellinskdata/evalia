package br.edu.evalia.model;

import java.util.Arrays;
import java.util.Objects;

/** Immutable snapshot including castling, en passant and both move counters. */
public final class BoardPosition {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final String fen;
    private final char[][] pieces = new char[8][8];
    private final String lastMove;
    private final int ply;
    private final Move move;

    public BoardPosition(String fen, String lastMove, int ply, Move move) {
        this.fen = validateFen(fen);
        this.lastMove = Objects.requireNonNullElse(lastMove, "");
        this.ply = Math.max(0, ply);
        this.move = move;
        String[] ranks = this.fen.split(" ")[0].split("/");
        for (int row = 0; row < 8; row++) {
            Arrays.fill(pieces[row], ' ');
            int col = 0;
            for (char c : ranks[row].toCharArray()) {
                if (Character.isDigit(c)) col += c - '0';
                else pieces[row][col++] = c;
            }
        }
    }

    public static BoardPosition initial() { return new BoardPosition(INITIAL_FEN, "Posicao inicial", 0, null); }

    public static String validateFen(String value) {
        if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("FEN invalida.");
        }
        String[] fields = value.trim().split("\\s+");
        if (fields.length != 6 || !fields[1].matches("[wb]")
                || !fields[2].matches("-|K?Q?k?q?") || fields[2].isEmpty()
                || !fields[3].matches("-|[a-h][36]") || !fields[4].matches("\\d+")
                || !fields[5].matches("[1-9]\\d*")) {
            throw new IllegalArgumentException("A FEN deve conter os seis campos validos.");
        }
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) throw new IllegalArgumentException("FEN: oito fileiras esperadas.");
        for (String rank : ranks) {
            int squares = 0;
            for (char c : rank.toCharArray()) {
                if (c >= '1' && c <= '8') squares += c - '0';
                else if ("KQRBNPkqrbnp".indexOf(c) >= 0) squares++;
                else throw new IllegalArgumentException("Peca invalida na FEN.");
            }
            if (squares != 8) throw new IllegalArgumentException("Fileira FEN invalida.");
        }
        if (fields[0].chars().filter(c -> c == 'K').count() != 1
                || fields[0].chars().filter(c -> c == 'k').count() != 1) {
            throw new IllegalArgumentException("A posicao deve conter um rei de cada cor.");
        }
        return String.join(" ", fields);
    }

    public char pieceAt(int row, int column) {
        return row < 0 || row > 7 || column < 0 || column > 7 ? ' ' : pieces[row][column];
    }
    public char[][] piecesCopy() {
        char[][] result = new char[8][];
        for (int row = 0; row < 8; row++) result[row] = pieces[row].clone();
        return result;
    }
    public String fen() { return fen; }
    public boolean whiteToMove() { return fen.split(" ")[1].equals("w"); }
    public int fullMoveNumber() { return Integer.parseInt(fen.split(" ")[5]); }
    public String lastMove() { return lastMove; }
    public int ply() { return ply; }
    public Move move() { return move; }
}
