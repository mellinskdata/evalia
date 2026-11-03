package br.edu.evalia.model;

public record OpeningTreeRow(String move, String line, int games, int wins,
                             int draws, int losses, double scoreRate) { }
