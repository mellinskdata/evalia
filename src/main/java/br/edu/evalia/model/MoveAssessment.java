package br.edu.evalia.model;

public record MoveAssessment(int plyIndex,
                             int moveNumber,
                             PieceColor side,
                             String san,
                             MoveClassification classification,
                             int centipawnLoss,
                             Integer evaluationBefore,
                             Integer evaluationAfter,
                             boolean engineBestMove,
                             boolean uniqueWinningIdea,
                             String bestMoveUci,
                             String explanation) {
    public String label() {
        String prefix = side == PieceColor.WHITE ? moveNumber + "." : moveNumber + "...";
        String annotation = classification.annotation();
        return prefix + " " + san + (annotation == null || annotation.isBlank() ? "" : " " + annotation);
    }
}
