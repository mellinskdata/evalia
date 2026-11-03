package br.edu.evalia.service;

import br.edu.evalia.model.PerformanceScore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class DashboardExportService {
    public void export(DashboardSnapshot snapshot, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        String metrics = snapshot.scores().stream()
                .map(this::metricJson).collect(Collectors.joining(",\n    "));
        String json = """
                {
                  "totalGames": %d,
                  "wins": %d,
                  "draws": %d,
                  "losses": %d,
                  "currentRating": %d,
                  "winRate": %.1f,
                  "bestOpening": "%s",
                  "metrics": [
                    %s
                  ]
                }
                """.formatted(snapshot.totalGames(), snapshot.wins(), snapshot.draws(),
                snapshot.losses(), snapshot.currentRating(), snapshot.winRate(), escape(snapshot.bestOpening()), metrics);
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }

    private String metricJson(PerformanceScore score) {
        return "{\"area\": \"%s\", \"score\": %d}".formatted(
                escape(score.metric().getLabel()), score.value());
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
