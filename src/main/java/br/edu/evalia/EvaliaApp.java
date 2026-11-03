package br.edu.evalia;

import br.edu.evalia.model.*;
import br.edu.evalia.integration.*;
import br.edu.evalia.repository.CsvGameRepository;
import br.edu.evalia.repository.GameRepository;
import br.edu.evalia.service.*;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EvaliaApp extends Application {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private GameRepository repository;
    private GameRepository historicalRepository;
    private AnalyticsService analyticsService;
    private AccountSettings accountSettings;
    private final OpeningBook openingBook = new OpeningBook();
    private final ChessGameReplay replayService = new ChessGameReplay();
    private final GameReviewService reviewService = new GameReviewService();
    private final GameDatabaseService databaseService = new GameDatabaseService();
    private final OpeningTreeService openingTreeService = new OpeningTreeService();
    private final InsightCenterService insightCenterService = new InsightCenterService();
    private final ReportPdfService reportPdfService = new ReportPdfService();
    private final ChessRules chessRules = new ChessRules();
    private GameAnnotationsService annotationsService;
    private EngineSettings engineSettings;
    private StockfishService stockfish;
    private final EngineGameReviewService engineGameReviewService = new EngineGameReviewService();
    private final Map<java.util.UUID, EngineGameReview> engineReviewCache = new HashMap<>();
    private final Map<Character, Image> pieceImages = new HashMap<>();
    private DashboardSnapshot snapshot;
    private BorderPane shell;
    private VBox content;
    private final ToggleGroup navigation = new ToggleGroup();
    private Label profileNameLabel;
    private Label profileRatingLabel;
    private Label dataStatusLabel;
    private boolean syncing;
    private Task<SyncOutcome> activeSyncTask;
    private boolean accountSwitchMode;
    private ImportComparison lastComparison;

    @Override
    public void start(Stage stage) throws Exception {
        Path accountFile = Path.of("data", "account-games.csv");
        Path historicalFile = Path.of("data", "historical-games.csv");
        repository = new CsvGameRepository(accountFile);
        historicalRepository = new CsvGameRepository(historicalFile, false);
        migrateLegacyDatabase(accountFile, historicalFile);
        seedHistoricalDatabase();
        analyticsService = new AnalyticsService(repository);
        accountSettings = new AccountSettings(Path.of("data", "accounts.properties"));
        annotationsService = new GameAnnotationsService(Path.of("data", "annotations.properties"));
        engineSettings = new EngineSettings(Path.of("data", "engine.properties"));
        snapshot = analyticsService.buildSnapshot();

        shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setLeft(buildSidebar());
        shell.setTop(buildTopbar(stage));

        content = new VBox();
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        shell.setCenter(scroll);
        showDashboard();

        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/evalia.css").toExternalForm());
        stage.setTitle("Evalia - See beyond the moves");
        stage.setMinWidth(1050);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setFullScreenExitHint("ESC para sair da tela cheia");
        stage.setFullScreenExitKeyCombination(KeyCombination.valueOf("ESC"));
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
            }
        });
        stage.setOnCloseRequest(event -> closeStockfish());
        stage.setMaximized(true);
        stage.show();
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(230);

        Label brand = new Label("EVALIA");
        brand.getStyleClass().add("brand");
        Label tagline = new Label("See beyond the moves.");
        tagline.getStyleClass().add("tagline");
        VBox brandBox = new VBox(2, brand, tagline);
        brandBox.getStyleClass().add("brand-box");

        Label menu = new Label("MENU");
        menu.getStyleClass().add("menu-label");

        ToggleButton dashboard = navButton("Dashboard", "D");
        dashboard.setSelected(true);
        dashboard.setOnAction(e -> showDashboard());
        ToggleButton games = navButton("Partidas", "P");
        games.setOnAction(e -> showGames());
        ToggleButton database = navButton("Database", "DB");
        database.setOnAction(e -> showDatabase());
        ToggleButton openings = navButton("Aberturas", "O");
        openings.setOnAction(e -> showOpeningBook());
        ToggleButton immortal = navButton("Imortal", "★");
        immortal.setOnAction(e -> showImmortal());
        ToggleButton insights = navButton("Insights", "I");
        insights.setOnAction(e -> showInsights());
        ToggleButton training = navButton("Treino", "T");
        training.setOnAction(e -> showTraining());
        ToggleButton presentation = navButton("Apresentação", "A");
        presentation.setOnAction(e -> showPresentation());
        ToggleButton importer = navButton("Sincronizar contas", "+");
        importer.setOnAction(e -> showConnections());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox profileCard = new VBox(4);
        profileCard.getStyleClass().add("mini-profile");
        profileNameLabel = new Label(accountSettings.activeUsername());
        profileNameLabel.getStyleClass().add("mini-profile-name");
        profileRatingLabel = new Label(ratingText());
        profileRatingLabel.getStyleClass().add("muted");
        profileCard.getChildren().addAll(profileNameLabel, profileRatingLabel);

        sidebar.getChildren().addAll(brandBox, menu, dashboard, games, database, openings, immortal, insights,
                training, presentation, importer, spacer, profileCard);
        return sidebar;
    }

    private ToggleButton navButton(String text, String symbol) {
        Label icon = new Label(symbol);
        icon.getStyleClass().add("nav-icon");
        Label label = new Label(text);
        HBox graphic = new HBox(12, icon, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        ToggleButton button = new ToggleButton();
        button.setGraphic(graphic);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(navigation);
        return button;
    }

    private Node buildTopbar(Stage stage) {
        BorderPane topbar = new BorderPane();
        topbar.getStyleClass().add("topbar");
        Button back = new Button("Voltar");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(e -> showDashboard());
        Button changeAccount = new Button("Alterar conta");
        changeAccount.getStyleClass().add("secondary-button");
        changeAccount.setOnAction(e -> switchAccount());
        dataStatusLabel = new Label(repository.isDemoMode() ? "●  Modo demonstração" : "●  Dados sincronizados");
        dataStatusLabel.getStyleClass().add("status-online");
        Button export = new Button("Exportar dashboard");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportDashboard(stage));
        Button fullScreen = new Button("Tela cheia  F11");
        fullScreen.getStyleClass().add("secondary-button");
        fullScreen.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));
        stage.fullScreenProperty().addListener((obs, old, value) ->
                fullScreen.setText(value ? "Sair da tela cheia  Esc" : "Tela cheia  F11"));
        HBox left = new HBox(10, back, changeAccount);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox right = new HBox(12, dataStatusLabel, fullScreen, export);
        right.setAlignment(Pos.CENTER_RIGHT);
        topbar.setLeft(left);
        topbar.setRight(right);
        return topbar;
    }

    private void showDashboard() {
        refreshSnapshot();
        beginPage("Performance Profile", "O que suas partidas dizem sobre o seu xadrez.");

        if (repository.isDemoMode()) {
            Label demo = new Label("Você está vendo dados de demonstração. Conecte seu usuário do Chess.com ou Lichess para carregar partidas reais.");
            demo.setWrapText(true);
            demo.getStyleClass().add("demo-banner");
            content.getChildren().add(demo);
        }

        HBox stats = new HBox(14,
                statCard("PARTIDAS", String.valueOf(snapshot.totalGames()), "histórico analisado"),
                statCard("TAXA DE VITÓRIA", "%.1f%%".formatted(snapshot.winRate()), snapshot.wins() + " vitórias"),
                statCard("RATING ATUAL", snapshot.currentRating() == 0 ? "--" : String.valueOf(snapshot.currentRating()), accountSettings.activeUsername()),
                statCard("MELHOR ABERTURA", snapshot.bestOpening(), "mínimo de 2 partidas"));
        stats.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        VBox performance = sectionCard("Perfil por área", buildMetricList(snapshot.scores()));
        VBox insightList = new VBox(12);
        snapshot.insights().stream().limit(2).map(this::insightCard).forEach(insightList.getChildren()::add);
        VBox insights = sectionCard("Diagnóstico rápido", insightList);

        HBox mainGrid = new HBox(16, performance, insights);
        HBox.setHgrow(performance, Priority.ALWAYS);
        HBox.setHgrow(insights, Priority.ALWAYS);
        performance.setMaxWidth(Double.MAX_VALUE);
        insights.setMaxWidth(Double.MAX_VALUE);

        VBox recent = sectionCard("Partidas recentes", buildGameTable(snapshot.recentGames(), 300));
        content.getChildren().addAll(stats, mainGrid, recent);
    }

    private void showGames() {
        refreshSnapshot();
        beginPage("Game Explorer", "Consulte o histórico completo e encontre padrões recorrentes.");

        HBox summary = new HBox(14,
                compactSummary("Vitórias", snapshot.wins(), "summary-win"),
                compactSummary("Empates", snapshot.draws(), "summary-draw"),
                compactSummary("Derrotas", snapshot.losses(), "summary-loss"));
        TableView<Game> table = buildGameTable(repository.findAll(), 520);
        Button open = new Button("Abrir partida selecionada");
        open.getStyleClass().add("primary-button");
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(event -> showGameDetail(table.getSelectionModel().getSelectedItem()));
        HBox actions = new HBox(open);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox tableContent = new VBox(12, actions, table);
        VBox tableCard = sectionCard("Todas as partidas", tableContent);
        content.getChildren().addAll(summary, tableCard);
    }

    private void showDatabase() {
        refreshSnapshot();
        beginPage("Game Database", "Pesquise partidas por jogador, posição, abertura, torneio ou resultado.");

        TextField player = new TextField();
        player.setPromptText("Jogador: Eduardo, Magnus, Rival...");
        TextField position = new TextField();
        position.setPromptText("Posição/FEN ou lance: Nxe5, rnbq, peão passado...");
        TextField opening = new TextField();
        opening.setPromptText("Abertura ou ECO: Siciliana, E98...");
        TextField tournament = new TextField();
        tournament.setPromptText("Torneio/evento: Brasileiro sub 18...");
        ChoiceBox<String> result = new ChoiceBox<>(FXCollections.observableArrayList("Todos", "Vitoria", "Empate", "Derrota"));
        result.setValue("Todos");
        Button search = new Button("Pesquisar");
        search.getStyleClass().add("primary-button");
        Button clear = new Button("Limpar");
        clear.getStyleClass().add("secondary-button");

        List<Game> accountGames = repository.findAll();
        List<Game> historicalGames = historicalRepository.findAll();
        List<Game> databaseGames = mergeDatabaseGames(accountGames, historicalGames);
        TableView<Game> table = buildGameTable(databaseGames, 480);
        Label count = new Label(databaseGames.size() + " partida(s) no banco local — "
                + accountGames.size() + " da conta e " + historicalGames.size() + " históricas");
        count.getStyleClass().add("muted");
        Runnable update = () -> {
            List<Game> filtered = databaseService.search(databaseGames,
                    new GameSearchCriteria(player.getText(), position.getText(), opening.getText(),
                            tournament.getText(), result.getValue()));
            table.setItems(FXCollections.observableArrayList(filtered));
            count.setText(filtered.size() + " partida(s) encontrada(s)");
        };
        search.setOnAction(e -> update.run());
        clear.setOnAction(e -> {
            player.clear();
            position.clear();
            opening.clear();
            tournament.clear();
            result.setValue("Todos");
            update.run();
        });

        GridPane filters = new GridPane();
        filters.getStyleClass().add("filter-grid");
        filters.setHgap(12);
        filters.setVgap(10);
        filters.add(fieldLabel("JOGADOR"), 0, 0);
        filters.add(player, 0, 1);
        filters.add(fieldLabel("POSICAO / FEN / LANCE"), 1, 0);
        filters.add(position, 1, 1);
        filters.add(fieldLabel("ABERTURA / ECO"), 0, 2);
        filters.add(opening, 0, 3);
        filters.add(fieldLabel("TORNEIO / EVENTO"), 1, 2);
        filters.add(tournament, 1, 3);
        filters.add(fieldLabel("RESULTADO"), 2, 0);
        filters.add(result, 2, 1);
        filters.add(new HBox(8, search, clear), 2, 3);
        ColumnConstraints wide = new ColumnConstraints();
        wide.setPercentWidth(38);
        ColumnConstraints wide2 = new ColumnConstraints();
        wide2.setPercentWidth(38);
        ColumnConstraints narrow = new ColumnConstraints();
        narrow.setPercentWidth(24);
        filters.getColumnConstraints().addAll(wide, wide2, narrow);

        Button open = new Button("Abrir partida");
        open.getStyleClass().add("primary-button");
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(e -> showGameDetail(table.getSelectionModel().getSelectedItem()));
        Button exportBase = new Button("Exportar base histórica");
        exportBase.getStyleClass().add("secondary-button");
        exportBase.setOnAction(e -> exportBase());
        Button importBase = new Button("Importar base histórica");
        importBase.getStyleClass().add("secondary-button");
        importBase.setOnAction(e -> importBase());
        HBox actions = new HBox(12, count, exportBase, importBase, open);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(count, Priority.ALWAYS);
        VBox body = new VBox(14, filters, actions, table);
        content.getChildren().add(sectionCard("Banco local — conta + histórico", body));
    }

    private void showOpeningBook() {
        refreshSnapshot();
        beginPage("Opening Tree", "Explore suas linhas e compare sua base com o livro mestre offline.");

        List<Game> accountGames = repository.findAll();
        ArrayList<String> line = new ArrayList<>();
        BoardPosition[] currentPosition = {BoardPosition.initial()};
        TableView<OpeningTreeRow> tree = buildOpeningTreeTable();
        Label currentLine = new Label();
        currentLine.getStyleClass().add("current-line");
        VBox referenceBook = new VBox(8);
        ProgressIndicator referenceProgress = new ProgressIndicator();
        referenceProgress.setMaxSize(22, 22);
        referenceProgress.setVisible(false);
        Label localReferenceStatus = new Label();
        localReferenceStatus.getStyleClass().add("muted");
        Button scanLocal = new Button("Comparar com base local");
        scanLocal.getStyleClass().add("secondary-button");
        Button cancelScan = new Button("Cancelar");
        cancelScan.getStyleClass().add("secondary-button");
        cancelScan.setDisable(true);
        final Task<?>[] activeScan = {null};

        Runnable refreshTree = () -> {
            currentLine.setText(openingTreeService.lineText(line));
            tree.setItems(FXCollections.observableArrayList(openingTreeService.nextMoves(accountGames, line)));
            try {
                BoardPosition position = BoardPosition.initial();
                for (String san : line) position = chessRules.playSan(position, san);
                currentPosition[0] = position;
                renderReferenceBook(referenceBook, position);
                localReferenceStatus.setText("");
            } catch (RuntimeException ex) {
                referenceBook.getChildren().setAll(new Label("Linha inválida para o livro: " + ex.getMessage()));
            }
        };

        scanLocal.setOnAction(e -> {
            if (activeScan[0] != null) return;
            BoardPosition target = currentPosition[0];
            Task<List<ReferenceMoveStat>> task = new Task<>() {
                @Override protected List<ReferenceMoveStat> call() {
                    updateMessage("Lendo partidas da conta...");
                    return openingTreeService.referenceMoves(repository, target);
                }
            };
            activeScan[0] = task;
            referenceProgress.setVisible(true);
            scanLocal.setDisable(true);
            cancelScan.setDisable(false);
            localReferenceStatus.textProperty().bind(task.messageProperty());
            task.setOnSucceeded(event -> {
                localReferenceStatus.textProperty().unbind();
                List<ReferenceMoveStat> stats = task.getValue();
                localReferenceStatus.setText(stats.isEmpty()
                        ? "A posição não apareceu na base local."
                        : formatReferenceStats(stats));
                referenceProgress.setVisible(false);
                scanLocal.setDisable(false);
                cancelScan.setDisable(true);
                activeScan[0] = null;
            });
            task.setOnCancelled(event -> {
                localReferenceStatus.textProperty().unbind();
                localReferenceStatus.setText("Comparação cancelada.");
                referenceProgress.setVisible(false);
                scanLocal.setDisable(false);
                cancelScan.setDisable(true);
                activeScan[0] = null;
            });
            task.setOnFailed(event -> {
                localReferenceStatus.textProperty().unbind();
                Throwable ex = task.getException();
                localReferenceStatus.setText(ex == null ? "Falha ao comparar." : ex.getMessage());
                referenceProgress.setVisible(false);
                scanLocal.setDisable(false);
                cancelScan.setDisable(true);
                activeScan[0] = null;
            });
            Thread worker = new Thread(task, "evalia-opening-reference");
            worker.setDaemon(true);
            worker.start();
        });
        cancelScan.setOnAction(e -> { if (activeScan[0] != null) activeScan[0].cancel(true); });

        tree.setOnMouseClicked(event -> {
            OpeningTreeRow selected = tree.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) {
                line.add(selected.move());
                refreshTree.run();
            }
        });
        Button playMove = new Button("Entrar no lance");
        playMove.getStyleClass().add("primary-button");
        playMove.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
        playMove.setOnAction(e -> {
            OpeningTreeRow selected = tree.getSelectionModel().getSelectedItem();
            if (selected != null) {
                line.add(selected.move());
                refreshTree.run();
            }
        });
        Button backLine = new Button("Voltar lance");
        backLine.getStyleClass().add("secondary-button");
        backLine.setOnAction(e -> {
            if (!line.isEmpty()) {
                line.remove(line.size() - 1);
                refreshTree.run();
            }
        });
        Button reset = new Button("Início");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(e -> {
            line.clear();
            refreshTree.run();
        });
        HBox treeActions = new HBox(10, playMove, backLine, reset, currentLine);
        treeActions.setAlignment(Pos.CENTER_LEFT);

        List<OpeningStat> stats = openingBook.summarize(accountGames);
        VBox families = new VBox(12);
        for (OpeningStat stat : stats.stream().limit(6).toList()) families.getChildren().add(openingCard(stat));
        if (families.getChildren().isEmpty()) {
            Label empty = new Label("Importe partidas da conta para gerar estatísticas pessoais de abertura.");
            empty.getStyleClass().add("muted");
            families.getChildren().add(empty);
        }
        Label note = new Label("O livro mestre fica dentro do Evalia e funciona sem login ou internet. Ele usa o catálogo CC0 do lichess-org/chess-openings, cujas linhas são construídas a partir de sequências conhecidas e caminhos comuns de partidas de mestres. A indexação por posição também reconhece transposições.");
        note.setWrapText(true);
        note.getStyleClass().add("info-banner");
        HBox referenceActions = new HBox(10, scanLocal, cancelScan, referenceProgress, localReferenceStatus);
        referenceActions.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(note,
                sectionCard("Árvore das suas partidas", new VBox(12, treeActions, tree)),
                sectionCard("Livro mestre offline (CC0)", new VBox(10, referenceBook, referenceActions)),
                sectionCard("Famílias ECO mais jogadas", families));
        refreshTree.run();
    }

    private void showImmortal() {
        refreshSnapshot();
        Optional<Game> featured = historicalRepository.findAll().stream().filter(Game::isFeatured).findFirst();
        if (featured.isPresent()) {
            showGameDetail(featured.get());
            return;
        }
        beginPage("A Imortal do Criador do Programa", "Partida destacada não encontrada no banco local.");
        Label message = new Label("A partida especial fica no arquivo examples/imortal-criador.pgn. Importe esse PGN usando o nome Eduardo Mello da Costa.");
        message.setWrapText(true);
        message.getStyleClass().add("info-banner");
        content.getChildren().add(message);
    }

    private void showInsights() {
        refreshSnapshot();
        beginPage("Central de Diagnóstico", "Perfil, repertório, gráficos e plano de treino em uma visão só.");
        ChoiceBox<String> period = new ChoiceBox<>(FXCollections.observableArrayList(
                "Todas", "Ultimas 10", "Ultimas 30", "Ultimos 90 dias"));
        period.setValue("Todas");
        Button pdf = new Button("Gerar relatório PDF");
        pdf.getStyleClass().add("primary-button");
        VBox diagnosticBody = new VBox(18);
        Runnable render = () -> renderInsightCenter(diagnosticBody, period.getValue());
        period.setOnAction(e -> render.run());
        pdf.setOnAction(e -> exportPdf(period.getValue()));
        HBox filters = new HBox(12, fieldLabel("PERIODO"), period, pdf);
        filters.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(filters, diagnosticBody);
        render.run();
    }

    private void renderInsightCenter(VBox target, String period) {
        target.getChildren().clear();
        List<Game> games = insightCenterService.filterByPeriod(repository.findAll(), period);
        List<PerformanceScore> periodScores = analyticsService.scoresFor(games);
        PlayerDiagnosis diagnosis = insightCenterService.diagnose(games, periodScores);
        List<RepertoireHole> holes = insightCenterService.repertoireHoles(games);
        List<String> plan = insightCenterService.trainingPlan(games);

        HBox profile = new HBox(14,
                statCard("FORÇA PRINCIPAL", diagnosis.mainStrength(), "melhor indicador"),
                statCard("MAIOR FRAQUEZA", diagnosis.mainWeakness(), "prioridade de treino"),
                statCard("MELHOR ABERTURA", diagnosis.bestOpening(), "por aproveitamento"),
                statCard("PIOR ABERTURA", diagnosis.worstOpening(), "buraco provável"));
        profile.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox phase = new HBox(14,
                compactSummary("Fase crítica", diagnosis.criticalPhase(), "summary-loss"),
                compactSummary("Aproveitamento", "%.1f%%".formatted(diagnosis.scoreRate()), "summary-win"),
                compactSummary("Brancas", "%.1f%%".formatted(diagnosis.whiteScoreRate()), "summary-draw"),
                compactSummary("Pretas", "%.1f%%".formatted(diagnosis.blackScoreRate()), "summary-draw"));
        phase.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        double expected = insightCenterService.expectedScoreRate(games);
        double versusExpected = insightCenterService.performanceVsExpected(games);
        double recentTrend = insightCenterService.recentTrend(games);
        int ratingDelta = insightCenterService.ratingChange(games);
        HBox advanced = new HBox(14,
                compactSummary("Esperado pelo rating", expected == 0 ? "n/d" : "%.1f%%".formatted(expected), "summary-draw"),
                compactSummary("Vs. esperado", expected == 0 ? "n/d" : "%+.1f p.p.".formatted(versusExpected), versusExpected >= 0 ? "summary-win" : "summary-loss"),
                compactSummary("Tendência recente", games.size() < 8 ? "amostra pequena" : "%+.1f p.p.".formatted(recentTrend), recentTrend >= 0 ? "summary-win" : "summary-loss"),
                compactSummary("Rating no período", (ratingDelta >= 0 ? "+" : "") + ratingDelta, ratingDelta >= 0 ? "summary-win" : "summary-loss"));
        advanced.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox charts = new HBox(16,
                sectionCard("Resultados por abertura", barChart(insightCenterService.scoreByOpening(games))),
                sectionCard("Desempenho por cor", barChart(insightCenterService.scoreByColor(games))),
                sectionCard("Evolução por data", barChart(insightCenterService.scoreTimeline(games))));
        charts.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox themes = new HBox(16,
                themeSummaryCard("Temas táticos mais comuns", reviewService.countTacticalThemes(games)),
                themeSummaryCard("Temas estratégicos", reviewService.countStrategicThemes(games)),
                themeSummaryCard("Finais", reviewService.countEndgameThemes(games)));
        themes.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox holesBox = new VBox(10);
        if (holes.isEmpty()) {
            Label empty = new Label("Nenhum buraco grave detectado no período. Importe mais partidas para refinar o repertório.");
            empty.setWrapText(true);
            empty.getStyleClass().add("muted");
            holesBox.getChildren().add(empty);
        }
        for (RepertoireHole hole : holes) holesBox.getChildren().add(repertoireHoleCard(hole));

        Label comparison = new Label(lastComparison == null
                ? "Depois de importar partidas, o Evalia mostra aqui o comparativo antes/depois."
                : lastComparison.summary());
        comparison.setWrapText(true);
        comparison.getStyleClass().add("info-banner");

        HBox lower = new HBox(16,
                sectionCard("Buracos de repertório", holesBox),
                sectionCard("Plano de treino automático", bulletList(plan)),
                sectionCard("Antes/depois da importação", comparison));
        lower.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox actionable = sectionCard("Insights acionáveis", bulletList(insightCenterService.actionableInsights(games)));
        target.getChildren().addAll(profile, phase, advanced, actionable, charts, themes, lower);
    }

    private void showTraining() {
        refreshSnapshot();
        beginPage("Modo Treino", "Revise os lances-chave das suas partidas; use ← e → para navegar entre os exercícios.");
        List<Game> games = insightCenterService.filterByPeriod(repository.findAll(), "Ultimas 30");
        List<TrainingExercise> exercises = insightCenterService.exercises(games);
        if (exercises.isEmpty()) {
            Label empty = new Label("Ainda não há exercícios suficientes. Importe partidas com PGN completo para gerar posições de treino.");
            empty.setWrapText(true);
            empty.getStyleClass().add("info-banner");
            content.getChildren().add(empty);
            return;
        }

        int[] index = {0};
        VBox exerciseHost = new VBox();
        Label counter = new Label();
        counter.getStyleClass().add("current-move");
        Button previous = new Button("← Anterior");
        Button next = new Button("Próximo →");
        Button open = new Button("Abrir posição no replay");
        previous.getStyleClass().add("secondary-button");
        next.getStyleClass().add("secondary-button");
        open.getStyleClass().add("primary-button");

        Runnable render = () -> {
            TrainingExercise exercise = exercises.get(index[0]);
            counter.setText("Exercício " + (index[0] + 1) + " de " + exercises.size());
            exerciseHost.getChildren().setAll(trainingExerciseCard(exercise));
            previous.setDisable(index[0] == 0);
            next.setDisable(index[0] == exercises.size() - 1);
        };
        previous.setOnAction(e -> { index[0] = Math.max(0, index[0] - 1); render.run(); });
        next.setOnAction(e -> { index[0] = Math.min(exercises.size() - 1, index[0] + 1); render.run(); });
        open.setOnAction(e -> {
            TrainingExercise exercise = exercises.get(index[0]);
            showGameDetail(exercise.game(), Math.max(0, exercise.plyIndex()));
        });
        content.setFocusTraversable(true);
        content.setOnKeyPressed(event -> {
            if (event.getTarget() instanceof TextInputControl) return;
            if (event.getCode() == KeyCode.LEFT && index[0] > 0) {
                index[0]--; render.run(); event.consume();
            } else if (event.getCode() == KeyCode.RIGHT && index[0] < exercises.size() - 1) {
                index[0]++; render.run(); event.consume();
            }
        });
        HBox controls = new HBox(10, previous, next, open, counter);
        controls.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(sectionCard("Exercício atual", new VBox(12, controls, exerciseHost)),
                sectionCard("Como usar", new Label("As setas esquerda/direita trocam o exercício. Ao abrir o replay, as mesmas setas navegam lance a lance.")));
        render.run();
        content.requestFocus();
    }

    private void showPresentation() {
        refreshSnapshot();
        beginPage("Modo Apresentação", "Roteiro rápido para demonstrar o projeto ao professor.");
        PlayerDiagnosis diagnosis = insightCenterService.diagnose(repository.findAll(), snapshot.scores());
        VBox script = new VBox(12,
                presentationStep("1. Objetivo", "O Evalia importa partidas públicas e transforma histórico real em diagnóstico de treino."),
                presentationStep("2. POO aplicada", "Modelos como Game, GameReview, OpeningStat e serviços separados mostram encapsulamento e responsabilidade única."),
                presentationStep("3. Integrações", "Chess.com, Lichess e PGN manual alimentam a mesma base local."),
                presentationStep("4. Diagnóstico", "Força: " + diagnosis.mainStrength() + ". Fraqueza: " + diagnosis.mainWeakness() + "."),
                presentationStep("5. Demonstração", "Abra a aba Imortal, use o replay, favorite a partida, escreva uma nota e teste o tabuleiro interativo."),
                presentationStep("6. Evolução", "Mostre Insights, Treino, Database, Opening Tree, PDF e exportação/importação da base."));
        content.getChildren().add(sectionCard("Roteiro de fala", script));
    }

    private void showConnections() {
        beginPage("Conectar contas", "Sincronize automaticamente suas partidas públicas do Chess.com ou Lichess.");

        if (accountSwitchMode) {
            Label switchNotice = new Label("Modo troca de conta ativo: a conta anterior foi encerrada e as partidas importadas foram limpas. Conecte Chess.com ou Lichess para começar do zero.");
            switchNotice.setWrapText(true);
            switchNotice.getStyleClass().add("info-banner");
            content.getChildren().add(switchNotice);
        }

        Spinner<Integer> limit = new Spinner<>(10, 500, 100, 10);
        limit.setEditable(true);
        limit.getStyleClass().add("game-limit");
        HBox limitRow = new HBox(10, fieldLabel("PARTIDAS MAIS RECENTES"), limit);
        limitRow.setAlignment(Pos.CENTER_LEFT);
        ProgressIndicator syncProgress = new ProgressIndicator();
        syncProgress.setMaxSize(24, 24);
        syncProgress.setVisible(false);
        Button cancelSync = new Button("Cancelar sincronização");
        cancelSync.getStyleClass().add("secondary-button");
        cancelSync.setDisable(true);
        cancelSync.setOnAction(e -> { if (activeSyncTask != null) activeSyncTask.cancel(true); });
        HBox syncControls = new HBox(10, syncProgress, cancelSync);
        syncControls.setAlignment(Pos.CENTER_LEFT);

        TextField chessUsername = new TextField(accountSettings.username(SourcePlatform.CHESS_COM));
        chessUsername.setPromptText("Seu usuário no Chess.com");
        Label chessFeedback = new Label();
        Button chessButton = new Button("Conectar Chess.com");
        chessButton.getStyleClass().add("primary-button");
        chessButton.setOnAction(e -> syncAccount(new ChessComGameProvider(), chessUsername.getText(),
                limit.getValue(), chessButton, cancelSync, syncProgress, chessFeedback));
        VBox chessCard = accountCard("CHESS.COM", "Importa os arquivos mensais mais recentes da sua conta pública.",
                chessUsername, chessButton, chessFeedback);

        TextField lichessUsername = new TextField(accountSettings.username(SourcePlatform.LICHESS));
        lichessUsername.setPromptText("Seu usuário no Lichess");
        Label lichessFeedback = new Label();
        Button lichessButton = new Button("Conectar Lichess");
        lichessButton.getStyleClass().add("primary-button");
        lichessButton.setOnAction(e -> syncAccount(new LichessGameProvider(), lichessUsername.getText(),
                limit.getValue(), lichessButton, cancelSync, syncProgress, lichessFeedback));
        VBox lichessCard = accountCard("LICHESS", "Baixa suas partidas públicas pela API oficial em formato PGN.",
                lichessUsername, lichessButton, lichessFeedback);

        HBox providers = new HBox(16, chessCard, lichessCard);
        HBox.setHgrow(chessCard, Priority.ALWAYS);
        HBox.setHgrow(lichessCard, Priority.ALWAYS);
        chessCard.setMaxWidth(Double.MAX_VALUE);
        lichessCard.setMaxWidth(Double.MAX_VALUE);

        TextField pgnUsername = new TextField(accountSettings.activeUsername().replace("Jogador demo", ""));
        pgnUsername.setPromptText("Nome do jogador como aparece no PGN");
        TextArea pgn = new TextArea();
        pgn.setPromptText(samplePgn());
        pgn.setPrefRowCount(10);
        pgn.setWrapText(false);
        pgn.getStyleClass().add("pgn-area");
        Label pgnFeedback = new Label();
        pgnFeedback.setWrapText(true);
        ProgressBar importProgress = new ProgressBar(0);
        importProgress.setMaxWidth(Double.MAX_VALUE);
        importProgress.setVisible(false);
        Path[] selectedPgnFile = {null};
        pgn.setOnKeyTyped(e -> selectedPgnFile[0] = null);
        Button chooseFile = new Button("Selecionar .pgn");
        chooseFile.getStyleClass().add("secondary-button");
        chooseFile.setOnAction(e -> {
            Path chosen = choosePgnFilePath();
            if (chosen != null) {
                selectedPgnFile[0] = chosen;
                pgn.clear();
                pgn.setPromptText("Arquivo selecionado: " + chosen.getFileName() + " — será lido em streaming.");
                pgnFeedback.setText("Pronto para importar " + chosen.getFileName() + ".");
                pgnFeedback.getStyleClass().setAll("muted");
            }
        });
        Button importButton = new Button("Importar arquivo");
        importButton.getStyleClass().add("secondary-button");
        Button cancelImport = new Button("Cancelar");
        cancelImport.getStyleClass().add("secondary-button");
        cancelImport.setDisable(true);
        final Task<?>[] activeImport = {null};
        importButton.setOnAction(e -> {
            if (activeImport[0] != null) return;
            Task<BatchImportResult> task = selectedPgnFile[0] == null
                    ? createPgnImportTask(pgnUsername.getText(), pgn.getText())
                    : createPgnFileImportTask(pgnUsername.getText(), selectedPgnFile[0]);
            activeImport[0] = task;
            importButton.setDisable(true);
            chooseFile.setDisable(true);
            cancelImport.setDisable(false);
            importProgress.setVisible(true);
            importProgress.progressProperty().bind(task.progressProperty());
            pgnFeedback.textProperty().bind(task.messageProperty());
            task.setOnSucceeded(event -> {
                importProgress.progressProperty().unbind();
                pgnFeedback.textProperty().unbind();
                BatchImportResult result = task.getValue();
                pgnFeedback.setText(result.added() + " partida(s) nova(s), " + result.duplicates()
                        + " repetida(s), " + result.rejected() + " rejeitada(s).");
                pgnFeedback.getStyleClass().setAll(result.rejected() == 0 ? "feedback-success" : "feedback-loading");
                importProgress.setVisible(false);
                importButton.setDisable(false);
                chooseFile.setDisable(false);
                cancelImport.setDisable(true);
                activeImport[0] = null;
                accountSwitchMode = false;
                refreshSnapshot();
            });
            task.setOnCancelled(event -> {
                importProgress.progressProperty().unbind();
                pgnFeedback.textProperty().unbind();
                pgnFeedback.setText("Importação cancelada; lotes já salvos foram preservados.");
                pgnFeedback.getStyleClass().setAll("feedback-loading");
                importProgress.setVisible(false);
                importButton.setDisable(false);
                chooseFile.setDisable(false);
                cancelImport.setDisable(true);
                activeImport[0] = null;
                refreshSnapshot();
            });
            task.setOnFailed(event -> {
                importProgress.progressProperty().unbind();
                pgnFeedback.textProperty().unbind();
                Throwable ex = task.getException();
                pgnFeedback.setText(ex == null ? "Falha na importação." : ex.getMessage());
                pgnFeedback.getStyleClass().setAll("feedback-error");
                importProgress.setVisible(false);
                importButton.setDisable(false);
                chooseFile.setDisable(false);
                cancelImport.setDisable(true);
                activeImport[0] = null;
            });
            Thread worker = new Thread(task, "evalia-pgn-import");
            worker.setDaemon(true);
            worker.start();
        });
        cancelImport.setOnAction(e -> { if (activeImport[0] != null) activeImport[0].cancel(true); });
        HBox pgnActions = new HBox(10, chooseFile, importButton, cancelImport);
        pgnActions.setAlignment(Pos.CENTER_RIGHT);
        VBox pgnFields = new VBox(10, fieldLabel("USUARIO NO PGN"), pgnUsername,
                fieldLabel("CONTEUDO"), pgn, importProgress, pgnFeedback, pgnActions);

        Label privacy = new Label("Não solicitamos senha. As APIs fornecem somente partidas públicas e os dados ficam salvos localmente.");
        privacy.setWrapText(true);
        privacy.getStyleClass().add("info-banner");
        content.getChildren().addAll(privacy, limitRow, syncControls, providers, sectionCard("Importação manual (alternativa)", pgnFields));
    }

    private void switchAccount() {
        try {
            repository.resetForNewAccount();
            accountSettings.clear();
            accountSwitchMode = true;
            refreshSnapshot();
            showConnections();
        } catch (Exception ex) {
            showError("Não foi possível trocar de conta", ex.getMessage());
        }
    }

    private VBox accountCard(String platform, String description, TextField username,
                             Button action, Label feedback) {
        Label name = new Label(platform);
        name.getStyleClass().add("account-platform");
        Label descriptionNode = new Label(description);
        descriptionNode.setWrapText(true);
        descriptionNode.getStyleClass().add("muted");
        username.getStyleClass().add("input");
        feedback.setWrapText(true);
        VBox card = new VBox(12, name, descriptionNode, username, feedback, action);
        card.getStyleClass().add("account-card");
        return card;
    }

    private void beginPage(String title, String subtitle) {
        content.setOnKeyPressed(null);
        content.getChildren().clear();
        content.getStyleClass().setAll("page");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("page-subtitle");
        VBox heading = new VBox(5, titleLabel, subtitleLabel);
        heading.getStyleClass().add("page-heading");
        content.getChildren().add(heading);
    }

    private VBox statCard(String label, String value, String detail) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("stat-label");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("stat-value");
        valueNode.setWrapText(true);
        Label detailNode = new Label(detail);
        detailNode.getStyleClass().add("muted");
        VBox card = new VBox(7, labelNode, valueNode, detailNode);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(170);
        return card;
    }

    private VBox compactSummary(String label, int value, String cssClass) {
        return compactSummary(label, String.valueOf(value), cssClass);
    }

    private VBox compactSummary(String label, String value, String cssClass) {
        Label number = new Label(String.valueOf(value));
        number.getStyleClass().add("summary-number");
        number.setWrapText(true);
        Label text = new Label(label);
        text.getStyleClass().add("muted");
        VBox box = new VBox(3, number, text);
        box.getStyleClass().addAll("compact-summary", cssClass);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox sectionCard(String title, Node body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        VBox card = new VBox(16, titleLabel, body);
        card.getStyleClass().add("section-card");
        return card;
    }

    private VBox buildMetricList(List<PerformanceScore> scores) {
        VBox list = new VBox(11);
        for (PerformanceScore score : scores) {
            Label name = new Label(score.metric().getLabel());
            name.setPrefWidth(125);
            ProgressBar bar = new ProgressBar(score.value() / 100.0);
            bar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label value = new Label(String.valueOf(score.value()));
            value.getStyleClass().add("metric-value");
            value.setPrefWidth(30);
            HBox row = new HBox(10, name, bar, value);
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        return list;
    }

    private VBox insightCard(AnalysisInsight insight) {
        Label type = new Label(insight.getTypeLabel().toUpperCase());
        type.getStyleClass().add("insight-type");
        Label title = new Label(insight.getTitle() + "  " + insight.getScore());
        title.getStyleClass().add("insight-title");
        Label description = new Label(insight.getDescription());
        description.setWrapText(true);
        description.getStyleClass().add("muted");
        VBox card = new VBox(5, type, title, description);
        card.getStyleClass().addAll("insight-card", insight.getCssClass());
        return card;
    }

    private VBox openingCard(OpeningStat stat) {
        Label name = new Label(stat.family().name());
        name.getStyleClass().add("opening-name");
        Label eco = new Label(stat.family().ecoRange() + "  •  " + stat.games() + " partida(s)  •  "
                + "%.1f%%".formatted(stat.scoreRate()) + " de aproveitamento");
        eco.getStyleClass().add("muted");
        Label score = new Label(stat.wins() + "V / " + stat.draws() + "E / " + stat.losses() + "D");
        score.getStyleClass().add("opening-score");
        Label plan = new Label("Plano: " + stat.family().mainPlan());
        plan.setWrapText(true);
        plan.getStyleClass().add("body-text");
        Label risk = new Label("Cuidado: " + stat.family().typicalRisk());
        risk.setWrapText(true);
        risk.getStyleClass().add("muted");
        HBox heading = new HBox(12, new VBox(3, name, eco), score);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heading.getChildren().get(0), Priority.ALWAYS);
        VBox card = new VBox(9, heading, plan, risk);
        card.getStyleClass().add("opening-card");
        return card;
    }

    private VBox themeSummaryCard(String title, java.util.Map<String, Long> counts) {
        VBox rows = new VBox(8);
        counts.entrySet().stream().limit(5).forEach(entry -> {
            Label theme = new Label(entry.getKey());
            theme.setWrapText(true);
            HBox.setHgrow(theme, Priority.ALWAYS);
            Label count = new Label(entry.getValue() + "x");
            count.getStyleClass().add("theme-count");
            HBox row = new HBox(10, theme, count);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        });
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label("Sem dados suficientes.");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
        }
        VBox card = sectionCard(title, rows);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox barChart(Map<String, Double> values) {
        VBox rows = new VBox(9);
        if (values.isEmpty()) {
            Label empty = new Label("Sem dados suficientes.");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
            return rows;
        }
        values.entrySet().stream().limit(8).forEach(entry -> {
            Label label = new Label(entry.getKey());
            label.setWrapText(true);
            label.setPrefWidth(118);
            ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, entry.getValue() / 100.0)));
            bar.setMaxWidth(Double.MAX_VALUE);
            Label value = new Label("%.1f%%".formatted(entry.getValue()));
            value.getStyleClass().add("metric-value");
            value.setPrefWidth(58);
            HBox row = new HBox(9, label, bar, value);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(bar, Priority.ALWAYS);
            rows.getChildren().add(row);
        });
        return rows;
    }

    private VBox repertoireHoleCard(RepertoireHole hole) {
        Label title = new Label(hole.opening() + "  •  " + "%.1f%%".formatted(hole.scoreRate()));
        title.getStyleClass().add("feedback-theme");
        Label detail = new Label(hole.games() + " partida(s), " + hole.losses() + " derrota(s).");
        detail.getStyleClass().add("feedback-move");
        Label advice = new Label(hole.advice());
        advice.setWrapText(true);
        advice.getStyleClass().add("muted");
        VBox card = new VBox(5, title, detail, advice);
        card.getStyleClass().addAll("feedback-card", "feedback-warning");
        return card;
    }

    private VBox trainingExerciseCard(TrainingExercise exercise) {
        Label prompt = new Label(exercise.prompt());
        prompt.setWrapText(true);
        prompt.getStyleClass().add("feedback-theme");
        Label source = new Label(exercise.game().getWhitePlayer() + " x " + exercise.game().getBlackPlayer()
                + " • " + exercise.game().getDate().format(DATE_FORMAT));
        source.getStyleClass().add("muted");
        Label answer = new Label("Tema: " + exercise.feedback().category() + " / " + exercise.feedback().theme());
        answer.setWrapText(true);
        answer.getStyleClass().add("body-text");
        Button open = new Button("Abrir posição na partida");
        open.getStyleClass().add("secondary-button");
        open.setOnAction(e -> showGameDetail(exercise.game(), Math.max(0, exercise.plyIndex())));
        VBox card = new VBox(8, prompt, source, answer, open);
        card.getStyleClass().add("training-card");
        return card;
    }

    private VBox presentationStep(String title, String text) {
        Label titleNode = new Label(title);
        titleNode.getStyleClass().add("feedback-move");
        Label textNode = new Label(text);
        textNode.setWrapText(true);
        textNode.getStyleClass().add("body-text");
        VBox card = new VBox(5, titleNode, textNode);
        card.getStyleClass().add("presentation-step");
        return card;
    }

    private void showGameDetail(Game game) {
        showGameDetail(game, 0);
    }

    private void showGameDetail(Game game, int initialPly) {
        if (game == null) return;
        String title = game.isFeatured() ? "A Imortal do Criador do Programa" : "Replay da partida";
        beginPage(title, game.getWhitePlayer() + " x " + game.getBlackPlayer()
                + " • " + game.getEvent() + " • " + game.getDate().format(DATE_FORMAT));

        List<String> moves = replayService.moves(game);
        List<BoardPosition> positions;
        try {
            positions = replayService.positions(game);
        } catch (RuntimeException ex) {
            Label invalid = new Label("Não foi possível reconstruir o tabuleiro: " + ex.getMessage());
            invalid.setWrapText(true);
            invalid.getStyleClass().add("feedback-error");
            content.getChildren().add(invalid);
            return;
        }
        GameReview review = reviewService.review(game);
        int[] current = {Math.max(0, Math.min(initialPly, positions.size() - 1))};

        GridPane board = new GridPane();
        configureBoardGrid(board);
        Label moveLabel = new Label();
        moveLabel.getStyleClass().add("current-move");
        ListView<String> moveList = new ListView<>(FXCollections.observableArrayList(moveLabels(moves)));
        moveList.getStyleClass().add("move-list");
        moveList.setPrefHeight(430);

        Runnable render = () -> {
            renderBoard(board, positions.get(current[0]), game.getColor() == PieceColor.BLACK);
            String text = current[0] == 0 ? "Posição inicial" : current[0] + "/" + moves.size()
                    + "  " + positions.get(current[0]).lastMove();
            moveLabel.setText(text);
            if (current[0] > 0) moveList.getSelectionModel().select(current[0] - 1);
            else moveList.getSelectionModel().clearSelection();
        };

        Button first = new Button("|<");
        Button previous = new Button("<");
        Button next = new Button(">");
        Button last = new Button(">|");
        for (Button button : List.of(first, previous, next, last)) button.getStyleClass().add("secondary-button");
        first.setOnAction(e -> { current[0] = 0; render.run(); });
        previous.setOnAction(e -> { current[0] = Math.max(0, current[0] - 1); render.run(); });
        next.setOnAction(e -> { current[0] = Math.min(positions.size() - 1, current[0] + 1); render.run(); });
        last.setOnAction(e -> { current[0] = positions.size() - 1; render.run(); });
        moveList.getSelectionModel().selectedIndexProperty().addListener((obs, old, selected) -> {
            if (selected.intValue() >= 0 && selected.intValue() + 1 != current[0]) {
                current[0] = selected.intValue() + 1;
                render.run();
            }
        });

        content.setFocusTraversable(true);
        content.setOnKeyPressed(event -> {
            if (event.getTarget() instanceof TextInputControl) return;
            if (event.getCode() == KeyCode.LEFT) {
                current[0] = Math.max(0, current[0] - 1); render.run(); event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                current[0] = Math.min(positions.size() - 1, current[0] + 1); render.run(); event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                current[0] = 0; render.run(); event.consume();
            } else if (event.getCode() == KeyCode.END) {
                current[0] = positions.size() - 1; render.run(); event.consume();
            }
        });

        Button back = new Button("Voltar para partidas");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(e -> showGames());

        HBox controls = new HBox(8, first, previous, next, last, moveLabel);
        controls.setAlignment(Pos.CENTER_LEFT);
        StackPane boardFrame = responsiveBoardFrame(board);
        VBox boardBody = new VBox(12, boardFrame, controls);
        boardBody.setAlignment(Pos.CENTER);
        VBox boardPanel = sectionCard("Tabuleiro", boardBody);

        VBox movePanel = sectionCard("Lista de lances", moveList);
        HBox replayGrid = new HBox(16, boardPanel, movePanel);
        HBox.setHgrow(boardPanel, Priority.ALWAYS);
        HBox.setHgrow(movePanel, Priority.ALWAYS);
        boardPanel.setMaxWidth(Double.MAX_VALUE);
        movePanel.setMaxWidth(Double.MAX_VALUE);

        HBox metadata = new HBox(12,
                compactSummary("Resultado", game.getResult().getLabel(), "summary-draw"),
                compactSummary("Abertura", review.openingFamily().name(), "summary-win"),
                compactSummary("ECO", game.getEco().isBlank() ? review.openingFamily().ecoRange() : game.getEco(), "summary-draw"),
                compactSummary("Final", review.endgameType(), "summary-loss"));
        metadata.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox keyMovesHost = new VBox(8);
        Label pendingKeyMoves = new Label("Execute o Game Review para detectar lances-chave pela mudança real de avaliação do Stockfish.");
        pendingKeyMoves.setWrapText(true);
        pendingKeyMoves.getStyleClass().add("muted");
        keyMovesHost.getChildren().add(pendingKeyMoves);
        HBox analysisGrid = new HBox(16,
                sectionCard("Lances-chave", keyMovesHost),
                sectionCard("Temas da partida", reviewThemes(review)),
                sectionCard("Recomendações", bulletList(review.recommendations())));
        analysisGrid.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        CheckBox favorite = new CheckBox("Favoritar partida");
        favorite.setSelected(annotationsService.isFavorite(game));
        favorite.getStyleClass().add("favorite-check");
        favorite.setOnAction(e -> {
            try { annotationsService.setFavorite(game, favorite.isSelected()); }
            catch (Exception ex) { showError("Não foi possível favoritar", ex.getMessage()); }
        });
        TextArea note = new TextArea(annotationsService.note(game));
        note.setPromptText("Escreva suas anotações pessoais: plano, erro crítico, ideia de treino...");
        note.setPrefRowCount(4);
        note.setWrapText(true);
        Button saveNote = new Button("Salvar anotação");
        saveNote.getStyleClass().add("secondary-button");
        saveNote.setOnAction(e -> {
            try {
                annotationsService.saveNote(game, note.getText());
                new Alert(Alert.AlertType.INFORMATION, "Anotação salva.").showAndWait();
            } catch (Exception ex) {
                showError("Não foi possível salvar", ex.getMessage());
            }
        });
        VBox notes = sectionCard("Favoritos e anotações", new VBox(10, favorite, note, saveNote));

        GridPane analysisBoard = new GridPane();
        configureBoardGrid(analysisBoard);
        BoardPosition[] analysisState = {positions.get(current[0])};
        int[] selected = {-1, -1};
        Label analysisStatus = new Label("Análise livre: clique em uma peça e depois no destino.");
        analysisStatus.getStyleClass().add("current-move");
        Runnable renderAnalysis = () -> renderInteractiveBoard(analysisBoard, analysisState,
                game.getColor() == PieceColor.BLACK, selected, analysisStatus);
        Button loadCurrent = new Button("Carregar posição atual");
        loadCurrent.getStyleClass().add("secondary-button");
        loadCurrent.setOnAction(e -> {
            analysisState[0] = positions.get(current[0]);
            selected[0] = -1;
            selected[1] = -1;
            analysisStatus.setText("Posição carregada. Vez das "
                    + (analysisState[0].whiteToMove() ? "brancas" : "pretas") + ".");
            renderAnalysis.run();
        });
        VBox analysisBoardBody = new VBox(12, responsiveBoardFrame(analysisBoard), new HBox(10, loadCurrent, analysisStatus));
        analysisBoardBody.setAlignment(Pos.CENTER);
        VBox freeAnalysis = sectionCard("Tabuleiro interativo — regras completas", analysisBoardBody);

        VBox fullReview = buildFullGameReviewPanel(game, moveList, keyMovesHost);
        VBox engine = buildEnginePanel(() -> positions.get(current[0]));

        content.getChildren().addAll(back, metadata, replayGrid, fullReview, analysisGrid, engine, notes, freeAnalysis);
        render.run();
        renderAnalysis.run();
        content.requestFocus();
    }

    private VBox buildFullGameReviewPanel(Game game, ListView<String> moveList, VBox keyMovesHost) {
        Label status = new Label("A revisão completa usa a variação da avaliação do Stockfish para classificar cada lance e detectar viradas reais.");
        status.setWrapText(true);
        status.getStyleClass().add("muted");
        Spinner<Integer> depth = new Spinner<>(9, 18, 11, 1);
        depth.setEditable(true);
        Button analyze = new Button("Analisar partida completa");
        analyze.getStyleClass().add("primary-button");
        Button cancel = new Button("Cancelar");
        cancel.getStyleClass().add("secondary-button");
        cancel.setDisable(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(24, 24);
        progress.setVisible(false);
        final Task<?>[] active = {null};

        java.util.function.Consumer<EngineGameReview> applyReview = result -> {
            moveList.setItems(FXCollections.observableArrayList(result.moves().stream().map(MoveAssessment::label).toList()));
            populateFeedbackList(keyMovesHost, result.keyMoves());
            status.setText("Revisão pronta • ACPL brancas "
                    + String.format(java.util.Locale.ROOT, "%.0f", result.whiteAverageCentipawnLoss())
                    + " • ACPL pretas "
                    + String.format(java.util.Locale.ROOT, "%.0f", result.blackAverageCentipawnLoss())
                    + ". Lances-chave agora são baseados em mudança real de avaliação e ideias únicas vencedoras.");
        };

        EngineGameReview cached = engineReviewCache.get(game.getId());
        if (cached != null) applyReview.accept(cached);

        analyze.setOnAction(e -> {
            if (active[0] != null) return;
            Task<EngineGameReview> task = new Task<>() {
                @Override protected EngineGameReview call() throws Exception {
                    if (engineSettings.executable().isBlank()) {
                        new StockfishProvisioner(engineSettings).ensureInstalled(this::isCancelled, this::updateMessage);
                        closeStockfish();
                    }
                    return engineGameReviewService.review(game, stockfish(), depth.getValue(),
                            this::isCancelled, this::updateMessage);
                }
            };
            active[0] = task;
            analyze.setDisable(true);
            depth.setDisable(true);
            cancel.setDisable(false);
            progress.setVisible(true);
            status.textProperty().bind(task.messageProperty());
            task.setOnSucceeded(event -> {
                status.textProperty().unbind();
                EngineGameReview result = task.getValue();
                engineReviewCache.put(game.getId(), result);
                applyReview.accept(result);
                analyze.setDisable(false);
                depth.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            task.setOnCancelled(event -> {
                status.textProperty().unbind();
                status.setText("Revisão completa cancelada.");
                analyze.setDisable(false);
                depth.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            task.setOnFailed(event -> {
                status.textProperty().unbind();
                Throwable ex = task.getException();
                status.setText(ex == null ? "Falha na revisão completa." : "Falha: " + ex.getMessage());
                analyze.setDisable(false);
                depth.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            Thread worker = new Thread(task, "evalia-full-game-review");
            worker.setDaemon(true);
            worker.start();
        });
        cancel.setOnAction(e -> { if (active[0] != null) active[0].cancel(true); });

        FlowPane legend = new FlowPane(8, 8);
        for (MoveClassification classification : MoveClassification.values()) {
            String annotation = classification.annotation();
            String legendText = annotation == null || annotation.isBlank()
                    ? classification.label() + " (sem símbolo)"
                    : annotation + " " + classification.label();
            Label chip = new Label(legendText);
            chip.getStyleClass().addAll("classification-chip", classification.cssClass());
            legend.getChildren().add(chip);
        }
        HBox actions = new HBox(10, fieldLabel("DEPTH"), depth, analyze, cancel, progress);
        actions.setAlignment(Pos.CENTER_LEFT);
        return sectionCard("Game Review — classificação de lances", new VBox(10, status, actions, legend));
    }

    private void populateFeedbackList(VBox host, List<MoveFeedback> feedback) {
        host.getChildren().clear();
        for (MoveFeedback item : feedback) host.getChildren().add(feedbackCard(item));
    }

    private List<String> moveLabels(List<String> moves) {
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            String prefix = i % 2 == 0 ? (i / 2 + 1) + "." : (i / 2 + 1) + "...";
            labels.add(prefix + " " + moves.get(i));
        }
        return labels;
    }

    private void configureBoardGrid(GridPane board) {
        board.getStyleClass().add("chess-board");
        board.getColumnConstraints().clear();
        board.getRowConstraints().clear();
        for (int i = 0; i < 8; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(12.5);
            column.setHgrow(Priority.ALWAYS);
            board.getColumnConstraints().add(column);
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(12.5);
            row.setVgrow(Priority.ALWAYS);
            board.getRowConstraints().add(row);
        }
        board.setMinSize(336, 336);
        board.setPrefSize(512, 512);
        board.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private StackPane responsiveBoardFrame(GridPane board) {
        StackPane frame = new StackPane(board);
        frame.getStyleClass().add("board-frame");
        frame.setMinHeight(360);
        frame.setPrefHeight(520);
        frame.setMaxWidth(Double.MAX_VALUE);
        var size = Bindings.max(336.0, Bindings.min(frame.widthProperty().subtract(8), frame.heightProperty().subtract(8)));
        board.prefWidthProperty().bind(size);
        board.prefHeightProperty().bind(size);
        board.maxWidthProperty().bind(size);
        board.maxHeightProperty().bind(size);
        return frame;
    }

    private void renderBoard(GridPane board, BoardPosition position, boolean flipped) {
        board.getChildren().clear();
        Move lastMove = position.move();
        for (int displayRow = 0; displayRow < 8; displayRow++) {
            for (int displayColumn = 0; displayColumn < 8; displayColumn++) {
                int boardRow = flipped ? 7 - displayRow : displayRow;
                int boardColumn = flipped ? 7 - displayColumn : displayColumn;
                StackPane square = squareNode(position.pieceAt(boardRow, boardColumn), boardRow, boardColumn);
                if (lastMove != null && isMoveSquare(lastMove, boardRow, boardColumn)) {
                    square.getStyleClass().add("square-last-move");
                }
                board.add(square, displayColumn, displayRow);
            }
        }
    }

    private void renderInteractiveBoard(GridPane board, BoardPosition[] state, boolean flipped, int[] selected,
                                        Label status) {
        board.getChildren().clear();
        BoardPosition position = state[0];
        for (int displayRow = 0; displayRow < 8; displayRow++) {
            for (int displayColumn = 0; displayColumn < 8; displayColumn++) {
                int boardRow = flipped ? 7 - displayRow : displayRow;
                int boardColumn = flipped ? 7 - displayColumn : displayColumn;
                char pieceValue = position.pieceAt(boardRow, boardColumn);
                StackPane square = squareNode(pieceValue, boardRow, boardColumn);
                if (selected[0] == boardRow && selected[1] == boardColumn) square.getStyleClass().add("square-selected");

                square.setOnMouseClicked(event -> {
                    BoardPosition now = state[0];
                    char clickedPiece = now.pieceAt(boardRow, boardColumn);
                    if (selected[0] < 0) {
                        if (belongsToMoveSide(clickedPiece, now.whiteToMove())) {
                            selected[0] = boardRow;
                            selected[1] = boardColumn;
                            status.setText("Peça selecionada. Escolha o destino.");
                        }
                    } else if (belongsToMoveSide(clickedPiece, now.whiteToMove())) {
                        selected[0] = boardRow;
                        selected[1] = boardColumn;
                        status.setText("Nova peça selecionada.");
                    } else {
                        try {
                            Move move = coordinateMove(now, selected[0], selected[1], boardRow, boardColumn);
                            state[0] = chessRules.play(now, move);
                            status.setText("Lance " + state[0].lastMove() + " aceito. Vez das "
                                    + (state[0].whiteToMove() ? "brancas" : "pretas") + ".");
                        } catch (IllegalArgumentException ex) {
                            status.setText("Lance ilegal.");
                        }
                        selected[0] = -1;
                        selected[1] = -1;
                    }
                    renderInteractiveBoard(board, state, flipped, selected, status);
                    event.consume();
                });
                square.setOnDragDetected(event -> {
                    BoardPosition now = state[0];
                    if (belongsToMoveSide(now.pieceAt(boardRow, boardColumn), now.whiteToMove())) {
                        selected[0] = boardRow;
                        selected[1] = boardColumn;
                        status.setText("Arraste até a casa de destino.");
                        square.startFullDrag();
                    }
                    event.consume();
                });
                square.setOnMouseDragReleased(event -> {
                    if (selected[0] >= 0 && (selected[0] != boardRow || selected[1] != boardColumn)) {
                        try {
                            BoardPosition now = state[0];
                            Move move = coordinateMove(now, selected[0], selected[1], boardRow, boardColumn);
                            state[0] = chessRules.play(now, move);
                            status.setText("Lance " + state[0].lastMove() + " aceito. Vez das "
                                    + (state[0].whiteToMove() ? "brancas" : "pretas") + ".");
                        } catch (IllegalArgumentException ex) {
                            status.setText("Lance ilegal.");
                        }
                        selected[0] = -1;
                        selected[1] = -1;
                        renderInteractiveBoard(board, state, flipped, selected, status);
                    }
                    event.consume();
                });
                board.add(square, displayColumn, displayRow);
            }
        }
    }

    private StackPane squareNode(char pieceValue, int boardRow, int boardColumn) {
        StackPane square = new StackPane();
        square.getStyleClass().add((boardRow + boardColumn) % 2 == 0 ? "square-light" : "square-dark");
        square.setMinSize(0, 0);
        square.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Node piece = pieceNode(pieceValue, square);
        if (piece != null) square.getChildren().add(piece);
        return square;
    }

    private Node pieceNode(char piece, StackPane square) {
        if (piece == ' ') return null;
        String name = (Character.isUpperCase(piece) ? "w" : "b") + Character.toUpperCase(piece) + ".png";
        Image image = pieceImages.computeIfAbsent(piece, key -> {
            var resource = getClass().getResource("/pieces/chessnut/png/" + name);
            return resource == null ? null : new Image(resource.toExternalForm(), true);
        });
        if (image == null) {
            Label fallback = new Label(pieceSymbol(piece));
            fallback.getStyleClass().add("piece-fallback");
            return fallback;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setMouseTransparent(true);
        view.fitWidthProperty().bind(square.widthProperty().multiply(0.84));
        view.fitHeightProperty().bind(square.heightProperty().multiply(0.84));
        return view;
    }

    private boolean belongsToMoveSide(char piece, boolean whiteToMove) {
        if (piece == ' ') return false;
        return Character.isUpperCase(piece) == whiteToMove;
    }

    private Move coordinateMove(BoardPosition position, int fromRow, int fromColumn, int toRow, int toColumn) {
        char piece = position.pieceAt(fromRow, fromColumn);
        String uci = squareName(fromRow, fromColumn) + squareName(toRow, toColumn);
        if (Character.toLowerCase(piece) == 'p' && (toRow == 0 || toRow == 7)) uci += "q";
        return new Move(uci);
    }

    private String squareName(int row, int column) {
        return String.valueOf((char) ('a' + column)) + (8 - row);
    }

    private boolean isMoveSquare(Move move, int row, int column) {
        String square = squareName(row, column);
        return move.from().equals(square) || move.to().equals(square);
    }

    private String pieceSymbol(char piece) {
        return switch (piece) {
            case 'K' -> "♔"; case 'Q' -> "♕"; case 'R' -> "♖"; case 'B' -> "♗";
            case 'N' -> "♘"; case 'P' -> "♙"; case 'k' -> "♚"; case 'q' -> "♛";
            case 'r' -> "♜"; case 'b' -> "♝"; case 'n' -> "♞"; case 'p' -> "♟";
            default -> "";
        };
    }

    private VBox buildEnginePanel(java.util.function.Supplier<BoardPosition> positionSupplier) {
        Label configured = new Label(engineSettings.executable().isBlank()
                ? "Stockfish será instalado automaticamente no primeiro uso." : "Engine: " + engineSettings.executable());
        configured.setWrapText(true);
        configured.getStyleClass().add("muted");
        Spinner<Integer> depth = new Spinner<>(8, 30, 16, 1);
        depth.setEditable(true);
        Button choose = new Button("Selecionar manualmente");
        Button analyze = new Button("Analisar posição");
        Button cancel = new Button("Cancelar");
        choose.getStyleClass().add("secondary-button");
        analyze.getStyleClass().add("primary-button");
        cancel.getStyleClass().add("secondary-button");
        cancel.setDisable(true);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(24, 24);
        progress.setVisible(false);
        Label result = new Label("Selecione uma posição no replay e clique em analisar.");
        result.setWrapText(true);
        result.getStyleClass().add("body-text");
        final Task<?>[] active = {null};

        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Selecionar executável do Stockfish");
            File file = chooser.showOpenDialog(shell.getScene().getWindow());
            if (file == null) return;
            try {
                engineSettings.save(file.toPath());
                closeStockfish();
                configured.setText("Engine: " + engineSettings.executable());
                result.setText("Stockfish configurado. A análise roda em segundo plano.");
            } catch (Exception ex) {
                showError("Não foi possível salvar a engine", ex.getMessage());
            }
        });

        analyze.setOnAction(e -> {
            if (active[0] != null) return;
            BoardPosition target = positionSupplier.get();
            Task<EngineAnalysis> task = new Task<>() {
                @Override protected EngineAnalysis call() throws Exception {
                    if (engineSettings.executable().isBlank()) {
                        new StockfishProvisioner(engineSettings).ensureInstalled(this::isCancelled, this::updateMessage);
                        closeStockfish();
                    }
                    updateMessage("Analisando " + target.lastMove() + "...");
                    return stockfish().analyze(target.fen(), depth.getValue(), Duration.ofSeconds(45), this::isCancelled);
                }
            };
            active[0] = task;
            analyze.setDisable(true);
            choose.setDisable(true);
            cancel.setDisable(false);
            progress.setVisible(true);
            result.textProperty().bind(task.messageProperty());
            task.setOnSucceeded(event -> {
                result.textProperty().unbind();
                configured.setText("Engine: " + engineSettings.executable());
                result.setText(formatEngineAnalysis(task.getValue()));
                analyze.setDisable(false);
                choose.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            task.setOnCancelled(event -> {
                result.textProperty().unbind();
                result.setText("Análise cancelada.");
                analyze.setDisable(false);
                choose.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            task.setOnFailed(event -> {
                result.textProperty().unbind();
                Throwable ex = task.getException();
                result.setText(ex == null ? "Falha na análise." : "Falha: " + ex.getMessage());
                analyze.setDisable(false);
                choose.setDisable(false);
                cancel.setDisable(true);
                progress.setVisible(false);
                active[0] = null;
            });
            Thread worker = new Thread(task, "evalia-stockfish");
            worker.setDaemon(true);
            worker.start();
        });
        cancel.setOnAction(e -> { if (active[0] != null) active[0].cancel(true); });
        HBox actions = new HBox(10, fieldLabel("DEPTH"), depth, choose, analyze, cancel, progress);
        actions.setAlignment(Pos.CENTER_LEFT);
        return sectionCard("Stockfish — análise UCI em background", new VBox(10, configured, actions, result));
    }

    private synchronized StockfishService stockfish() {
        if (stockfish == null) stockfish = new StockfishService(Path.of(engineSettings.executable()));
        return stockfish;
    }

    private synchronized void closeStockfish() {
        if (stockfish != null) {
            stockfish.close();
            stockfish = null;
        }
    }

    private String formatEngineAnalysis(EngineAnalysis analysis) {
        String score = analysis.mateIn() != null ? "mate " + analysis.mateIn()
                : analysis.centipawns() == null ? "sem score"
                : String.format(java.util.Locale.ROOT, "%+.2f", analysis.centipawns() / 100.0);
        String best = analysis.bestMove().map(Move::uci).orElse("--");
        String pv = analysis.principalVariation().stream().limit(10).map(Move::uci)
                .reduce((a, b) -> a + " " + b).orElse("--");
        return "Depth " + analysis.depth() + " • avaliação " + score + " • melhor lance " + best + "\nPV: " + pv;
    }

    private VBox feedbackList(List<MoveFeedback> feedback) {
        VBox list = new VBox(10);
        for (MoveFeedback item : feedback) list.getChildren().add(feedbackCard(item));
        return list;
    }

    private VBox feedbackCard(MoveFeedback item) {
        Label move = new Label(item.label() + "  •  " + item.category());
        move.getStyleClass().add("feedback-move");
        Label theme = new Label(item.theme());
        theme.getStyleClass().add("feedback-theme");
        Label description = new Label(item.description());
        description.setWrapText(true);
        description.getStyleClass().add("muted");
        VBox card = new VBox(4, move, theme, description);
        card.getStyleClass().addAll("feedback-card", item.cssClass());
        return card;
    }

    private VBox reviewThemes(GameReview review) {
        VBox list = new VBox(11,
                chipGroup("Tática", review.tacticalThemes()),
                chipGroup("Estratégia", review.strategicThemes()),
                chipGroup("Finais", review.endgameThemes()));
        return list;
    }

    private VBox chipGroup(String title, List<String> values) {
        Label label = fieldLabel(title.toUpperCase());
        FlowPane chips = new FlowPane(8, 8);
        for (String value : values) {
            Label chip = new Label(value);
            chip.getStyleClass().add("theme-chip");
            chip.setWrapText(true);
            chips.getChildren().add(chip);
        }
        return new VBox(6, label, chips);
    }

    private VBox bulletList(List<String> values) {
        VBox list = new VBox(8);
        for (String value : values) {
            Label item = new Label("• " + value);
            item.setWrapText(true);
            item.getStyleClass().add("body-text");
            list.getChildren().add(item);
        }
        return list;
    }

    private TableView<OpeningTreeRow> buildOpeningTreeTable() {
        TableView<OpeningTreeRow> table = new TableView<>();
        table.setPrefHeight(360);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhum próximo lance encontrado para essa linha."));

        TableColumn<OpeningTreeRow, String> move = new TableColumn<>("Lance");
        move.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().move()));
        TableColumn<OpeningTreeRow, String> games = new TableColumn<>("Partidas");
        games.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(String.valueOf(data.getValue().games())));
        TableColumn<OpeningTreeRow, String> score = new TableColumn<>("Aproveitamento");
        score.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty("%.1f%%".formatted(data.getValue().scoreRate())));
        TableColumn<OpeningTreeRow, String> split = new TableColumn<>("W/D/L");
        split.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().wins()
                + "/" + data.getValue().draws() + "/" + data.getValue().losses()));
        TableColumn<OpeningTreeRow, String> line = new TableColumn<>("Linha");
        line.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().line()));
        line.setPrefWidth(330);
        table.getColumns().addAll(List.of(move, games, score, split, line));
        return table;
    }

    private TableView<Game> buildGameTable(List<Game> games, double height) {
        TableView<Game> table = new TableView<>(FXCollections.observableArrayList(games));
        table.setPrefHeight(height);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhuma partida encontrada."));

        TableColumn<Game, String> date = column("Data", g -> g.getDate().format(DATE_FORMAT));
        TableColumn<Game, String> event = column("Evento", Game::getEvent);
        TableColumn<Game, String> white = column("Brancas", Game::getWhitePlayer);
        TableColumn<Game, String> black = column("Pretas", Game::getBlackPlayer);
        TableColumn<Game, String> opponent = column("Adversário", Game::getOpponent);
        TableColumn<Game, String> playerRating = column("Seu rating", g -> ratingValue(g.getPlayerRating()));
        TableColumn<Game, String> opponentRating = column("Rating adv.", g -> ratingValue(g.getOpponentRating()));
        TableColumn<Game, String> color = column("Cor", g -> g.getColor().getLabel());
        TableColumn<Game, String> result = column("Resultado", g -> g.getResult().getLabel());
        TableColumn<Game, String> opening = column("Abertura", Game::getOpening);
        TableColumn<Game, String> eco = column("ECO", g -> g.getEco().isBlank() ? "--" : g.getEco());
        TableColumn<Game, String> moves = column("Lances", g -> String.valueOf(g.getMoves()));
        TableColumn<Game, String> source = column("Origem", g -> g.getSource().getLabel());
        opening.setPrefWidth(190);
        event.setPrefWidth(190);
        table.getColumns().addAll(List.of(date, event, white, black, opponent, playerRating, opponentRating,
                color, result, opening, eco, moves, source));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Game game, boolean empty) {
                super.updateItem(game, empty);
                getStyleClass().removeAll("row-win", "row-draw", "row-loss");
                if (!empty && game != null) getStyleClass().add(switch (game.getResult()) {
                    case WIN -> "row-win";
                    case DRAW -> "row-draw";
                    case LOSS -> "row-loss";
                });
            }
            {
                setOnMouseClicked(event -> {
                    if (!isEmpty() && event.getClickCount() == 2) showGameDetail(getItem());
                });
            }
        });
        return table;
    }

    private TableColumn<Game, String> column(String title, java.util.function.Function<Game, String> mapper) {
        TableColumn<Game, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(mapper.apply(data.getValue())));
        return column;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Task<BatchImportResult> createPgnImportTask(String username, String pgn) {
        List<Game> before = repository.findAll();
        return new Task<>() {
            @Override protected BatchImportResult call() throws Exception {
                if (username == null || username.isBlank()) throw new IllegalArgumentException("Informe o nome do jogador no PGN.");
                if (pgn == null || pgn.isBlank()) throw new IllegalArgumentException("Cole ou selecione um PGN para importar.");
                long read = 0, added = 0, duplicates = 0, rejected = 0;
                List<String> warnings = new ArrayList<>();
                PgnParser parser = new PgnParser();
                try (PgnStreamReader reader = new PgnStreamReader(new StringReader(pgn))) {
                    String gameText;
                    while ((gameText = reader.nextGame()) != null) {
                        if (isCancelled()) return new BatchImportResult(read, added, duplicates, rejected, true, warnings);
                        try {
                            List<Game> parsed = parser.parse(gameText, username.trim());
                            read += parsed.size();
                            int saved = repository.saveAll(parsed);
                            added += saved;
                            duplicates += parsed.size() - saved;
                        } catch (RuntimeException ex) {
                            rejected++;
                            if (warnings.size() < 8) warnings.add(ex.getMessage() == null ? "PGN inválido." : ex.getMessage());
                        }
                        updateProgress(Math.min(reader.charactersRead(), pgn.length()), Math.max(1, pgn.length()));
                        updateMessage("Importando: " + read + " lida(s), " + added + " nova(s), " + duplicates + " repetida(s)...");
                    }
                } catch (InterruptedIOException ex) {
                    if (!isCancelled()) throw ex;
                    return new BatchImportResult(read, added, duplicates, rejected, true, warnings);
                }
                updateProgress(1, 1);
                lastComparison = insightCenterService.compare(before, repository.findAll());
                return new BatchImportResult(read, added, duplicates, rejected, false, warnings);
            }
        };
    }

    private Task<BatchImportResult> createPgnFileImportTask(String username, Path file) {
        List<Game> before = repository.findAll();
        return new Task<>() {
            @Override protected BatchImportResult call() throws Exception {
                if (username == null || username.isBlank()) throw new IllegalArgumentException("Informe o nome do jogador no PGN.");
                if (file == null || !Files.isRegularFile(file)) throw new IllegalArgumentException("Arquivo PGN não encontrado.");
                long total = Math.max(1, Files.size(file));
                long read = 0, added = 0, duplicates = 0, rejected = 0;
                List<String> warnings = new ArrayList<>();
                PgnParser parser = new PgnParser();
                try (PgnStreamReader reader = new PgnStreamReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
                    String gameText;
                    while ((gameText = reader.nextGame()) != null) {
                        if (isCancelled()) return new BatchImportResult(read, added, duplicates, rejected, true, warnings);
                        try {
                            List<Game> parsed = parser.parse(gameText, username.trim());
                            read += parsed.size();
                            int saved = repository.saveAll(parsed);
                            added += saved;
                            duplicates += parsed.size() - saved;
                        } catch (RuntimeException ex) {
                            rejected++;
                            if (warnings.size() < 8) warnings.add(ex.getMessage() == null ? "PGN inválido." : ex.getMessage());
                        }
                        updateProgress(Math.min(reader.charactersRead(), total), total);
                        updateMessage("Streaming: " + read + " lida(s), " + added + " nova(s), "
                                + duplicates + " repetida(s), " + rejected + " rejeitada(s)...");
                    }
                } catch (InterruptedIOException ex) {
                    if (!isCancelled()) throw ex;
                    return new BatchImportResult(read, added, duplicates, rejected, true, warnings);
                }
                updateProgress(1, 1);
                lastComparison = insightCenterService.compare(before, repository.findAll());
                return new BatchImportResult(read, added, duplicates, rejected, false, warnings);
            }
        };
    }

    private void syncAccount(GameProvider provider, String username, int limit,
                             Button action, Button cancel, ProgressIndicator progress, Label feedback) {
        if (syncing) {
            feedback.setText("Aguarde a sincronização atual terminar.");
            feedback.getStyleClass().setAll("feedback-error");
            return;
        }
        syncing = true;
        action.setDisable(true);
        cancel.setDisable(false);
        progress.setVisible(true);
        feedback.setText("Buscando partidas públicas...");
        feedback.getStyleClass().setAll("feedback-loading");
        List<Game> before = repository.findAll();

        Task<SyncOutcome> task = new Task<>() {
            @Override protected SyncOutcome call() throws Exception {
                List<Game> downloaded = provider.fetchGames(username, limit);
                int added = downloaded.isEmpty() ? 0 : repository.saveAll(downloaded);
                accountSettings.saveUsername(provider.platform(), username);
                return new SyncOutcome(provider.platform(), username.trim(), downloaded.size(), added);
            }
        };
        activeSyncTask = task;
        task.setOnSucceeded(event -> {
            syncing = false;
            activeSyncTask = null;
            action.setDisable(false);
            cancel.setDisable(true);
            progress.setVisible(false);
            SyncOutcome result = task.getValue();
            int duplicates = result.downloaded() - result.added();
            feedback.setText("Conta conectada: " + result.downloaded() + " baixada(s), "
                    + result.added() + " nova(s), " + duplicates + " repetida(s).");
            feedback.getStyleClass().setAll("feedback-success");
            accountSwitchMode = false;
            refreshSnapshot();
            lastComparison = insightCenterService.compare(before, repository.findAll());
        });
        task.setOnCancelled(event -> {
            syncing = false;
            activeSyncTask = null;
            action.setDisable(false);
            cancel.setDisable(true);
            progress.setVisible(false);
            feedback.setText("Sincronização cancelada.");
            feedback.getStyleClass().setAll("feedback-loading");
        });
        task.setOnFailed(event -> {
            syncing = false;
            activeSyncTask = null;
            action.setDisable(false);
            cancel.setDisable(true);
            progress.setVisible(false);
            Throwable error = task.getException();
            feedback.setText(error == null || error.getMessage() == null
                    ? "Não foi possível sincronizar a conta." : error.getMessage());
            feedback.getStyleClass().setAll("feedback-error");
        });
        Thread worker = new Thread(task, "evalia-sync-" + provider.platform().name().toLowerCase());
        worker.setDaemon(true);
        worker.start();
    }

    private Path choosePgnFilePath() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar arquivo PGN");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Partidas PGN", "*.pgn", "*.txt"));
        File file = chooser.showOpenDialog(shell.getScene().getWindow());
        return file == null ? null : file.toPath();
    }

    private void exportDashboard(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar dados do dashboard");
        chooser.setInitialFileName("evalia-export.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            new DashboardExportService().export(snapshot, file.toPath());
            new Alert(Alert.AlertType.INFORMATION, "Dashboard exportado com sucesso.").showAndWait();
        } catch (Exception ex) { showError("Falha ao exportar", ex.getMessage()); }
    }

    private void exportPdf(String period) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Gerar relatório Evalia");
        chooser.setInitialFileName("evalia-relatorio.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(shell.getScene().getWindow());
        if (file == null) return;
        try {
            List<Game> games = insightCenterService.filterByPeriod(repository.findAll(), period);
            PlayerDiagnosis diagnosis = insightCenterService.diagnose(games, analyticsService.scoresFor(games));
            reportPdfService.export(file.toPath(), diagnosis,
                    insightCenterService.repertoireHoles(games), insightCenterService.trainingPlan(games));
            new Alert(Alert.AlertType.INFORMATION, "Relatório PDF gerado com sucesso.").showAndWait();
        } catch (Exception ex) {
            showError("Falha ao gerar PDF", ex.getMessage());
        }
    }

    private void exportBase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar base Evalia");
        chooser.setInitialFileName("evalia-base.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base Evalia CSV", "*.csv"));
        File file = chooser.showSaveDialog(shell.getScene().getWindow());
        if (file == null) return;
        try {
            historicalRepository.exportTo(file.toPath());
            new Alert(Alert.AlertType.INFORMATION, "Base histórica exportada com sucesso.").showAndWait();
        } catch (Exception ex) {
            showError("Falha ao exportar base", ex.getMessage());
        }
    }

    private void importBase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar base histórica Evalia");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base Evalia CSV", "*.csv"));
        File file = chooser.showOpenDialog(shell.getScene().getWindow());
        if (file == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Importando base histórica");
        dialog.setHeaderText("A importação roda em segundo plano e pode ser cancelada.");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelType);
        ProgressIndicator progress = new ProgressIndicator();
        Label status = new Label("Lendo registros...");
        status.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(12, progress, status));

        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception {
                updateMessage("Importando CSV em lotes...");
                return historicalRepository.importFrom(file.toPath());
            }
        };
        status.textProperty().bind(task.messageProperty());
        Node cancelButton = dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            task.cancel(true);
            event.consume();
        });
        task.setOnSucceeded(event -> {
            status.textProperty().unbind();
            dialog.close();
            new Alert(Alert.AlertType.INFORMATION, task.getValue() + " partida(s) histórica(s) nova(s) importada(s).").showAndWait();
            showDatabase();
        });
        task.setOnCancelled(event -> {
            status.textProperty().unbind();
            dialog.close();
            new Alert(Alert.AlertType.INFORMATION, "Importação cancelada; lotes concluídos foram preservados.").showAndWait();
            showDatabase();
        });
        task.setOnFailed(event -> {
            status.textProperty().unbind();
            dialog.close();
            Throwable ex = task.getException();
            showError("Falha ao importar base histórica", ex == null ? null : ex.getMessage());
        });
        Thread worker = new Thread(task, "evalia-historical-import");
        worker.setDaemon(true);
        worker.start();
        dialog.show();
    }

    private List<Game> allDatabaseGames() {
        return mergeDatabaseGames(repository.findAll(), historicalRepository.findAll());
    }

    private List<Game> mergeDatabaseGames(List<Game> accountGames, List<Game> historicalGames) {
        LinkedHashMap<String, Game> unique = new LinkedHashMap<>();
        for (Game game : accountGames) unique.putIfAbsent(game.fingerprint(), game);
        for (Game game : historicalGames) unique.putIfAbsent(game.fingerprint(), game);
        return List.copyOf(unique.values());
    }

    private void migrateLegacyDatabase(Path accountFile, Path historicalFile) throws Exception {
        Path legacy = Path.of("data", "games.csv");
        if (!Files.exists(legacy) || Files.exists(accountFile) || Files.exists(historicalFile)) return;
        CsvGameRepository old = new CsvGameRepository(legacy, false);
        List<Game> legacyGames = old.findAll();
        List<Game> accountGames = legacyGames.stream().filter(game -> !game.isFeatured()).toList();
        List<Game> historicalGames = legacyGames.stream().filter(Game::isFeatured).toList();
        if (!accountGames.isEmpty()) repository.saveAll(accountGames);
        if (!historicalGames.isEmpty()) historicalRepository.saveAll(historicalGames);
        Path backup = Path.of("data", "games.legacy.csv");
        Files.move(legacy, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void seedHistoricalDatabase() throws Exception {
        if (historicalRepository.findAll().stream().anyMatch(Game::isFeatured)) return;
        try (InputStream stream = getClass().getResourceAsStream("/pgn/imortal-criador.pgn")) {
            if (stream == null) return;
            String pgn = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Game parsed = new PgnParser().parse(pgn, "Eduardo Mello da Costa").get(0);
            Game featured = new Game(java.util.UUID.randomUUID(), parsed.getDate(), parsed.getOpponent(),
                    parsed.getPlayerRating(), parsed.getOpponentRating(), parsed.getColor(), parsed.getResult(),
                    parsed.getOpening(), parsed.getMoves(), SourcePlatform.PGN, parsed.getEco(), parsed.getPgn(),
                    "A Imortal do Criador do Programa", parsed.getSite(), parsed.getWhitePlayer(),
                    parsed.getBlackPlayer(), parsed.getTimeControl(), true);
            historicalRepository.saveAll(List.of(featured));
        }
    }

    private void renderReferenceBook(VBox target, BoardPosition position) {
        target.getChildren().clear();
        Optional<OpeningEntry> entry = openingBook.lookup(position);
        Label name = new Label(entry.map(e -> e.name() + " • " + e.eco()).orElse("Posição sem nome no catálogo"));
        name.getStyleClass().add("feedback-theme");
        Label fen = new Label(position.fen());
        fen.setWrapText(true);
        fen.getStyleClass().add("muted");
        target.getChildren().addAll(name, fen);

        List<BookContinuation> continuations = openingBook.nextMoves(position);
        if (!continuations.isEmpty()) {
            Label heading = fieldLabel("CONTINUAÇÕES DO JSON");
            FlowPane rows = new FlowPane(8, 8);
            for (BookContinuation move : continuations.stream().limit(12).toList()) {
                Label chip = new Label(move.san() + (move.eco().isBlank() ? "" : " · " + move.eco())
                        + (move.opening().isBlank() ? "" : " · " + move.opening()));
                chip.getStyleClass().add("theme-chip");
                rows.getChildren().add(chip);
            }
            target.getChildren().addAll(heading, rows);
        }
        List<ReferenceMoveStat> stats = openingBook.referenceStatistics(position);
        if (!stats.isEmpty()) {
            Label heading = fieldLabel("ESTATÍSTICAS DE REFERÊNCIA");
            Label values = new Label(formatReferenceStats(stats));
            values.setWrapText(true);
            values.getStyleClass().add("body-text");
            target.getChildren().addAll(heading, values);
        }
        if (continuations.isEmpty() && stats.isEmpty()) {
            Label empty = new Label("O catálogo não tem continuação/estatística adicional para esta posição.");
            empty.getStyleClass().add("muted");
            target.getChildren().add(empty);
        }
    }

    private String formatReferenceStats(List<ReferenceMoveStat> stats) {
        return stats.stream().limit(8)
                .map(row -> row.uci() + " — " + row.games() + " jogo(s), score brancas "
                        + String.format(java.util.Locale.ROOT, "%.1f%%", row.whiteScore()) + " [" + row.source() + "]")
                .reduce((a, b) -> a + "\n" + b).orElse("Sem estatísticas.");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Erro inesperado." : message);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private void refreshSnapshot() {
        snapshot = analyticsService.buildSnapshot();
        if (profileNameLabel != null) profileNameLabel.setText(accountSettings.activeUsername());
        if (profileRatingLabel != null) profileRatingLabel.setText(ratingText());
        if (dataStatusLabel != null) dataStatusLabel.setText(
                repository.isDemoMode() ? "●  Modo demonstração" : "●  Dados sincronizados");
    }

    private String ratingText() {
        return snapshot.currentRating() > 0 ? "Rating  " + snapshot.currentRating() : "Rating não disponível";
    }

    private String ratingValue(int rating) {
        return rating > 0 ? String.valueOf(rating) : "--";
    }

    private String samplePgn() {
        return """
                [Event "Partida casual"]
                [Site "https://lichess.org"]
                [Date "2026.09.14"]
                [White "eduardo"]
                [Black "Adversario"]
                [WhiteElo "1804"]
                [BlackElo "1790"]
                [Result "1-0"]
                [ECO "C50"]
                [Opening "Abertura Italiana"]

                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 Bb4+ 7. Nc3 1-0
                """;
    }

    public static void main(String[] args) { launch(args); }
}
