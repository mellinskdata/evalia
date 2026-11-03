package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Full-game review driven by Stockfish evaluation changes instead of PGN heuristics. */
public final class EngineGameReviewService {
    private static final int MATE_SCORE = 100_000;

    /*
     * Annotation philosophy:
     * - most sound moves are NORMAL and receive no punctuation;
     * - ! / !! are intentionally rare and require evidence that the move mattered;
     * - ?! / ? / ?? are based on both centipawn loss and the resulting position,
     *   so an already-lost position does not produce a stream of fake blunders.
     */
    private static final int DUBIOUS_LOSS = 55;
    private static final int BAD_LOSS = 125;
    private static final int GREAT_SECOND_CHOICE_GAP = 70;
    private static final int BRILLIANT_SECOND_CHOICE_GAP = 120;

    private final ChessGameReplay replay = new ChessGameReplay();
    private final OpeningBook openingBook = new OpeningBook();

    public EngineGameReview review(Game game, StockfishService engine, int depth,
                                   BooleanSupplier cancelled, Consumer<String> progress) throws Exception {
        List<String> sanMoves = replay.moves(game);
        List<BoardPosition> positions = replay.positions(game);
        if (positions.size() != sanMoves.size() + 1) throw new IllegalStateException("Replay inconsistente.");

        List<EngineAnalysis> analyses = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            checkCancelled(cancelled);
            progress.accept("Analisando posição " + (i + 1) + " de " + positions.size() + "...");
            analyses.add(engine.analyze(positions.get(i).fen(), depth, Duration.ofSeconds(25), cancelled));
        }

        List<MoveAssessment> assessments = new ArrayList<>();
        int multiPvChecks = 0;
        for (int i = 0; i < sanMoves.size(); i++) {
            checkCancelled(cancelled);
            BoardPosition before = positions.get(i);
            BoardPosition after = positions.get(i + 1);
            EngineAnalysis beforeAnalysis = analyses.get(i);
            EngineAnalysis afterAnalysis = analyses.get(i + 1);
            String actualUci = after.move() == null ? "" : after.move().uci();

            int bestForMover = score(beforeAnalysis);
            int actualForMover = -score(afterAnalysis);
            int loss = normalizedLoss(bestForMover, actualForMover);
            boolean bestMove = beforeAnalysis.bestMove().map(Move::uci).orElse("").equals(actualUci);
            boolean book = i < 30 && openingBook.nextMoves(before).stream()
                    .anyMatch(bookMove -> bookMove.move().uci().equals(actualUci));

            boolean uniqueWinningIdea = false;
            boolean greatMoveIdea = false;
            int secondChoiceGap = 0;

            /*
             * Do not reward every Stockfish first choice with '!'. We only ask MultiPV for
             * non-book best moves in positions where the decision can matter. If the second
             * choice is substantially worse, the move can earn !; if it is the only move that
             * keeps a win/mate, it can earn !!.
             */
            boolean decisionPosition = beforeAnalysis.mateIn() != null || Math.abs(bestForMover) <= 650;
            if (bestMove && !book && multiPvChecks < 14 && decisionPosition) {
                multiPvChecks++;
                try {
                    List<EngineAnalysis> lines = engine.analyzeMultiPv(before.fen(), Math.max(9, depth - 1), 2,
                            Duration.ofSeconds(25), cancelled);
                    if (lines.size() >= 2) {
                        int first = score(lines.get(0));
                        int second = score(lines.get(1));
                        secondChoiceGap = Math.max(0, first - second);

                        boolean firstWins = first >= 150
                                || (lines.get(0).mateIn() != null && lines.get(0).mateIn() > 0);
                        boolean secondStillWins = second >= 100
                                || (lines.get(1).mateIn() != null && lines.get(1).mateIn() > 0);
                        boolean secondClearlyInferior = secondChoiceGap >= GREAT_SECOND_CHOICE_GAP;

                        uniqueWinningIdea = firstWins
                                && !secondStillWins
                                && secondChoiceGap >= BRILLIANT_SECOND_CHOICE_GAP;
                        greatMoveIdea = secondClearlyInferior;
                    }
                } catch (java.util.concurrent.TimeoutException ignored) {
                    // Optional nuance check. The main evaluation remains valid if MultiPV times out.
                }
            }

            MoveClassification classification = classify(book, uniqueWinningIdea, greatMoveIdea, bestMove, loss,
                    bestForMover, actualForMover, beforeAnalysis, afterAnalysis);
            String bestUci = beforeAnalysis.bestMove().map(Move::uci).orElse("--");
            String explanation = explanation(classification, loss, bestForMover, actualForMover,
                    bestUci, uniqueWinningIdea, secondChoiceGap);
            PieceColor side = i % 2 == 0 ? PieceColor.WHITE : PieceColor.BLACK;
            assessments.add(new MoveAssessment(i, i / 2 + 1, side, sanMoves.get(i), classification, loss,
                    bestForMover, actualForMover, bestMove, uniqueWinningIdea, bestUci, explanation));
        }

        List<MoveFeedback> keyMoves = keyMoves(assessments);
        return new EngineGameReview(assessments, keyMoves,
                averageLoss(assessments, PieceColor.WHITE), averageLoss(assessments, PieceColor.BLACK));
    }

    private MoveClassification classify(boolean book, boolean unique, boolean greatIdea, boolean bestMove, int loss,
                                        int before, int after, EngineAnalysis beforeAnalysis, EngineAnalysis afterAnalysis) {
        if (book && loss <= 35) return MoveClassification.BOOK;

        // ?? is deliberately strict: the move must cause an immediate decisive collapse.
        if (isCatastrophicBlunder(beforeAnalysis, afterAnalysis, before, after, loss)) {
            return MoveClassification.BLUNDER;
        }

        // Clear error: sizeable loss that leaves the player materially/positionally much worse.
        if (loss >= BAD_LOSS || crossesIntoClearlyWorse(before, after, loss)) {
            return MoveClassification.BAD;
        }

        // Dubious/inaccuracy: noticeable concession, but not a losing-on-the-spot move.
        if (loss >= DUBIOUS_LOSS) {
            return MoveClassification.DUBIOUS;
        }

        // !! is rare: best move, essentially no loss, and the only continuation retaining a win/mate.
        if (unique && bestMove && loss <= 10) {
            return MoveClassification.BRILLIANT;
        }

        // ! is also selective: being Stockfish's #1 move is not enough by itself.
        // The second-best choice must be meaningfully worse, making this an important precise move.
        if (greatIdea && bestMove && loss <= 15) {
            return MoveClassification.GREAT;
        }

        // Correct/good/quiet moves are intentionally left unannotated.
        return MoveClassification.NORMAL;
    }

    private boolean isCatastrophicBlunder(EngineAnalysis beforeAnalysis, EngineAnalysis afterAnalysis,
                                          int before, int after, int loss) {
        // A move that directly allows a forced mate when there was no forced mate against the mover.
        boolean opponentHasForcedMateAfter = afterAnalysis.mateIn() != null && afterAnalysis.mateIn() > 0;
        boolean moverWasNotAlreadyMated = beforeAnalysis.mateIn() == null || beforeAnalysis.mateIn() >= 0;
        if (opponentHasForcedMateAfter && moverWasNotAlreadyMated) return true;

        // Winning -> clearly losing in one move.
        if (before >= 150 && after <= -180 && loss >= 330) return true;

        // Roughly equal/better -> decisively lost, with a very large engine swing.
        if (before >= -60 && after <= -300 && loss >= 300) return true;

        // A huge drop can still be ??, but only if the resulting position is actually lost.
        return loss >= 450 && after <= -250;
    }

    private boolean crossesIntoClearlyWorse(int before, int after, int loss) {
        // Do not mark every move in an already-lost game with '?'. The move needs to create
        // the serious disadvantage, not merely continue to play from one.
        return before >= -80 && after <= -160 && loss >= 100;
    }

    private List<MoveFeedback> keyMoves(List<MoveAssessment> moves) {
        return moves.stream()
                .filter(m -> m.uniqueWinningIdea()
                        || m.classification() == MoveClassification.BRILLIANT
                        || m.classification() == MoveClassification.GREAT
                        || m.classification() == MoveClassification.BAD
                        || m.classification() == MoveClassification.BLUNDER
                        || m.centipawnLoss() >= 90)
                .sorted(Comparator.comparingInt(MoveAssessment::plyIndex))
                .limit(10)
                .map(this::feedback)
                .toList();
    }

    private MoveFeedback feedback(MoveAssessment move) {
        String theme;
        if (move.uniqueWinningIdea()) theme = "Única ideia vencedora";
        else if (move.classification() == MoveClassification.BLUNDER) theme = "Colapso imediato da posição";
        else if (move.classification() == MoveClassification.BAD) theme = "Erro que muda bastante a avaliação";
        else if (move.classification() == MoveClassification.BRILLIANT) theme = "Lance excepcional";
        else if (move.classification() == MoveClassification.GREAT) theme = "Lance preciso em momento crítico";
        else theme = "Mudança importante de avaliação";

        String annotation = move.classification().annotation();
        String moveText = move.san() + (annotation == null || annotation.isBlank() ? "" : " " + annotation);
        return new MoveFeedback(move.moveNumber(), move.side(), moveText,
                move.classification().label(), theme, move.explanation(), move.classification().cssClass());
    }

    private String explanation(MoveClassification classification, int loss, int before, int after,
                               String bestUci, boolean unique, int secondChoiceGap) {
        if (classification == MoveClassification.BOOK) {
            return "Lance reconhecido pelo livro de aberturas e sem perda relevante de avaliação.";
        }
        if (classification == MoveClassification.BRILLIANT && unique) {
            return "Lance excepcional: o Stockfish encontrou esta como a única continuação que preservava uma vitória clara. "
                    + "Avaliação: " + eval(before) + " → " + eval(after) + ".";
        }
        if (classification == MoveClassification.GREAT) {
            return "Lance muito preciso em posição crítica. A segunda opção da engine era cerca de "
                    + String.format(Locale.ROOT, "%.2f", secondChoiceGap / 100.0)
                    + " pior. Avaliação: " + eval(before) + " → " + eval(after) + ".";
        }
        if (classification == MoveClassification.NORMAL) {
            if (loss <= 20) {
                return "Lance correto: mantém praticamente toda a avaliação da posição. Melhor lance da engine: "
                        + bestUci + ".";
            }
            return "Lance aceitável, sem mudança suficiente para receber uma anotação especial. Avaliação: "
                    + eval(before) + " → " + eval(after) + ".";
        }
        if (classification == MoveClassification.BLUNDER) {
            return "Erro decisivo: a posição entra imediatamente em território perdido ou permite mate. Avaliação: "
                    + eval(before) + " → " + eval(after) + ". Melhor lance: " + bestUci + ".";
        }
        return "A avaliação para quem jogou mudou de " + eval(before) + " para " + eval(after)
                + " (perda aproximada de " + String.format(Locale.ROOT, "%.2f", loss / 100.0)
                + "). Melhor lance: " + bestUci + ".";
    }

    private int score(EngineAnalysis analysis) {
        if (analysis.mateIn() != null) {
            int mate = analysis.mateIn();
            if (mate > 0) return MATE_SCORE - Math.min(900, Math.abs(mate));
            if (mate < 0) return -MATE_SCORE + Math.min(900, Math.abs(mate));
            return 0;
        }
        return analysis.centipawns() == null ? 0 : analysis.centipawns();
    }

    private int normalizedLoss(int best, int actual) {
        if (best > 50_000 && actual > 50_000) return 0;
        if (best < -50_000 && actual < -50_000) return 0;
        return Math.min(10_000, Math.max(0, best - actual));
    }

    private String eval(int cp) {
        if (cp > 50_000) return "mate a favor";
        if (cp < -50_000) return "mate contra";
        return String.format(Locale.ROOT, "%+.2f", cp / 100.0);
    }

    private double averageLoss(List<MoveAssessment> moves, PieceColor color) {
        return moves.stream().filter(m -> m.side() == color).mapToInt(MoveAssessment::centipawnLoss).average().orElse(0.0);
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Análise cancelada.");
    }
}
