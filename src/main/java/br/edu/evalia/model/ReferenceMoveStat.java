package br.edu.evalia.model;

public record ReferenceMoveStat(String uci, long whiteWins, long draws, long blackWins, String source) {
    public ReferenceMoveStat {
        new Move(uci);
        if (whiteWins < 0 || draws < 0 || blackWins < 0 || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Estatisticas precisam de contagens validas e fonte.");
        }
    }
    public long games() { return Math.addExact(Math.addExact(whiteWins, draws), blackWins); }
    public double whiteScore() { return games() == 0 ? 0 : (whiteWins + draws * 0.5) * 100 / games(); }
}
