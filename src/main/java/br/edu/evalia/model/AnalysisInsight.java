package br.edu.evalia.model;

public abstract class AnalysisInsight {
    private final String title;
    private final String description;
    private final int score;

    protected AnalysisInsight(String title, String description, int score) {
        this.title = title;
        this.description = description;
        this.score = score;
    }

    public abstract String getTypeLabel();
    public abstract String getCssClass();

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getScore() { return score; }
}
