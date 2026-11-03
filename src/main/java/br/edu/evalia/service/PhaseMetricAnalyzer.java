package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.List;

public final class PhaseMetricAnalyzer implements MetricAnalyzer {
    private final PerformanceMetric metric;

    public PhaseMetricAnalyzer(PerformanceMetric metric) {
        this.metric = metric;
    }

    @Override public PerformanceMetric metric() { return metric; }

    @Override public int analyze(List<Game> games) {
        double result = ScoreMath.resultRate(games);
        double win = ScoreMath.winRate(games);
        double longRate = games.isEmpty() ? .5 : games.stream().filter(g -> g.getMoves() >= 45).count() / (double) games.size();
        double shortWinRate = games.isEmpty() ? .5 : games.stream()
                .filter(g -> g.getMoves() <= 35 && g.getResult() == GameResult.WIN).count() / (double) games.size();

        return switch (metric) {
            case OPENING -> ScoreMath.clamp(66 + result * 25 + shortWinRate * 10);
            case TACTICS -> ScoreMath.clamp(55 + win * 32 + shortWinRate * 20);
            case STRATEGY -> ScoreMath.clamp(59 + result * 34);
            case ENDGAME -> ScoreMath.clamp(50 + longRate * 25 + result * 20);
            case DEFENSE -> ScoreMath.clamp(54 + result * 31 + longRate * 8);
            case CONVERSION -> ScoreMath.clamp(52 + win * 47);
            case TIME_MANAGEMENT -> ScoreMath.clamp(63 + Math.min(games.size(), 20) * .7 - shortWinRate * 4);
            default -> throw new IllegalStateException("Metrica de fase invalida: " + metric);
        };
    }
}
