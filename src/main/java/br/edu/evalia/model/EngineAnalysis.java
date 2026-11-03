package br.edu.evalia.model;

import java.util.List;
import java.util.Optional;

/** Scores are from the side-to-move's perspective, as reported by UCI. */
public record EngineAnalysis(String fen, Optional<Move> bestMove, int depth,
                             Integer centipawns, Integer mateIn, List<Move> principalVariation) {
    public EngineAnalysis {
        bestMove = bestMove == null ? Optional.empty() : bestMove;
        principalVariation = List.copyOf(principalVariation);
    }
}
