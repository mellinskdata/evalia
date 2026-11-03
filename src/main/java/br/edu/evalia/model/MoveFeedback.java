package br.edu.evalia.model;

public record MoveFeedback(int moveNumber, PieceColor side, String san,
                           String category, String theme, String description,
                           String cssClass) {
    public String label() {
        String prefix = side == PieceColor.WHITE ? moveNumber + "." : moveNumber + "...";
        return prefix + " " + san;
    }
}
