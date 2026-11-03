package br.edu.evalia.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Tiny deterministic UCI process used by StockfishServiceTest. */
public final class FakeUciEngine {
    private FakeUciEngine() { }

    public static void main(String[] args) throws Exception {
        int multiPv = 1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String command = line.trim();
                if (command.equals("uci")) {
                    System.out.println("id name Evalia FakeFish");
                    System.out.println("option name MultiPV type spin default 1 min 1 max 8");
                    System.out.println("uciok");
                    System.out.flush();
                } else if (command.equals("isready")) {
                    System.out.println("readyok");
                    System.out.flush();
                } else if (command.startsWith("setoption name MultiPV value ")) {
                    multiPv = Integer.parseInt(command.substring(command.lastIndexOf(' ') + 1));
                } else if (command.startsWith("go depth ")) {
                    if (multiPv >= 2) {
                        System.out.println("info depth 10 multipv 1 score cp 180 pv e2e4 e7e5 g1f3");
                        System.out.println("info depth 10 multipv 2 score cp 20 pv d2d4 d7d5 c2c4");
                    } else {
                        System.out.println("info depth 12 score cp 35 pv e2e4 e7e5 g1f3");
                    }
                    System.out.println("bestmove e2e4");
                    System.out.flush();
                } else if (command.equals("quit")) {
                    return;
                }
            }
        }
    }
}
