package br.edu.evalia.model;

import java.util.List;

public record OpeningEntry(String name, String eco, List<String> moves, String fen) {
    public OpeningEntry { moves = List.copyOf(moves); }
}
