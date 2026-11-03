# EVALIA — Chess Analysis Platform

**See beyond the moves.**

EVALIA is a Java 17 / JavaFX desktop application for chess game review and performance analysis. It combines Chess.com and Lichess game imports, PGN replay, player insights, opening references, and Stockfish-powered analysis.

## Features

- Chess.com and Lichess public account synchronization
- PGN file import and interactive chessboard replay
- Stockfish game review and move classification
- Opening book, opening tree, and player performance insights
- Local game storage, filtering, and historical collections
- Native desktop interface with responsive chessboard and fullscreen mode

## Tech stack

Java 17, JavaFX, CSS, Maven, JUnit 5, Stockfish (UCI), Jackson and public chess platform APIs.

## Build from source

Requires JDK 17+ and Maven 3.9+.

```bash
mvn clean test
mvn javafx:run
```

Windows packaging scripts are included in the full source distribution. Stockfish is downloaded automatically on Windows for first use, or can be configured manually.

## Publication status

**Source upload in progress:** this repository currently contains the project overview and setup notes. The complete JavaFX source distribution (v2.1) must be uploaded before the build instructions will work from a fresh clone.

This project is an independent chess-analysis application, not affiliated with Chess.com, Lichess or Stockfish.
