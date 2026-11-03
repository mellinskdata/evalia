package br.edu.evalia.service;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;

/** A bounded PGN framer. Headers inside comments/variations never split a game. */
public final class PgnStreamReader implements Closeable {
    private static final int MAX_GAME_CHARS = 2 * 1024 * 1024;
    private final BufferedReader reader;
    private int pending = -1;
    private long characters;

    public PgnStreamReader(Reader reader) { this.reader = new BufferedReader(reader, 64 * 1024); }
    public long charactersRead() { return characters; }

    public String nextGame() throws IOException {
        StringBuilder text = new StringBuilder();
        boolean tag = false, quoted = false, escaped = false, comment = false, lineComment = false;
        boolean hasMoves = false;
        int variation = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Importacao cancelada.");
            int value;
            if (pending != -1) { value = pending; pending = -1; }
            else { value = reader.read(); if (value != -1) characters++; }
            if (value == -1) return text.toString().isBlank() ? null : text.toString();
            char c = (char) value;
            if (c == '\uFEFF' && text.isEmpty()) continue;
            if (!tag && !comment && !lineComment && variation == 0 && c == '[' && hasMoves) {
                pending = value;
                return text.toString();
            }
            text.append(c);
            if (text.length() > MAX_GAME_CHARS) {
                throw new IOException("Uma partida excedeu 2 MiB. Verifique o PGN. Lotes anteriores foram preservados.");
            }
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (comment) { if (c == '}') comment = false; continue; }
            if (tag) {
                if (escaped) escaped = false;
                else if (c == '\\' && quoted) escaped = true;
                else if (c == '"') quoted = !quoted;
                else if (c == ']' && !quoted) tag = false;
                continue;
            }
            if (c == ';' || c == '%') lineComment = true;
            else if (c == '{') comment = true;
            else if (c == '(') variation++;
            else if (c == ')') variation = Math.max(0, variation - 1);
            else if (c == '[' && variation == 0) tag = true;
            else if (!Character.isWhitespace(c) && variation == 0) hasMoves = true;
        }
    }

    @Override public void close() throws IOException { reader.close(); }
}
