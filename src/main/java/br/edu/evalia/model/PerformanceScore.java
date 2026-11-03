package br.edu.evalia.model;

public record PerformanceScore(PerformanceMetric metric, int value) {
    public PerformanceScore {
        if (metric == null) throw new IllegalArgumentException("A metrica e obrigatoria.");
        if (value < 0 || value > 100) throw new IllegalArgumentException("O valor deve estar entre 0 e 100.");
    }
}
