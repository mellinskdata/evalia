package br.edu.evalia.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineSettingsTest {
    @TempDir Path tempDir;

    @Test
    void persistsSelectedExecutable() throws Exception {
        Path executable = tempDir.resolve("stockfish-test.exe");
        Files.writeString(executable, "test");
        EngineSettings settings = new EngineSettings(tempDir.resolve("engine.properties"));
        settings.save(executable);
        assertEquals(executable.toAbsolutePath().normalize().toString(), settings.executable());
    }
}
