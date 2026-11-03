package br.edu.evalia.service;

import br.edu.evalia.model.Game;
import br.edu.evalia.model.PerformanceMetric;

import java.util.List;

public interface MetricAnalyzer {
    PerformanceMetric metric();
    int analyze(List<Game> games);
}
