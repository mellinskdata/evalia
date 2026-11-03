package br.edu.evalia.service;

import br.edu.evalia.model.BoardPosition;
import br.edu.evalia.model.Move;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.MoveList;
import java.util.List;

/** The UI never implements chess rules or shares a mutable board across threads. */
public final class ChessRules {
    public List<Move> legalMoves(BoardPosition position) {
        return board(position).legalMoves().stream().map(m -> new Move(m.toString())).toList();
    }

    public BoardPosition play(BoardPosition position, Move move) {
        Board board = board(position);
        var legal = board.legalMoves().stream().filter(m -> m.toString().equals(move.uci()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Lance ilegal: " + move.uci()));
        MoveList list = new MoveList(position.fen());
        list.add(legal);
        String san = list.toSanArray()[0];
        board.doMove(legal, true);
        return new BoardPosition(board.getFen(), san, position.ply() + 1, move);
    }

    public BoardPosition playSan(BoardPosition position, String san) {
        MoveList list = new MoveList(position.fen());
        list.loadFromSan(san.replace('0', 'O').replaceAll("[?!]+", ""));
        if (list.size() != 1) throw new IllegalArgumentException("Lance SAN invalido: " + san);
        return play(position, new Move(list.get(0).toString()));
    }

    public String positionKey(BoardPosition position) {
        Board board = board(position);
        String[] fields = position.fen().split(" ");
        // En passant distinguishes positions only when the capture is legal.
        if (!fields[3].equals("-")) {
            boolean possible = board.legalMoves().stream().anyMatch(m ->
                    m.getTo().toString().equalsIgnoreCase(fields[3])
                    && board.getPiece(m.getFrom()).getFenSymbol().equalsIgnoreCase("P")
                    && m.getFrom().getFile() != m.getTo().getFile());
            if (!possible) fields[3] = "-";
        }
        return String.join(" ", fields[0], fields[1], fields[2], fields[3]);
    }

    private Board board(BoardPosition position) {
        Board board = new Board();
        board.loadFromFen(position.fen());
        return board;
    }
}
