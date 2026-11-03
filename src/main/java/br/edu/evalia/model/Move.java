package br.edu.evalia.model;

import java.util.Locale;

/** Immutable UCI move, independent from chesslib's mutable board. */
public record Move(String uci) {
    public Move {
        if (uci == null) throw new IllegalArgumentException("Lance UCI obrigatorio.");
        uci = uci.toLowerCase(Locale.ROOT);
        if (!uci.matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
            throw new IllegalArgumentException("Lance UCI invalido: " + uci);
        }
    }
    public String from() { return uci.substring(0, 2); }
    public String to() { return uci.substring(2, 4); }
}
