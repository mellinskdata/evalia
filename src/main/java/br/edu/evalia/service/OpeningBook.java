package br.edu.evalia.service;

import br.edu.evalia.model.*;

import java.util.*;
import java.util.stream.Collectors;

public final class OpeningBook {
    private static final List<OpeningFamily> FAMILIES = List.of(
            family("Defesa Siciliana", "B20-B99", "siciliana", "sicilian", "najdorf", "dragao", "dragon",
                    "Jogo assimetrico: pretas lutam contra o centro com ...c5 e buscam contra-jogo ativo.",
                    "Casas fracas em d5, ataques opostos e atrasos no desenvolvimento."),
            family("Defesa Francesa", "C00-C19", "francesa", "french",
                    "Centro fechado: atacar a cadeia de peoes com ...c5 e ...f6.",
                    "Bispo de c8 preso, rupturas no tempo errado e ala do rei exposta."),
            family("Defesa Caro-Kann", "B10-B19", "caro-kann", "caro kann",
                    "Estrutura solida: desenvolver com ...Bf5 ou ...Bg4 antes de ...e6.",
                    "Jogo passivo se as pretas demorarem para disputar o centro."),
            family("Abertura Italiana", "C50-C59", "italiana", "italian", "giuoco", "pianissimo",
                    "Pressao em f7, desenvolvimento rapido e ruptura d4 quando preparada.",
                    "Tatica em e4/f7 e perda de tempo com muitas jogadas do mesmo bispo."),
            family("Ruy Lopez", "C60-C99", "ruy lopez", "espanhola", "spanish",
                    "Pressionar e5 e construir vantagem de espaco com c3 e d4.",
                    "Centro solto e ataques na ala do rei se o desenvolvimento atrasar."),
            family("Gambito da Dama", "D06-D69", "gambito da dama", "queen's gambit", "qgd", "slav", "semi-slav",
                    "Disputar o centro com c4 e pressionar a coluna c/d.",
                    "Peao isolado da dama, casas c4/e4 e finais estruturais."),
            family("Sistema Londres", "D02-D03", "londres", "london",
                    "Desenvolvimento repetivel com Bf4, e3, Nf3 e estrutura solida.",
                    "Plano automatico demais: permitir ...c5/...Qb6 sem resposta."),
            family("Abertura Inglesa", "A10-A39", "inglesa", "english",
                    "Controle de d5 por c4, desenvolvimento flexivel e transposicoes.",
                    "Perder o centro se as rupturas d4 ou b4 nunca aparecem."),
            family("Defesa India do Rei", "E60-E99", "india do rei", "king's indian", "kings indian",
                    "Pretas aceitam espaco branco e atacam com ...f5 no momento certo.",
                    "Ataque branco na ala da dama chega primeiro se o centro fechar sem contra-jogo."),
            family("Defesas Indias da Dama", "E20-E59", "nimzo", "bogo", "queen's indian", "india da dama",
                    "Pressao posicional nas casas centrais e troca seletiva de pecas.",
                    "Ceder par de bispos ou centro amplo sem compensacao clara."),
            family("Jogo de Peao da Dama", "D00-D05", "peao da dama", "queen's pawn",
                    "Construir centro solido e decidir entre c4, Bf4 ou Bg5.",
                    "Ficar sem plano se a estrutura travar cedo."),
            family("Aberturas Abertas", "C20-C49", "abertura aberta", "vienna", "scotch", "four knights",
                    "Desenvolvimento rapido, disputa do centro e seguranca do rei.",
                    "Taticas em e4/e5 e reis no centro por tempo demais.")
    );

    private static final OpeningFamily UNKNOWN = new OpeningFamily("Abertura nao classificada", "--",
            List.of(), "Sem dados suficientes para definir um plano de abertura.",
            "Importe mais partidas ou PGNs com tag ECO/Opening para melhorar a classificacao.");

    private final PgnMoveExtractor extractor = new PgnMoveExtractor();

    public Optional<OpeningEntry> lookup(BoardPosition position) {
        return JsonOpeningCatalog.bundled().lookup(position);
    }

    public List<BookContinuation> nextMoves(BoardPosition position) {
        return JsonOpeningCatalog.bundled().nextMoves(position);
    }

    public List<ReferenceMoveStat> referenceStatistics(BoardPosition position) {
        return JsonOpeningCatalog.bundled().statistics(position);
    }

    public OpeningFamily classify(Game game) {
        Optional<OpeningFamily> byEco = findByEco(game.getEco());
        if (byEco.isPresent()) return byEco.get();

        String opening = game.getOpening().toLowerCase(Locale.ROOT);
        for (OpeningFamily family : FAMILIES) {
            if (family.aliases().stream().anyMatch(opening::contains)) return family;
        }

        List<String> moves = extractor.extractSan(game.getPgn());
        return classifyByMoveOrder(moves).orElse(UNKNOWN);
    }

    public List<OpeningStat> summarize(List<Game> games) {
        Map<OpeningFamily, List<Game>> grouped = games.stream()
                .collect(Collectors.groupingBy(this::classify));
        return grouped.entrySet().stream()
                .map(entry -> stat(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(OpeningStat::games).reversed()
                        .thenComparing(stat -> stat.family().name()))
                .toList();
    }

    public static Optional<String> nameForEco(String eco) {
        return findByEco(eco).map(OpeningFamily::name);
    }

    private OpeningStat stat(OpeningFamily family, List<Game> games) {
        int wins = count(games, GameResult.WIN);
        int draws = count(games, GameResult.DRAW);
        int losses = count(games, GameResult.LOSS);
        double scoreRate = games.isEmpty() ? 0 : (wins + draws * 0.5) * 100.0 / games.size();
        return new OpeningStat(family, games.size(), wins, draws, losses, scoreRate);
    }

    private int count(List<Game> games, GameResult result) {
        return (int) games.stream().filter(game -> game.getResult() == result).count();
    }

    private Optional<OpeningFamily> classifyByMoveOrder(List<String> moves) {
        if (moves.size() < 2) return Optional.empty();
        String first = normalize(moves.get(0));
        String second = normalize(moves.get(1));
        if (first.equals("e4") && second.equals("c5")) return named("Defesa Siciliana");
        if (first.equals("e4") && second.equals("e6")) return named("Defesa Francesa");
        if (first.equals("e4") && second.equals("c6")) return named("Defesa Caro-Kann");
        if (first.equals("e4") && second.equals("e5")) {
            if (moves.size() > 4 && normalize(moves.get(4)).startsWith("Bc4")) return named("Abertura Italiana");
            if (moves.size() > 4 && normalize(moves.get(4)).startsWith("Bb5")) return named("Ruy Lopez");
            return named("Aberturas Abertas");
        }
        if (first.equals("d4") && second.equals("Nf6") && moves.stream().limit(8).map(this::normalize)
                .anyMatch(move -> move.equals("g6"))) return named("Defesa India do Rei");
        if (first.equals("d4") && moves.stream().limit(6).map(this::normalize)
                .anyMatch(move -> move.equals("Bf4"))) return named("Sistema Londres");
        if (first.equals("d4") && moves.size() > 2 && normalize(moves.get(2)).equals("c4")) return named("Gambito da Dama");
        if (first.equals("c4")) return named("Abertura Inglesa");
        return Optional.empty();
    }

    private Optional<OpeningFamily> named(String name) {
        return FAMILIES.stream().filter(family -> family.name().equals(name)).findFirst();
    }

    private static Optional<OpeningFamily> findByEco(String eco) {
        if (eco == null || !eco.matches("[A-Ea-e]\\d{2}")) return Optional.empty();
        char letter = Character.toUpperCase(eco.charAt(0));
        int number = Integer.parseInt(eco.substring(1));
        for (OpeningFamily family : FAMILIES) {
            if (matchesRange(letter, number, family.ecoRange())) return Optional.of(family);
        }
        return Optional.empty();
    }

    private static boolean matchesRange(char letter, int number, String range) {
        if (!range.matches("[A-E]\\d{2}-[A-E]\\d{2}")) return false;
        char startLetter = range.charAt(0);
        int startNumber = Integer.parseInt(range.substring(1, 3));
        char endLetter = range.charAt(4);
        int endNumber = Integer.parseInt(range.substring(5, 7));
        if (letter < startLetter || letter > endLetter) return false;
        if (letter == startLetter && number < startNumber) return false;
        return letter != endLetter || number <= endNumber;
    }

    private static OpeningFamily family(String name, String ecoRange, String alias1, String alias2,
                                        String mainPlan, String typicalRisk) {
        return new OpeningFamily(name, ecoRange, List.of(alias1, alias2), mainPlan, typicalRisk);
    }

    private static OpeningFamily family(String name, String ecoRange, String alias1, String alias2,
                                        String alias3, String mainPlan, String typicalRisk) {
        return new OpeningFamily(name, ecoRange, List.of(alias1, alias2, alias3), mainPlan, typicalRisk);
    }

    private static OpeningFamily family(String name, String ecoRange, String alias1, String alias2,
                                        String alias3, String alias4, String mainPlan, String typicalRisk) {
        return new OpeningFamily(name, ecoRange, List.of(alias1, alias2, alias3, alias4), mainPlan, typicalRisk);
    }

    private static OpeningFamily family(String name, String ecoRange, String alias1, String alias2,
                                        String alias3, String alias4, String alias5,
                                        String mainPlan, String typicalRisk) {
        return new OpeningFamily(name, ecoRange, List.of(alias1, alias2, alias3, alias4, alias5), mainPlan, typicalRisk);
    }

    private String normalize(String move) {
        return move.replaceAll("[+#?!]", "").replace("0", "O");
    }
}
