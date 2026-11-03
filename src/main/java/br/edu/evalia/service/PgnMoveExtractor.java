package br.edu.evalia.service;

import java.util.ArrayList;
import java.util.List;

public final class PgnMoveExtractor {
    public List<String> extractSan(String pgn) {
        if (pgn == null || pgn.isBlank()) return List.of();
        String text = stripAnnotations(pgn.replaceAll("(?m)^\\s*\\[[^\\n]*\\]", " "))
                .replaceAll("\\$\\d+", " ")
                .replaceAll("\\r?\\n", " ");
        String[] tokens = text.trim().split("\\s+");
        List<String> moves = new ArrayList<>();
        for (String token : tokens) {
            String clean = token.trim();
            if (clean.isBlank()) continue;
            clean = clean.replaceAll("^\\d+\\.(\\.\\.)?", "");
            if (clean.isBlank() || isResult(clean) || clean.equals("...") || clean.equals("e.p.")) continue;
            if (clean.matches("\\d+\\.(\\.\\.)?")) continue;
            moves.add(clean);
        }
        return List.copyOf(moves);
    }

    private String stripAnnotations(String pgn) {
        StringBuilder clean = new StringBuilder();
        int braceDepth = 0;
        int variationDepth = 0;
        boolean lineComment = false;
        for (int i = 0; i < pgn.length(); i++) {
            char c = pgn.charAt(i);
            if (lineComment) {
                if (c == '\n') { lineComment = false; clean.append(' '); }
                continue;
            }
            if ((c == ';' || c == '%') && braceDepth == 0) { lineComment = true; continue; }
            if (c == '{') {
                braceDepth++;
            } else if (c == '}' && braceDepth > 0) {
                braceDepth--;
                clean.append(' ');
            } else if (c == '(' && braceDepth == 0) {
                variationDepth++;
            } else if (c == ')' && variationDepth > 0) {
                variationDepth--;
            } else if (braceDepth == 0 && variationDepth == 0) {
                clean.append(c);
            }
        }
        return clean.toString();
    }

    private boolean isResult(String token) {
        return token.equals("1-0") || token.equals("0-1")
                || token.equals("1/2-1/2") || token.equals("*");
    }
}
