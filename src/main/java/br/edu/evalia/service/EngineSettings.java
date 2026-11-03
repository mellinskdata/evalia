package br.edu.evalia.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class EngineSettings {
    private final Path file;
    private String executable;

    public EngineSettings(Path file) throws IOException {
        this.file = file;
        Properties properties = new Properties();
        if (Files.exists(file)) try (var in = Files.newInputStream(file)) { properties.load(in); }
        executable = System.getProperty("evalia.stockfish",
                System.getenv().getOrDefault("STOCKFISH_PATH", properties.getProperty("executable", ""))).trim();
        if (executable.isBlank() || !Files.isRegularFile(Path.of(executable))) {
            executable = discover().map(path -> path.toAbsolutePath().normalize().toString()).orElse("");
        }
    }

    public String executable() { return executable; }

    public void save(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IOException("Executável do Stockfish não encontrado: " + normalized);
        Properties properties = new Properties();
        properties.setProperty("executable", normalized.toString());
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (var out = Files.newOutputStream(file)) { properties.store(out, "Evalia UCI"); }
        executable = normalized.toString();
    }

    private java.util.Optional<Path> discover() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String binary = windows ? "stockfish.exe" : "stockfish";
        List<Path> candidates = new ArrayList<>(List.of(
                Path.of("engine", binary), Path.of(binary), Path.of("runtime", "engine", binary)));
        String appPath = System.getProperty("jpackage.app-path", "").trim();
        if (!appPath.isBlank()) {
            Path appDir = Path.of(appPath).toAbsolutePath().getParent();
            if (appDir != null) {
                candidates.add(appDir.resolve("engine").resolve(binary));
                candidates.add(appDir.resolve("app").resolve("engine").resolve(binary));
            }
        }
        String path = System.getenv().getOrDefault("PATH", "");
        if (!path.isBlank()) {
            for (String entry : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
                if (!entry.isBlank()) candidates.add(Path.of(entry, binary));
            }
        }
        return candidates.stream().map(Path::toAbsolutePath).filter(Files::isRegularFile).findFirst();
    }
}
