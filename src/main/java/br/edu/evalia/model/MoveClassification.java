package br.edu.evalia.model;

public enum MoveClassification {
    BOOK("De livro", "📖", "feedback-book"),
    BRILLIANT("Brilhante", "!!", "feedback-brilliant"),
    GREAT("Ótimo", "!", "feedback-great"),
    NORMAL("Normal", "", "feedback-normal"),
    DUBIOUS("Duvidoso", "?!", "feedback-dubious"),
    BAD("Ruim", "?", "feedback-bad"),
    BLUNDER("Erro grave", "??", "feedback-blunder");

    private final String label;
    private final String annotation;
    private final String cssClass;

    MoveClassification(String label, String annotation, String cssClass) {
        this.label = label;
        this.annotation = annotation;
        this.cssClass = cssClass;
    }

    public String label() { return label; }
    public String annotation() { return annotation; }
    public String cssClass() { return cssClass; }
}
