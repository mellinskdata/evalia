package br.edu.evalia.model;

public enum SourcePlatform {
    CHESS_COM("Chess.com"), LICHESS("Lichess"), FIDE("FIDE"), PGN("PGN");

    private final String label;

    SourcePlatform(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
