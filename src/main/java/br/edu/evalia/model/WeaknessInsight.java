package br.edu.evalia.model;

public final class WeaknessInsight extends AnalysisInsight {
    public WeaknessInsight(String title, String description, int score) {
        super(title, description, score);
    }

    @Override public String getTypeLabel() { return "Blind spot"; }
    @Override public String getCssClass() { return "insight-weakness"; }
}
