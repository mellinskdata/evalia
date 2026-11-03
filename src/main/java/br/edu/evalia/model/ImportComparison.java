package br.edu.evalia.model;

public record ImportComparison(int previousGames,
                               int currentGames,
                               double previousScoreRate,
                               double currentScoreRate) {
    public String summary() {
        int deltaGames = currentGames - previousGames;
        double deltaScore = currentScoreRate - previousScoreRate;
        String signal = deltaScore >= 0 ? "+" : "";
        return deltaGames + " partida(s) adicionada(s). Aproveitamento: "
                + "%.1f%%".formatted(previousScoreRate) + " -> "
                + "%.1f%%".formatted(currentScoreRate) + " (" + signal
                + "%.1f".formatted(deltaScore) + " pp).";
    }
}
