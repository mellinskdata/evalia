package br.edu.evalia.repository;

import br.edu.evalia.model.Game;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public interface GameRepository {
    List<Game> findAll();
    default void forEach(Consumer<Game> visitor) { findAll().forEach(visitor); }
    int saveAll(List<Game> games) throws IOException;
    void resetForNewAccount() throws IOException;
    void exportTo(Path target) throws IOException;
    int importFrom(Path source) throws IOException;
    boolean isDemoMode();
}
