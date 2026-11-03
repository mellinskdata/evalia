package br.edu.evalia.model;

public enum PieceColor {
    WHITE("Brancas"), BLACK("Pretas");

    private final String label;

    PieceColor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
