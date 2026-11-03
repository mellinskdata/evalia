package br.edu.evalia.model;

import java.util.List;

public record GameReview(OpeningFamily openingFamily,
                         List<MoveFeedback> keyMoves,
                         List<String> tacticalThemes,
                         List<String> strategicThemes,
                         List<String> endgameThemes,
                         String endgameType,
                         List<String> recommendations) {
    public GameReview {
        keyMoves = List.copyOf(keyMoves);
        tacticalThemes = List.copyOf(tacticalThemes);
        strategicThemes = List.copyOf(strategicThemes);
        endgameThemes = List.copyOf(endgameThemes);
        recommendations = List.copyOf(recommendations);
    }
}
