package br.edu.evalia.model;

public enum GameResult {
    WIN("Vitoria"), DRAW("Empate"), LOSS("Derrota");

    private final String label;

    GameResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
