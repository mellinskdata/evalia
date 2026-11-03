package br.edu.evalia.model;

public record OpeningStat(OpeningFamily family, int games, int wins, int draws,
                          int losses, double scoreRate) { }
