package br.edu.evalia.model;

import java.util.List;

public record EngineGameReview(List<MoveAssessment> moves,
                               List<MoveFeedback> keyMoves,
                               double whiteAverageCentipawnLoss,
                               double blackAverageCentipawnLoss) {
    public EngineGameReview {
        moves = List.copyOf(moves);
        keyMoves = List.copyOf(keyMoves);
    }
}
