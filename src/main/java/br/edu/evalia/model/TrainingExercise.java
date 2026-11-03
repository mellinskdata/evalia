package br.edu.evalia.model;

public record TrainingExercise(Game game,
                               MoveFeedback feedback,
                               int plyIndex,
                               String prompt) { }
