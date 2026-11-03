package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public final class InsightCenterService {
    private final OpeningBook openingBook = new OpeningBook();
    private final GameReviewService reviewService = new GameReviewService();

    public List<Game> filterByPeriod(List<Game> games, String period) {
        List<Game> sorted = games.stream()
                .sorted(Comparator.comparing(Game::getDate).reversed())
                .toList();
        if ("Ultimas 10".equals(period)) return sorted.stream().limit(10).toList();
        if ("Ultimas 30".equals(period)) return sorted.stream().limit(30).toList();
        if ("Ultimos 90 dias".equals(period)) {
            LocalDate cutoff = LocalDate.now().minusDays(90);
            return sorted.stream().filter(game -> !game.getDate().isBefore(cutoff)).toList();
        }
        return sorted;
    }

    public PlayerDiagnosis diagnose(List<Game> games, List<PerformanceScore> scores) {
        PerformanceScore weakestFallback = scores.stream().min(Comparator.comparingInt(PerformanceScore::value))
                .orElse(new PerformanceScore(PerformanceMetric.STRATEGY, 0));
        PerformanceScore strongestFallback = scores.stream().max(Comparator.comparingInt(PerformanceScore::value))
                .orElse(new PerformanceScore(PerformanceMetric.STRATEGY, 0));

        List<OpeningStat> openings = openingStats(games);
        OpeningStat best = openings.stream()
                .filter(stat -> stat.games() >= 3)
                .max(Comparator.comparingDouble(this::adjustedOpeningScore))
                .orElse(null);
        OpeningStat worst = openings.stream()
                .filter(stat -> stat.games() >= 3)
                .min(Comparator.comparingDouble(this::adjustedOpeningScore))
                .orElse(null);

        List<Evidence> evidence = new ArrayList<>();
        List<Game> whiteGames = games.stream().filter(g -> g.getColor() == PieceColor.WHITE).toList();
        List<Game> blackGames = games.stream().filter(g -> g.getColor() == PieceColor.BLACK).toList();
        if (whiteGames.size() >= 4) evidence.add(new Evidence("Brancas", adjustedScoreRate(whiteGames), whiteGames.size(), scoreRate(whiteGames)));
        if (blackGames.size() >= 4) evidence.add(new Evidence("Pretas", adjustedScoreRate(blackGames), blackGames.size(), scoreRate(blackGames)));
        openings.stream().filter(stat -> stat.games() >= 4).forEach(stat ->
                evidence.add(new Evidence(stat.family().name(), adjustedOpeningScore(stat), stat.games(), stat.scoreRate())));

        Evidence strongestEvidence = evidence.stream().max(Comparator.comparingDouble(Evidence::adjustedScore)).orElse(null);
        Evidence weakestEvidence = evidence.stream().min(Comparator.comparingDouble(Evidence::adjustedScore)).orElse(null);
        double versusExpected = performanceVsExpected(games);
        long ratedCount = games.stream().filter(g -> g.getPlayerRating() > 0 && g.getOpponentRating() > 0).count();

        String strength = strongestEvidence == null
                ? strongestFallback.metric().getLabel() + " (indicador heurístico " + strongestFallback.value() + ")"
                : strongestEvidence.label() + " — " + String.format(Locale.ROOT, "%.1f%%", strongestEvidence.rawScore())
                    + " em " + strongestEvidence.games() + " partidas";
        String weakness = weakestEvidence == null
                ? weakestFallback.metric().getLabel() + " (indicador heurístico " + weakestFallback.value() + ")"
                : weakestEvidence.label() + " — " + String.format(Locale.ROOT, "%.1f%%", weakestEvidence.rawScore())
                    + " em " + weakestEvidence.games() + " partidas";
        if (ratedCount >= 6 && versusExpected >= 7.0) {
            strength = "Performance vs rating — " + String.format(Locale.ROOT, "%+.1f p.p.", versusExpected)
                    + " em " + ratedCount + " partidas ranqueadas";
        }
        if (ratedCount >= 6 && versusExpected <= -7.0) {
            weakness = "Performance vs rating — " + String.format(Locale.ROOT, "%+.1f p.p.", versusExpected)
                    + " em " + ratedCount + " partidas ranqueadas";
        }

        return new PlayerDiagnosis(
                strength,
                weakness,
                best == null ? "Dados insuficientes" : best.family().name() + " — "
                        + String.format(Locale.ROOT, "%.1f%%", best.scoreRate()) + " (" + best.games() + ")",
                worst == null ? "Dados insuficientes" : worst.family().name() + " — "
                        + String.format(Locale.ROOT, "%.1f%%", worst.scoreRate()) + " (" + worst.games() + ")",
                criticalPhase(games, weakestFallback, worst),
                scoreRate(whiteGames),
                scoreRate(blackGames),
                scoreRate(games),
                games.size());
    }

    public List<OpeningStat> openingStats(List<Game> games) {
        return openingBook.summarize(games);
    }

    public List<RepertoireHole> repertoireHoles(List<Game> games) {
        return openingStats(games).stream()
                .filter(stat -> stat.games() >= 4)
                .filter(stat -> adjustedOpeningScore(stat) < 47.0 || stat.losses() >= Math.max(2, stat.games() / 2))
                .sorted(Comparator.comparingDouble(this::adjustedOpeningScore))
                .map(stat -> new RepertoireHole(stat.family().name(), stat.games(), stat.scoreRate(), stat.losses(),
                        "Revisar plano: " + stat.family().mainPlan() + " Cuidado: " + stat.family().typicalRisk()))
                .limit(6)
                .toList();
    }

    public List<String> trainingPlan(List<Game> games) {
        LinkedHashMap<String, Integer> priorities = new LinkedHashMap<>();
        for (RepertoireHole hole : repertoireHoles(games)) {
            priorities.merge("Revisar " + hole.opening() + " (" + "%.1f%%".formatted(hole.scoreRate()) + " em "
                    + hole.games() + " partida(s))", 4, Integer::sum);
        }
        for (Game game : games) {
            GameReview review = reviewService.review(game);
            for (String recommendation : review.recommendations()) priorities.merge(recommendation, 2, Integer::sum);
            for (String theme : review.tacticalThemes()) {
                if (!theme.contains("sem tema")) priorities.merge("Táticas: " + theme, 2, Integer::sum);
            }
            for (String theme : review.endgameThemes()) {
                if (theme.toLowerCase(Locale.ROOT).contains("torre")) priorities.merge("Treinar finais de torre", 3, Integer::sum);
                if (theme.toLowerCase(Locale.ROOT).contains("peao")) priorities.merge("Treinar finais de peões", 2, Integer::sum);
            }
        }
        if (priorities.isEmpty()) {
            priorities.put("Importar mais partidas para gerar um plano de treino personalizado.", 1);
        }
        return priorities.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(7)
                .toList();
    }

    public List<TrainingExercise> exercises(List<Game> games) {
        List<TrainingExercise> exercises = new ArrayList<>();
        for (Game game : games) {
            GameReview review = reviewService.review(game);
            for (MoveFeedback feedback : review.keyMoves()) {
                if ("Resumo".equals(feedback.category())) continue;
                int ply = Math.max(0, (feedback.moveNumber() - 1) * 2 + (feedback.side() == PieceColor.BLACK ? 1 : 0));
                String prompt = "Encontre a ideia do lance " + feedback.label() + ": " + feedback.theme();
                exercises.add(new TrainingExercise(game, feedback, ply, prompt));
                if (exercises.size() >= 12) return List.copyOf(exercises);
            }
        }
        return List.copyOf(exercises);
    }

    public Map<String, Double> scoreByOpening(List<Game> games) {
        return openingStats(games).stream().limit(8)
                .collect(Collectors.toMap(stat -> stat.family().name(), OpeningStat::scoreRate,
                        (a, b) -> a, LinkedHashMap::new));
    }

    public Map<String, Double> scoreByColor(List<Game> games) {
        LinkedHashMap<String, Double> map = new LinkedHashMap<>();
        map.put("Brancas", scoreRate(games.stream().filter(game -> game.getColor() == PieceColor.WHITE).toList()));
        map.put("Pretas", scoreRate(games.stream().filter(game -> game.getColor() == PieceColor.BLACK).toList()));
        return map;
    }

    public Map<String, Double> scoreTimeline(List<Game> games) {
        return games.stream()
                .collect(Collectors.groupingBy(game -> YearMonth.from(game.getDate()), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), entry -> scoreRate(entry.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    public double expectedScoreRate(List<Game> games) {
        List<Game> rated = games.stream().filter(g -> g.getPlayerRating() > 0 && g.getOpponentRating() > 0).toList();
        if (rated.isEmpty()) return 0.0;
        return rated.stream().mapToDouble(g -> 1.0 / (1.0 + Math.pow(10.0,
                        (g.getOpponentRating() - g.getPlayerRating()) / 400.0)))
                .average().orElse(0.0) * 100.0;
    }

    public double performanceVsExpected(List<Game> games) {
        List<Game> rated = games.stream().filter(g -> g.getPlayerRating() > 0 && g.getOpponentRating() > 0).toList();
        if (rated.isEmpty()) return 0.0;
        return scoreRate(rated) - expectedScoreRate(rated);
    }

    public int ratingChange(List<Game> games) {
        List<Game> rated = games.stream().filter(g -> g.getPlayerRating() > 0)
                .sorted(Comparator.comparing(Game::getDate)).toList();
        if (rated.size() < 2) return 0;
        return rated.get(rated.size() - 1).getPlayerRating() - rated.get(0).getPlayerRating();
    }

    public double recentTrend(List<Game> games) {
        List<Game> sorted = games.stream().sorted(Comparator.comparing(Game::getDate).reversed()).toList();
        if (sorted.size() < 8) return 0.0;
        int window = Math.min(10, sorted.size() / 2);
        List<Game> recent = sorted.subList(0, window);
        List<Game> previous = sorted.subList(window, Math.min(window * 2, sorted.size()));
        return scoreRate(recent) - scoreRate(previous);
    }

    public List<String> actionableInsights(List<Game> games) {
        if (games.isEmpty()) return List.of("Importe partidas para gerar recomendações baseadas no seu histórico real.");
        List<String> result = new ArrayList<>();
        double expected = expectedScoreRate(games);
        double delta = performanceVsExpected(games);
        if (expected > 0) {
            if (delta >= 5) result.add("Você está pontuando " + String.format(Locale.ROOT, "%.1f", delta)
                    + " p.p. acima do esperado pela diferença de rating dos adversários.");
            else if (delta <= -5) result.add("Você está pontuando " + String.format(Locale.ROOT, "%.1f", Math.abs(delta))
                    + " p.p. abaixo do esperado pelo rating. Priorize revisar derrotas contra adversários de rating igual ou menor.");
            else result.add("Seu resultado está próximo do esperado pelo rating (diferença de "
                    + String.format(Locale.ROOT, "%+.1f", delta) + " p.p.).");
        }

        double trend = recentTrend(games);
        if (Math.abs(trend) >= 8) {
            result.add(trend > 0
                    ? "Forma recente em alta: as partidas mais novas têm score " + String.format(Locale.ROOT, "%.1f", trend) + " p.p. maior que o bloco anterior."
                    : "Queda recente: as partidas mais novas têm score " + String.format(Locale.ROOT, "%.1f", Math.abs(trend)) + " p.p. menor que o bloco anterior.");
        }

        double white = scoreRate(games.stream().filter(g -> g.getColor() == PieceColor.WHITE).toList());
        double black = scoreRate(games.stream().filter(g -> g.getColor() == PieceColor.BLACK).toList());
        if (Math.abs(white - black) >= 12) {
            String weaker = white < black ? "brancas" : "pretas";
            result.add("Há um gap de " + String.format(Locale.ROOT, "%.1f", Math.abs(white - black))
                    + " p.p. entre as cores; seu desempenho mais fraco é de " + weaker + ".");
        }

        openingStats(games).stream().filter(s -> s.games() >= 4).min(Comparator.comparingDouble(this::adjustedOpeningScore))
                .filter(s -> adjustedOpeningScore(s) < 48.0)
                .ifPresent(s -> result.add("Prioridade de repertório: " + s.family().name() + " tem "
                        + String.format(Locale.ROOT, "%.1f%%", s.scoreRate()) + " em " + s.games()
                        + " partidas. Revise primeiro as linhas que mais se repetem."));

        long losses = games.stream().filter(g -> g.getResult() == GameResult.LOSS).count();
        long shortLosses = games.stream().filter(g -> g.getResult() == GameResult.LOSS && g.getMoves() < 30).count();
        if (losses >= 3 && shortLosses * 1.0 / losses >= 0.35) {
            result.add(shortLosses + " de " + losses + " derrotas terminaram antes do lance 30; abertura, tática inicial e segurança do rei merecem prioridade.");
        }

        long upsets = games.stream().filter(g -> g.getResult() == GameResult.WIN && g.getOpponentRating() >= g.getPlayerRating() + 100
                && g.getPlayerRating() > 0).count();
        if (upsets > 0) result.add("Você venceu " + upsets + " partida(s) contra adversários pelo menos 100 pontos acima: use essas partidas como modelo do que funcionou.");

        int ratingDelta = ratingChange(games);
        if (Math.abs(ratingDelta) >= 25) result.add("Variação de rating no período: " + (ratingDelta >= 0 ? "+" : "") + ratingDelta + ".");
        if (games.size() < 10) result.add("Amostra pequena (" + games.size() + " partidas): trate tendências como indícios, não como conclusões.");
        return result.stream().distinct().limit(7).toList();
    }

    public ImportComparison compare(List<Game> before, List<Game> after) {
        return new ImportComparison(before.size(), after.size(), scoreRate(before), scoreRate(after));
    }

    private String criticalPhase(List<Game> games, PerformanceScore weakestFallback, OpeningStat worstOpening) {
        long losses = games.stream().filter(game -> game.getResult() == GameResult.LOSS).count();
        if (losses >= 3) {
            long shortLosses = games.stream().filter(game -> game.getResult() == GameResult.LOSS && game.getMoves() < 30).count();
            if (shortLosses * 1.0 / losses >= 0.35) return "Abertura / início do meio-jogo";
            long longLosses = games.stream().filter(game -> game.getResult() == GameResult.LOSS && game.getMoves() >= 45).count();
            if (longLosses * 1.0 / losses >= 0.45) return "Finais / partidas longas";
        }
        if (worstOpening != null && worstOpening.games() >= 4 && adjustedOpeningScore(worstOpening) < 47.0) {
            return "Repertório: " + worstOpening.family().name();
        }
        if (games.size() < 8) return "Amostra insuficiente";
        return weakestFallback.metric().getLabel() + " (heurístico)";
    }

    /** Shrinks small samples toward 50% so a 3/3 start does not dominate a 20-game line. */
    private double adjustedScoreRate(List<Game> games) {
        if (games.isEmpty()) return 50.0;
        double priorGames = 4.0;
        return (scoreRate(games) * games.size() + 50.0 * priorGames) / (games.size() + priorGames);
    }

    private double adjustedOpeningScore(OpeningStat stat) {
        double priorGames = 4.0;
        return (stat.scoreRate() * stat.games() + 50.0 * priorGames) / (stat.games() + priorGames);
    }

    private record Evidence(String label, double adjustedScore, int games, double rawScore) { }

    private double scoreRate(List<Game> games) {
        if (games.isEmpty()) return 0;
        double score = 0;
        for (Game game : games) {
            if (game.getResult() == GameResult.WIN) score += 1;
            if (game.getResult() == GameResult.DRAW) score += 0.5;
        }
        return score * 100.0 / games.size();
    }
}
