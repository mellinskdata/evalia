package br.edu.evalia.model;

public record PlayerDiagnosis(String mainStrength,
                              String mainWeakness,
                              String bestOpening,
                              String worstOpening,
                              String criticalPhase,
                              double whiteScoreRate,
                              double blackScoreRate,
                              double scoreRate,
                              int totalGames) { }
