package br.edu.evalia.model;

public enum PerformanceMetric {
    OPENING("Abertura", "Precisao e resultados nos primeiros lances"),
    TACTICS("Tatica", "Capacidade de encontrar recursos concretos"),
    STRATEGY("Estrategia", "Qualidade dos planos de medio prazo"),
    ENDGAME("Finais", "Conversao e defesa em finais"),
    DEFENSE("Defesa", "Resiliencia em posicoes inferiores"),
    CONVERSION("Conversao", "Aproveitamento de posicoes superiores"),
    TIME_MANAGEMENT("Gestao de tempo", "Uso consistente do relogio"),
    WHITE("Brancas", "Desempenho jogando de brancas"),
    BLACK("Pretas", "Desempenho jogando de pretas");

    private final String label;
    private final String description;

    PerformanceMetric(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
