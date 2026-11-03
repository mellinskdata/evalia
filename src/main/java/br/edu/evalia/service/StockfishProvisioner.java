package br.edu.evalia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs the official stable Stockfish Windows binary on first use. */
public final class StockfishProvisioner {
    private static final URI LATEST = URI.create("https://api.github.com/repos/official-stockfish/Stockfish/releases/latest");
    private final EngineSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public StockfishProvisioner(EngineSettings settings) { this.settings = settings; }

    public Path ensureInstalled(BooleanSupplier cancelled, Consumer<String> status)
            throws IOException, InterruptedException {
        if (!settings.executable().isBlank() && Files.isRegularFile(Path.of(settings.executable()))) {
            return Path.of(settings.executable());
        }
        if (!isWindows()) {
            throw new IOException("A instalação automática do Stockfish está habilitada para Windows nesta versão. Selecione o executável manualmente neste sistema.");
        }
        checkCancelled(cancelled);
        status.accept("Procurando a versão estável oficial do Stockfish...");
        JsonNode release = getJson(LATEST);
        JsonNode asset = chooseWindowsAsset(release.path("assets"));
        if (asset == null) throw new IOException("Não encontrei um binário Windows x64 na release estável do Stockfish.");
        String url = asset.path("browser_download_url").asText();
        if (url.isBlank()) throw new IOException("Release do Stockfish sem URL de download.");

        Path engineDir = Path.of("engine").toAbsolutePath().normalize();
        Files.createDirectories(engineDir);
        Path archive = Files.createTempFile(engineDir, "stockfish-", ".zip");
        Path tempDir = Files.createTempDirectory(engineDir, "stockfish-unpack-");
        try {
            status.accept("Baixando Stockfish oficial...");
            download(URI.create(url), archive, cancelled);
            checkCancelled(cancelled);
            status.accept("Instalando Stockfish...");
            Path extracted = unzipStockfish(archive, tempDir, cancelled);
            Path target = engineDir.resolve("stockfish.exe");
            Files.copy(extracted, target, StandardCopyOption.REPLACE_EXISTING);
            settings.save(target);
            status.accept("Stockfish pronto.");
            return target;
        } finally {
            Files.deleteIfExists(archive);
            if (Files.exists(tempDir)) {
                try (var walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
                }
            }
        }
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Evalia/2.2")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("GitHub respondeu HTTP " + response.statusCode() + ".");
        return mapper.readTree(response.body());
    }

    private JsonNode chooseWindowsAsset(JsonNode assets) {
        JsonNode fallback = null;
        for (JsonNode asset : assets) {
            String name = asset.path("name").asText().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".zip") || !(name.contains("windows") || name.contains("win"))) continue;
            if (name.contains("arm") || name.contains("source")) continue;
            if (name.contains("x64") || name.contains("x86-64") || name.contains("amd64")) return asset;
            if (fallback == null) fallback = asset;
        }
        return fallback;
    }

    private void download(URI uri, Path target, BooleanSupplier cancelled) throws IOException, InterruptedException {
        checkCancelled(cancelled);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Evalia/2.2").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Download do Stockfish respondeu HTTP " + response.statusCode() + ".");
        }
        try (InputStream in = response.body(); var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                checkCancelled(cancelled);
                out.write(buffer, 0, read);
            }
        }
    }

    private Path unzipStockfish(Path zip, Path targetDir, BooleanSupplier cancelled) throws IOException, InterruptedException {
        Path found = null;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                checkCancelled(cancelled);
                if (entry.isDirectory()) continue;
                String filename = Path.of(entry.getName()).getFileName().toString().toLowerCase(Locale.ROOT);
                if (!filename.startsWith("stockfish") || !filename.endsWith(".exe")) continue;
                Path output = targetDir.resolve("stockfish.exe");
                Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
                found = output;
                break;
            }
        }
        if (found == null) throw new IOException("O ZIP oficial não continha stockfish.exe.");
        return found;
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Operação cancelada.");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
