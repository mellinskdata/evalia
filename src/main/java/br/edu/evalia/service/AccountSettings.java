package br.edu.evalia.service;

import br.edu.evalia.model.SourcePlatform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AccountSettings {
    private final Path file;
    private final Properties properties = new Properties();

    public AccountSettings(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
            catch (IOException ignored) { }
        }
    }

    public String username(SourcePlatform platform) {
        return properties.getProperty(key(platform), "");
    }

    public synchronized void saveUsername(SourcePlatform platform, String username) throws IOException {
        properties.setProperty(key(platform), username.trim());
        persist();
    }

    public synchronized void clear() throws IOException {
        properties.clear();
        persist();
    }

    private void persist() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Contas publicas conectadas ao Evalia");
        }
    }

    public String activeUsername() {
        String chess = username(SourcePlatform.CHESS_COM);
        if (!chess.isBlank()) return chess;
        String lichess = username(SourcePlatform.LICHESS);
        return lichess.isBlank() ? "Jogador demo" : lichess;
    }

    private String key(SourcePlatform platform) {
        return platform == SourcePlatform.CHESS_COM ? "chess.com.username" : "lichess.username";
    }
}
