package br.edu.evalia.service;

import br.edu.evalia.model.*;
import br.edu.evalia.repository.GameRepository;

import java.util.*;
import java.util.stream.Collectors;

public final class AnalyticsService {
    private final GameRepository repository;
    private final List<MetricAnalyzer> analyzers;
    private final OpeningBook openingBook = new OpeningBook();

    public AnalyticsService(GameRepository repository) {
        this.repository = repository;
        this.analyzers = List.of(
                new PhaseMetricAnalyzer(PerformanceMetric.OPENING),
                new PhaseMetricAnalyzer(PerformanceMetric.TACTICS),
                new PhaseMetricAnalyzer(PerformanceMetric.STRATEGY),
                new PhaseMetricAnalyzer(PerformanceMetric.ENDGAME),
                new PhaseMetricAnalyzer(PerformanceMetric.DEFENSE),
                new PhaseMetricAnalyzer(PerformanceMetric.CONVERSION),
                new PhaseMetricAnalyzer(PerformanceMetric.TIME_MANAGEMENT),
                new ColorMetricAnalyzer(PieceColor.WHITE),
                new ColorMetricAnalyzer(PieceColor.BLACK));
    }

    public DashboardSnapshot buildSnapshot() {
        List<Game> games = repository.findAll().stream()
                .sorted(Comparator.comparing(Game::getDate).reversed()).toList();
        int wins = count(games, GameResult.WIN);
        int draws = count(games, GameResult.DRAW);
        int losses = count(games, GameResult.LOSS);
        double winRate = games.isEmpty() ? 0 : wins * 100.0 / games.size();
        List<PerformanceScore> scores = scoresFor(games);

        int currentRating = games.stream().mapToInt(Game::getPlayerRating).filter(r -> r > 0).findFirst().orElse(0);
        return new DashboardSnapshot(games.size(), wins, draws, losses, currentRating, winRate,
                findBestOpening(games), scores, buildInsights(scores, games), games.stream().limit(8).toList());
    }

    public List<PerformanceScore> scoresFor(List<Game> games) {
        return analyzers.stream()
                .map(a -> new PerformanceScore(a.metric(), a.analyze(games)))
                .toList();
    }

    private int count(List<Game> games, GameResult result) {
        return (int) games.stream().filter(g -> g.getResult() == result).count();
    }

    private String findBestOpening(List<Game> games) {
        return games.stream().collect(Collectors.groupingBy(g -> openingBook.classify(g).name()))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .max(Comparator.comparingDouble(e -> ScoreMath.resultRate(e.getValue())))
                .map(Map.Entry::getKey).orElse("Dados insuficientes");
    }

    private List<AnalysisInsight> buildInsights(List<PerformanceScore> scores, List<Game> games) {
        List<PerformanceScore> ordered = scores.stream()
                .sorted(Comparator.comparingInt(PerformanceScore::value)).toList();
        PerformanceScore weakest = ordered.get(0);
        PerformanceScore strongest = ordered.get(ordered.size() - 1);
        long shortLosses = games.stream().filter(g -> g.getMoves() < 30 && g.getResult() == GameResult.LOSS).count();

        return List.of(
                new WeaknessInsight(weakest.metric().getLabel(),
                        "E a area com menor indice do perfil. Revise posicoes relacionadas e acompanhe a evolucao.", weakest.value()),
                new StrengthInsight(strongest.metric().getLabel(),
                        "Seu melhor indicador atual. Use esse repertorio como base para escolhas consistentes.", strongest.value()),
                new WeaknessInsight("Decisoes em partidas curtas",
                        shortLosses + " derrota(s) aconteceram antes do lance 30. Vale revisar tatica e gestao de tempo.",
                        ScoreMath.clamp(78 - shortLosses * 8)));
    }
}
