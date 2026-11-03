package br.edu.evalia.service;

import br.edu.evalia.model.Game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GameAnnotationsService {
    private final Path file;
    private final Properties data = new Properties();

    public GameAnnotationsService(Path file) {
        this.file = file;
        load();
    }

    public boolean isFavorite(Game game) {
        return Boolean.parseBoolean(data.getProperty(favoriteKey(game), "false"));
    }

    public void setFavorite(Game game, boolean favorite) throws IOException {
        data.setProperty(favoriteKey(game), String.valueOf(favorite));
        persist();
    }

    public String note(Game game) {
        return data.getProperty(noteKey(game), "");
    }

    public void saveNote(Game game, String note) throws IOException {
        data.setProperty(noteKey(game), note == null ? "" : note.trim());
        persist();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data.load(reader);
        } catch (IOException ignored) {
            data.clear();
        }
    }

    private void persist() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            data.store(writer, "Evalia favorites and personal notes");
        }
    }

    private String favoriteKey(Game game) {
        return "favorite." + game.getId();
    }

    private String noteKey(Game game) {
        return "note." + game.getId();
    }
}
