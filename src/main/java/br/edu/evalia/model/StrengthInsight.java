package br.edu.evalia.model;

public final class StrengthInsight extends AnalysisInsight {
    public StrengthInsight(String title, String description, int score) {
        super(title, description, score);
    }

    @Override public String getTypeLabel() { return "Ponto forte"; }
    @Override public String getCssClass() { return "insight-strength"; }
}
