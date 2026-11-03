package br.edu.evalia.service;

import br.edu.evalia.model.AnalysisInsight;
import br.edu.evalia.model.Game;
import br.edu.evalia.model.PerformanceScore;

import java.util.List;

public record DashboardSnapshot(
        int totalGames,
        int wins,
        int draws,
        int losses,
        int currentRating,
        double winRate,
        String bestOpening,
        List<PerformanceScore> scores,
        List<AnalysisInsight> insights,
        List<Game> recentGames) {
    public DashboardSnapshot {
        scores = List.copyOf(scores);
        insights = List.copyOf(insights);
        recentGames = List.copyOf(recentGames);
    }
}
