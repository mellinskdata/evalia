package br.edu.evalia.service;

import br.edu.evalia.model.Game;
import br.edu.evalia.model.GameResult;

import java.util.List;

final class ScoreMath {
    private ScoreMath() { }

    static double resultRate(List<Game> games) {
        if (games.isEmpty()) return 0.5;
        double points = games.stream().mapToDouble(g -> switch (g.getResult()) {
            case WIN -> 1.0;
            case DRAW -> 0.5;
            case LOSS -> 0.0;
        }).sum();
        return points / games.size();
    }

    static double winRate(List<Game> games) {
        if (games.isEmpty()) return 0.5;
        return games.stream().filter(g -> g.getResult() == GameResult.WIN).count() / (double) games.size();
    }

    static int clamp(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }
}
