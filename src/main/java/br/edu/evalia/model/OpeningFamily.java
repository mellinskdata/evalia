package br.edu.evalia.model;

import java.util.List;

public record OpeningFamily(String name, String ecoRange, List<String> aliases,
                            String mainPlan, String typicalRisk) {
    public OpeningFamily { aliases = List.copyOf(aliases); }
}
