package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.List;

public final class ColorMetricAnalyzer implements MetricAnalyzer {
    private final PieceColor color;

    public ColorMetricAnalyzer(PieceColor color) { this.color = color; }

    @Override public PerformanceMetric metric() {
        return color == PieceColor.WHITE ? PerformanceMetric.WHITE : PerformanceMetric.BLACK;
    }

    @Override public int analyze(List<Game> games) {
        List<Game> selected = games.stream().filter(g -> g.getColor() == color).toList();
        return ScoreMath.clamp(45 + ScoreMath.resultRate(selected) * 50);
    }
}
