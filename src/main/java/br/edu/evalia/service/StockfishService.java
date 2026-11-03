package br.edu.evalia.service;

import br.edu.evalia.model.BoardPosition;
import br.edu.evalia.model.EngineAnalysis;
import br.edu.evalia.model.Move;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** One serialized UCI conversation, with an independent stdout drain and bounded waits. */
public final class StockfishService implements AutoCloseable {
    private static final String EOF = "\u0000EOF";
    private final List<String> command;
    private final Duration handshakeTimeout;
    private volatile Session session;
    private volatile boolean closed;

    public StockfishService(Path executable) {
        this(List.of(executable.toAbsolutePath().toString()), Duration.ofSeconds(10));
    }

    StockfishService(List<String> command, Duration handshakeTimeout) {
        this.command = List.copyOf(command);
        this.handshakeTimeout = handshakeTimeout;
    }

    public synchronized void initialize() throws IOException, InterruptedException, TimeoutException {
        if (closed) throw new IOException("A engine ja foi encerrada.");
        if (session != null && session.process.isAlive()) return;
        Session started = new Session(new ProcessBuilder(command).redirectErrorStream(true).start());
        session = started;
        if (closed) { shutdown(started); throw new IOException("Engine encerrada."); }
        try {
            started.send("uci");
            await(started, "uciok", handshakeTimeout);
            started.send("setoption name Threads value 1");
            started.send("setoption name Hash value 64");
            started.send("isready");
            await(started, "readyok", handshakeTimeout);
        } catch (IOException | InterruptedException | TimeoutException ex) {
            shutdown(started);
            throw ex;
        }
    }

    public synchronized EngineAnalysis analyze(String fen, int depth, Duration timeout,
                                               BooleanSupplier cancelled)
            throws IOException, InterruptedException, TimeoutException {
        fen = BoardPosition.validateFen(fen);
        if (depth < 1 || depth > 40) throw new IllegalArgumentException("Profundidade: 1 a 40.");
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Tempo limite: entre 1 ms e 10 minutos.");
        }
        checkCancelled(cancelled);
        initialize();
        Session active = session;
        try {
            active.send("ucinewgame");
            active.send("isready");
            await(active, "readyok", handshakeTimeout);
            active.send("position fen " + fen);
            active.send("go depth " + depth);
            long deadline = System.nanoTime() + timeout.toNanos();
            int reachedDepth = 0;
            Integer cp = null;
            Integer mate = null;
            List<Move> pv = List.of();
            while (true) {
                checkCancelled(cancelled);
                String line = next(active, deadline);
                String[] tokens = line.trim().split("\\s+");
                if (tokens[0].equals("bestmove")) {
                    if (tokens.length < 2) throw new IOException("Resposta bestmove incompleta.");
                    Optional<Move> best = tokens[1].equals("(none)") || tokens[1].equals("0000")
                            ? Optional.empty() : Optional.of(new Move(tokens[1]));
                    return new EngineAnalysis(fen, best, reachedDepth, cp, mate, pv);
                }
                if (!tokens[0].equals("info") || line.startsWith("info string")) continue;
                for (int i = 1; i < tokens.length; i++) {
                    if (tokens[i].equals("depth") && i + 1 < tokens.length) {
                        reachedDepth = parseInt(tokens[++i], reachedDepth);
                    } else if (tokens[i].equals("score") && i + 2 < tokens.length) {
                        String type = tokens[++i];
                        Integer score = parseInt(tokens[++i], null);
                        if (type.equals("cp")) { cp = score; mate = null; }
                        if (type.equals("mate")) { mate = score; cp = null; }
                    } else if (tokens[i].equals("pv")) {
                        List<Move> variation = new ArrayList<>();
                        for (int j = i + 1; j < tokens.length; j++) {
                            try { variation.add(new Move(tokens[j])); }
                            catch (IllegalArgumentException ignored) { break; }
                        }
                        pv = List.copyOf(variation);
                        break;
                    }
                }
            }
        } catch (IOException | InterruptedException | TimeoutException | RuntimeException ex) {
            // Do not let a late bestmove become the result of a subsequent request.
            shutdown(active);
            throw ex;
        }
    }

    public EngineAnalysis analyze(String fen, int depth) throws IOException, InterruptedException, TimeoutException {
        return analyze(fen, depth, Duration.ofSeconds(60), () -> false);
    }

    public synchronized List<EngineAnalysis> analyzeMultiPv(String fen, int depth, int lines, Duration timeout,
                                                             BooleanSupplier cancelled)
            throws IOException, InterruptedException, TimeoutException {
        fen = BoardPosition.validateFen(fen);
        if (depth < 1 || depth > 40) throw new IllegalArgumentException("Profundidade: 1 a 40.");
        if (lines < 1 || lines > 8) throw new IllegalArgumentException("MultiPV: 1 a 8 linhas.");
        checkCancelled(cancelled);
        initialize();
        Session active = session;
        try {
            active.send("setoption name MultiPV value " + lines);
            active.send("isready");
            await(active, "readyok", handshakeTimeout);
            active.send("ucinewgame");
            active.send("position fen " + fen);
            active.send("go depth " + depth);
            long deadline = System.nanoTime() + timeout.toNanos();
            Map<Integer, EngineAnalysis> latest = new HashMap<>();
            while (true) {
                checkCancelled(cancelled);
                String line = next(active, deadline);
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;
                if (tokens[0].equals("bestmove")) {
                    active.send("setoption name MultiPV value 1");
                    active.send("isready");
                    await(active, "readyok", handshakeTimeout);
                    return latest.entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(Map.Entry::getValue).toList();
                }
                if (!tokens[0].equals("info") || line.startsWith("info string")) continue;
                int reachedDepth = 0, multiPv = 1;
                Integer cp = null, mate = null;
                List<Move> pv = List.of();
                for (int i = 1; i < tokens.length; i++) {
                    if (tokens[i].equals("depth") && i + 1 < tokens.length) {
                        reachedDepth = parseInt(tokens[++i], reachedDepth);
                    } else if (tokens[i].equals("multipv") && i + 1 < tokens.length) {
                        multiPv = parseInt(tokens[++i], 1);
                    } else if (tokens[i].equals("score") && i + 2 < tokens.length) {
                        String type = tokens[++i];
                        Integer score = parseInt(tokens[++i], null);
                        if (type.equals("cp")) { cp = score; mate = null; }
                        if (type.equals("mate")) { mate = score; cp = null; }
                    } else if (tokens[i].equals("pv")) {
                        List<Move> variation = new ArrayList<>();
                        for (int j = i + 1; j < tokens.length; j++) {
                            try { variation.add(new Move(tokens[j])); }
                            catch (IllegalArgumentException ignored) { break; }
                        }
                        pv = List.copyOf(variation);
                        break;
                    }
                }
                if (!pv.isEmpty() && (cp != null || mate != null)) {
                    latest.put(multiPv, new EngineAnalysis(fen, Optional.of(pv.get(0)), reachedDepth, cp, mate, pv));
                }
            }
        } catch (IOException | InterruptedException | TimeoutException | RuntimeException ex) {
            shutdown(active);
            throw ex;
        }
    }

    private void await(Session active, String expected, Duration timeout)
            throws IOException, InterruptedException, TimeoutException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!next(active, deadline).equals(expected)) { /* UCI also emits engine identity and options. */ }
    }

    private String next(Session active, long deadline) throws IOException, InterruptedException, TimeoutException {
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException("Stockfish excedeu o tempo limite.");
            String line = active.output.poll(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
            if (EOF.equals(line)) throw new IOException("Stockfish encerrou sem concluir a analise. Confira o executavel e a CPU.");
            if (line != null) return line;
            if (closed || !active.process.isAlive()) throw new IOException("Stockfish foi encerrado.");
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Analise cancelada.");
        }
    }

    private Integer parseInt(String text, Integer fallback) {
        try { return Integer.valueOf(text); } catch (NumberFormatException ex) { return fallback; }
    }

    private void shutdown(Session active) {
        if (active == null) return;
        try { active.send("stop"); active.send("quit"); } catch (IOException ignored) { }
        active.process.destroy();
        if (active.process.isAlive()) active.process.destroyForcibly();
        active.reader.interrupt();
        if (session == active) session = null;
    }

    @Override public void close() {
        closed = true;
        shutdown(session);
    }

    private static final class Session {
        final Process process;
        final BufferedWriter input;
        final BlockingQueue<String> output = new ArrayBlockingQueue<>(4096);
        final Thread reader;

        Session(Process process) {
            this.process = process;
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new Thread(() -> {
                try (var stream = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stream.readLine()) != null) output.put(line);
                } catch (IOException ignored) {
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (!output.offer(EOF)) { output.poll(); output.offer(EOF); }
                }
            }, "evalia-uci-output");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(String command) throws IOException {
            input.write(command);
            input.newLine();
            input.flush();
        }
    }
}
