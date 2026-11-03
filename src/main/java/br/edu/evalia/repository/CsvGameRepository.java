package br.edu.evalia.repository;

import br.edu.evalia.model.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class CsvGameRepository implements GameRepository {
    private static final String HEADER = "id,date,opponent,player_rating,opponent_rating,color,result,opening,moves,source,eco,pgn_base64,event,site,white,black,time_control,featured";
    private final Path file;
    private List<Game> demoGames = List.of();
    private final Set<String> fingerprints = new HashSet<>();
    private boolean demoMode;

    public CsvGameRepository(Path file) {
        this(file, true);
    }

    public CsvGameRepository(Path file, boolean seedDemo) {
        this.file = file.toAbsolutePath().normalize();
        loadOrSeed(seedDemo);
    }

    @Override
    public synchronized List<Game> findAll() {
        List<Game> result = new ArrayList<>();
        forEach(result::add);
        return List.copyOf(result);
    }

    @Override
    public synchronized void forEach(java.util.function.Consumer<Game> visitor) {
        if (demoMode) { demoGames.forEach(visitor); return; }
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (reader.readLine() == null) throw new IOException("CSV sem cabecalho: " + file);
            String line;
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) continue;
                Game parsed;
                try { parsed = fromCsv(line); }
                catch (RuntimeException ex) { throw new IOException("CSV invalido na linha " + number, ex); }
                visitor.accept(parsed);
            }
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    @Override
    public synchronized int saveAll(List<Game> newGames) throws IOException {
        if (newGames.isEmpty()) return 0;
        List<Game> additions = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();
        for (Game game : newGames) {
            if (!fingerprints.contains(game.fingerprint()) && batchKeys.add(game.fingerprint())) additions.add(game);
        }
        if (demoMode) {
            List<Game> kept = new ArrayList<>(demoGames.stream().filter(Game::isFeatured).toList());
            kept.addAll(additions);
            persist(kept);
            demoGames = List.of();
            demoMode = false;
        } else if (!additions.isEmpty()) {
            if (!Files.exists(file)) persist(List.of());
            long previousSize = Files.size(file);
            try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                for (Game game : additions) { writer.write(toCsv(game)); writer.newLine(); }
            } catch (IOException ex) {
                try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.truncate(previousSize); }
                catch (IOException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
        fingerprints.addAll(batchKeys);
        return additions.size();
    }

    @Override
    public synchronized void resetForNewAccount() throws IOException {
        List<Game> kept = new ArrayList<>();
        forEach(game -> { if (game.isFeatured()) kept.add(game); });
        persist(kept);
        demoGames = List.of();
        fingerprints.clear();
        kept.forEach(g -> fingerprints.add(g.fingerprint()));
        demoMode = false;
    }

    @Override
    public synchronized void exportTo(Path target) throws IOException {
        if (file.equals(target.toAbsolutePath().normalize())) throw new IOException("Escolha outro arquivo para exportar.");
        if (demoMode) persist(demoGames);
        else if (!Files.exists(file)) persist(List.of());
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public int importFrom(Path source) throws IOException {
        if (file.equals(source.toAbsolutePath().normalize()) || (Files.exists(file) && Files.isSameFile(file, source))) {
            throw new IOException("A base de origem nao pode ser a base de destino.");
        }
        int added = 0;
        List<Game> batch = new ArrayList<>(250);
        try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("id,date,")) throw new IOException("Cabecalho CSV invalido.");
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Importacao cancelada.");
                if (line.isBlank()) continue;
                try { batch.add(fromCsv(line)); }
                catch (RuntimeException ex) { throw new IOException("Registro CSV invalido. Lotes anteriores preservados.", ex); }
                if (batch.size() == 250) { added += saveAll(batch); batch.clear(); }
            }
        }
        return added + saveAll(batch);
    }

    @Override
    public synchronized boolean isDemoMode() {
        return demoMode;
    }

    private void loadOrSeed(boolean seedDemo) {
        if (Files.exists(file)) {
            forEach(game -> fingerprints.add(game.fingerprint()));
            return;
        }
        demoMode = seedDemo;
        if (seedDemo) {
            demoGames = List.copyOf(seedGames());
            demoGames.stream().filter(Game::isFeatured).forEach(g -> fingerprints.add(g.fingerprint()));
        }
    }

    private void persist(List<Game> gamesToWrite) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "evalia-", ".csv.tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER); writer.newLine();
                for (Game game : gamesToWrite) { writer.write(toCsv(game)); writer.newLine(); }
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private String toCsv(Game g) {
        return String.join(",",
                g.getId().toString(), g.getDate().toString(), escape(g.getOpponent()),
                String.valueOf(g.getPlayerRating()), String.valueOf(g.getOpponentRating()),
                g.getColor().name(), g.getResult().name(), escape(g.getOpening()),
                String.valueOf(g.getMoves()), g.getSource().name(), escape(g.getEco()),
                escape(encodePgn(g.getPgn())), escape(g.getEvent()), escape(g.getSite()),
                escape(g.getWhitePlayer()), escape(g.getBlackPlayer()), escape(g.getTimeControl()),
                String.valueOf(g.isFeatured()));
    }

    private Game fromCsv(String line) {
        List<String> p = parseCsv(line);
        if (p.size() == 9) {
            return new Game(UUID.fromString(p.get(0)), LocalDate.parse(p.get(1)), p.get(2),
                    0, Integer.parseInt(p.get(3)), PieceColor.valueOf(p.get(4)),
                    GameResult.valueOf(p.get(5)), p.get(6), Integer.parseInt(p.get(7)),
                    SourcePlatform.valueOf(p.get(8)));
        }
        if (p.size() == 10) {
            return new Game(UUID.fromString(p.get(0)), LocalDate.parse(p.get(1)), p.get(2),
                    Integer.parseInt(p.get(3)), Integer.parseInt(p.get(4)), PieceColor.valueOf(p.get(5)),
                    GameResult.valueOf(p.get(6)), p.get(7), Integer.parseInt(p.get(8)),
                    SourcePlatform.valueOf(p.get(9)));
        }
        if (p.size() == 12) {
            return new Game(UUID.fromString(p.get(0)), LocalDate.parse(p.get(1)), p.get(2),
                    Integer.parseInt(p.get(3)), Integer.parseInt(p.get(4)), PieceColor.valueOf(p.get(5)),
                    GameResult.valueOf(p.get(6)), p.get(7), Integer.parseInt(p.get(8)),
                    SourcePlatform.valueOf(p.get(9)), p.get(10), decodePgn(p.get(11)));
        }
        return new Game(UUID.fromString(p.get(0)), LocalDate.parse(p.get(1)), p.get(2),
                Integer.parseInt(p.get(3)), Integer.parseInt(p.get(4)), PieceColor.valueOf(p.get(5)),
                GameResult.valueOf(p.get(6)), p.get(7), Integer.parseInt(p.get(8)),
                SourcePlatform.valueOf(p.get(9)), p.get(10), decodePgn(p.get(11)),
                p.get(12), p.get(13), p.get(14), p.get(15), p.get(16), Boolean.parseBoolean(p.get(17)));
    }

    private String escape(String text) {
        return '"' + text.replace('\n', ' ').replace('\r', ' ').replace("\"", "\"\"") + '"';
    }

    private List<String> parseCsv(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    private List<Game> seedGames() {
        List<Game> seeds = new ArrayList<>(Arrays.asList(
            game("2026-09-12", "KnightRider", 1804, 1812, PieceColor.WHITE, GameResult.WIN, "Gambito da Dama", "D35", 41, SourcePlatform.CHESS_COM),
            game("2026-09-11", "TorreForte", 1798, 1794, PieceColor.BLACK, GameResult.LOSS, "Defesa Siciliana Najdorf", "B90", 32, SourcePlatform.LICHESS),
            game("2026-09-09", "BispoAtivo", 1801, 1771, PieceColor.WHITE, GameResult.WIN, "Abertura Italiana", "C50", 28, SourcePlatform.CHESS_COM),
            game("2026-09-07", "EndgameLab", 1796, 1835, PieceColor.BLACK, GameResult.DRAW, "Defesa Caro-Kann", "B12", 63, SourcePlatform.LICHESS),
            game("2026-09-05", "PeaoPassado", 1792, 1758, PieceColor.WHITE, GameResult.WIN, "Sistema Londres", "D02", 36, SourcePlatform.PGN),
            game("2026-09-03", "TaticaPura", 1787, 1860, PieceColor.BLACK, GameResult.LOSS, "Defesa India do Rei", "E97", 24, SourcePlatform.CHESS_COM),
            game("2026-08-31", "XequeDuplo", 1794, 1766, PieceColor.WHITE, GameResult.WIN, "Gambito da Dama Recusado", "D55", 52, SourcePlatform.LICHESS),
            game("2026-08-29", "CasaFraca", 1788, 1801, PieceColor.BLACK, GameResult.WIN, "Defesa Francesa", "C02", 45, SourcePlatform.CHESS_COM),
            game("2026-08-26", "OpenFile", 1779, 1788, PieceColor.WHITE, GameResult.DRAW, "Abertura Inglesa", "A20", 58, SourcePlatform.PGN),
            game("2026-08-24", "TempoCurto", 1775, 1842, PieceColor.BLACK, GameResult.LOSS, "Defesa Siciliana Dragao", "B76", 19, SourcePlatform.LICHESS),
            game("2026-08-20", "Centraliza", 1782, 1729, PieceColor.WHITE, GameResult.WIN, "Abertura Italiana", "C53", 33, SourcePlatform.CHESS_COM),
            game("2026-08-18", "Finalista", 1776, 1819, PieceColor.BLACK, GameResult.DRAW, "Defesa Caro-Kann", "B18", 71, SourcePlatform.PGN)
        ));
        return seeds;
    }

    private Game game(String date, String opponent, int playerRating, int opponentRating, PieceColor color,
                      GameResult result, String opening, String eco, int moves, SourcePlatform source) {
        return new Game(UUID.randomUUID(), LocalDate.parse(date), opponent, playerRating, opponentRating,
                color, result, opening, moves, source, eco,
                demoPgn(date, opponent, playerRating, opponentRating, color, result, opening, eco),
                "Demo Evalia", "https://lichess.org/demo",
                color == PieceColor.WHITE ? "Jogador demo" : opponent,
                color == PieceColor.WHITE ? opponent : "Jogador demo",
                "600+0", false);
    }

    private String encodePgn(String pgn) {
        return Base64.getEncoder().encodeToString(pgn.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePgn(String value) {
        if (value == null || value.isBlank()) return "";
        try { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException ex) { return ""; }
    }

    private String demoPgn(String date, String opponent, int playerRating, int opponentRating, PieceColor color,
                           GameResult result, String opening, String eco) {
        String white = color == PieceColor.WHITE ? "Jogador demo" : opponent;
        String black = color == PieceColor.WHITE ? opponent : "Jogador demo";
        String whiteElo = String.valueOf(color == PieceColor.WHITE ? playerRating : opponentRating);
        String blackElo = String.valueOf(color == PieceColor.WHITE ? opponentRating : playerRating);
        String score = switch (result) {
            case WIN -> color == PieceColor.WHITE ? "1-0" : "0-1";
            case LOSS -> color == PieceColor.WHITE ? "0-1" : "1-0";
            case DRAW -> "1/2-1/2";
        };
        return """
                [Event "Demo Evalia"]
                [Site "https://lichess.org/demo"]
                [Date "%s"]
                [White "%s"]
                [Black "%s"]
                [WhiteElo "%s"]
                [BlackElo "%s"]
                [Result "%s"]
                [ECO "%s"]
                [Opening "%s"]

                %s %s
                """.formatted(date.replace("-", "."), white, black, whiteElo, blackElo, score, eco, opening,
                demoMoves(opening), score).trim();
    }

    private String demoMoves(String opening) {
        String lower = opening.toLowerCase();
        if (lower.contains("siciliana")) return "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Be3 e5 7. Nb3 Be6 8. f3 Be7 9. Qd2 O-O 10. O-O-O Nbd7 11. g4 b5 12. g5 b4 13. Ne2 Nh5 14. Kb1 a5 15. Ng3 Nf4";
        if (lower.contains("italiana")) return "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 Bb4+ 7. Nc3 Nxe4 8. O-O Bxc3 9. d5 Bf6 10. Re1+ Ne7 11. Rxe4 d6 12. Bb5+ Bd7";
        if (lower.contains("caro")) return "1. e4 c6 2. d4 d5 3. Nc3 dxe4 4. Nxe4 Bf5 5. Ng3 Bg6 6. h4 h6 7. Nf3 Nd7 8. h5 Bh7 9. Bd3 Bxd3 10. Qxd3 e6 11. Bf4 Ngf6 12. O-O-O Be7";
        if (lower.contains("india")) return "1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 5. Nf3 O-O 6. Be2 e5 7. O-O Nc6 8. d5 Ne7 9. Ne1 Nd7 10. Be3 f5 11. f3 f4 12. Bf2 g5";
        if (lower.contains("francesa")) return "1. e4 e6 2. d4 d5 3. e5 c5 4. c3 Nc6 5. Nf3 Qb6 6. a3 Nh6 7. b4 cxd4 8. cxd4 Nf5 9. Bb2 Be7 10. h4 O-O 11. g4 Nh6 12. Rg1 f6";
        if (lower.contains("inglesa")) return "1. c4 e5 2. Nc3 Nf6 3. g3 d5 4. cxd5 Nxd5 5. Bg2 Nb6 6. Nf3 Nc6 7. O-O Be7 8. a3 O-O 9. b4 Be6 10. d3 f6 11. Be3 Nd4 12. Nd2 c6";
        if (lower.contains("londres")) return "1. d4 d5 2. Bf4 Nf6 3. e3 e6 4. Nf3 c5 5. c3 Nc6 6. Nbd2 Bd6 7. Bg3 O-O 8. Bd3 b6 9. Ne5 Bb7 10. f4 Ne7 11. Bh4 Nf5 12. Bxf6 Qxf6";
        return "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Nf3 Be7 5. Bg5 O-O 6. e3 h6 7. Bh4 b6 8. cxd5 Nxd5 9. Bxe7 Qxe7 10. Nxd5 exd5 11. Rc1 Be6 12. Bd3 c5";
    }
}
