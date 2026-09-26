package com.battleship.view;

import com.battleship.controller.GameController;
import com.battleship.model.Coordinate;
import com.battleship.model.Orientation;
import com.battleship.model.Player;
import com.battleship.model.projection.ShipSnapshot;
import com.battleship.model.ShipType;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * template method base for both ship-placement screens (local + network).
 *
 * <p>owns every shared concern: the dock, the board grid, native drag-and-drop
 * (drag-over/enter/exit/drop plus click-to-remove), the orientation label and
 * its rotate wiring, the placement counter, the ready gating, and the exit
 * confirmation. subclasses contribute only their screen chrome
 * ({@link #assemblelayout()}, {@link #decorateroot(pane)}) and their ready /
 * exit behaviour.</p>
 *
 * <p>this mirrors the {@link abstractbattleview} design already proven on the
 * battle screens, eliminating the former near-verbatim duplication between
 * {@code shipplaceview} and {@code networkshipplaceview}.</p>
 */
public abstract class AbstractShipPlaceView {

    protected final ViewNavigator nav;
    protected final GameController controller;
    /** the admiral deploying here (local: placing player; network: {@code netsession.getme()}). */
    protected final Player player;
    /** audio facade injected from the navigator — never the static singleton. */
    protected final GameAudio audio;

    protected BoardGridPane boardGridPane;
    protected ShipDockPane dockPane;
    protected Label orientationLabel;
    protected Label countLabel;
    protected Button readyButton;

    /** current ship orientation, toggled by r / right-click / the rotate button. */
    protected Orientation orientation = Orientation.HORIZONTAL;

    private final List<Coordinate> ghostCells = new ArrayList<>();

    protected AbstractShipPlaceView(ViewNavigator nav, GameController controller, Player player) {
        this.nav = nav;
        this.controller = controller;
        this.player = player;
        this.audio = nav.getAudio();
    }

    // ================= template method =================

    private static double computePlacementCellSize(int size) {
        if (size <= 5) return 84;
        if (size <= 8) return 52;
        return 42;
    }

    protected BoardGridPane createBoardGrid() {
        int boardSize = controller.getSelectedTheater().getBoardSize();
        return new BoardGridPane(boardSize, computePlacementCellSize(boardSize));
    }

    /** assembles the shared placement skeleton. subclasses customize via hooks only. */
    public final StackPane build() {
        dockPane = createDock();
        boardGridPane = createBoardGrid();
        setupDragTargets();

        orientationLabel = new Label();
        orientationLabel.getStyleClass().addAll("accent-text", "orientation-label");
        updateOrientationLabel();

        countLabel = new Label();

        readyButton = new Button(readyButtonLabel());
        readyButton.getStyleClass().add("primary-button");
        readyButton.setOnAction(e -> {
            audio.playClick();
            onReadyPressed();
        });

        Pane layout = assembleLayout();
        StackPane root = decorateRoot(layout);

        root.setFocusTraversable(true);
        root.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("R")) toggleOrientation();
        });
        root.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) toggleOrientation();
        });
        root.requestFocus();

        onViewShown();
        refreshAll();
        return root;
    }

    // ================= hooks (subclass responsibilities) =================

    /** assembles the screen-specific chrome around the shared widgets. */
    protected abstract Pane assembleLayout();

    /** optional root decoration (e.g. the animated ocean backdrop). */
    protected abstract StackPane decorateRoot(Pane layout);

    /** handles the ready button (local: confirm + route; network: socket handshake). */
    protected abstract void onReadyPressed();

    /** confirmation text shown by the shared exit dialog. */
    protected abstract String exitPrompt();

    /** extra teardown once the player confirms exit, before returning to the menu. */
    protected abstract void onExitConfirmed();

    // ================= overridable hooks (sensible defaults) =================

    /** extra reason to keep the ready button disabled (e.g. ready already sent). */
    protected boolean isReadyLocked() { return false; }

    /** called once the screen is fully assembled and wired, before the first refresh. */
    protected void onViewShown() { }

    /** called after every orientation change. */
    protected void onOrientationToggled() { }

    /** called at the end of {@link #refreshall()} for extra per-screen labels. */
    protected void onRefreshed() { }

    /** ready button caption. */
    protected String readyButtonLabel() { return "READY"; }

    // ================= shared behaviour =================

    /** builds the dock tray; subclasses add style class / width in {@link #assemblelayout()}. */
    protected final ShipDockPane createDock() {
        ShipDockPane dock = new ShipDockPane(controller, player);
        dock.setOrientation(orientation);
        return dock;
    }

    /** shared exit button wired to the common confirm dialog. */
    protected final Button buildExitButton() {
        Button exit = new Button("EXIT");
        exit.getStyleClass().add("danger-button");
        exit.setOnAction(e -> {
            audio.playClick();
            confirmExit();
        });
        return exit;
    }

    /** wires drag-and-drop and click-to-remove onto every board cell. */
    protected void setupDragTargets() {
        int size = boardGridPane.getSize();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                final int row = r, col = c;
                StackPane cell = boardGridPane.getCell(r, c);

                cell.setOnDragOver(event -> {
                    if (event.getDragboard().hasString()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                    event.consume();
                });

                cell.setOnDragEntered(event -> {
                    if (!event.getDragboard().hasString()) return;
                    ShipType type = ShipType.valueOf(event.getDragboard().getString());
                    showGhost(row, col, type);
                });

                cell.setOnDragExited(event -> clearGhost());

                cell.setOnMouseEntered(event -> {
                    ShipType selected = dockPane.getSelectedShip();
                    if (selected != null) {
                        showGhost(row, col, selected);
                    }
                });

                cell.setOnMouseExited(event -> {
                    if (dockPane.getSelectedShip() != null) {
                        clearGhost();
                    }
                });

                cell.setOnMouseClicked(event -> {
                    if (event.getButton() != MouseButton.PRIMARY) return;
                    Coordinate clicked = new Coordinate(row, col);
                    ShipType selected = dockPane.getSelectedShip();
                    if (selected != null) {
                        clearGhost();
                        boolean placed = controller.placeShip(player, selected, clicked, orientation);
                        if (placed) {
                            audio.playPlaceShip();
                            refreshAll();
                        } else {
                            shakeCell(cell);
                        }
                    } else {
                        boolean removed = controller.removeShipAt(player, clicked);
                        if (removed) {
                            audio.playRemoveShip();
                            refreshAll();
                        }
                    }
                });

                cell.setOnDragDropped(event -> {
                    if (!event.getDragboard().hasString()) {
                        event.setDropCompleted(false);
                        event.consume();
                        return;
                    }
                    ShipType type = ShipType.valueOf(event.getDragboard().getString());
                    clearGhost();
                    boolean placed = controller.placeShip(player, type, new Coordinate(row, col), orientation);
                    if (placed) {
                        audio.playPlaceShip();
                        refreshAll();
                    } else {
                        shakeCell(cell);
                    }
                    event.setDropCompleted(placed);
                    event.consume();
                });
            }
        }
    }

    private void showGhost(int row, int col, ShipType type) {
        clearGhost();
        boolean valid = controller.canPlace(player, type, new Coordinate(row, col), orientation);
        String stateClass = valid ? BoardGridPane.GHOST_VALID : BoardGridPane.GHOST_INVALID;
        for (int i = 0; i < type.getSize(); i++) {
            int gr = orientation.isHorizontal() ? row : row + i;
            int gc = orientation.isHorizontal() ? col + i : col;
            if (gr < 0 || gr >= boardGridPane.getSize() || gc < 0 || gc >= boardGridPane.getSize()) continue;
            Coordinate ghostCoord = new Coordinate(gr, gc);
            boardGridPane.setCellState(ghostCoord, stateClass);
            ghostCells.add(ghostCoord);
        }
    }

    private void clearGhost() {
        for (Coordinate c : ghostCells) {
            boardGridPane.resetCellStyle(c.getRow(), c.getCol());
        }
        ghostCells.clear();
        // re-render any already-placed ships that may have been under the ghost.
        for (com.battleship.model.projection.ShipSnapshot s : player.fleet()) {
            boardGridPane.renderShip(s);
        }
    }

    private void shakeCell(StackPane cell) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), cell);
        shake.setByX(4);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();
    }

    /** re-renders the board, dock and counter, then re-evaluates the ready gating. */
    protected void refreshAll() {
        boardGridPane.clearAll();
        for (com.battleship.model.projection.ShipSnapshot s : player.fleet()) {
            boardGridPane.renderShip(s);
        }
        dockPane.refresh();
        int placed = player.deployedShipCount();
        int total = controller.getSelectedTheater().getTotalShipCount();
        countLabel.setText("Ships placed: " + placed + " / " + total);
        readyButton.setDisable(!controller.isPlacementComplete(player) || isReadyLocked());
        onRefreshed();
    }

    protected void toggleOrientation() {
        orientation = orientation.toggle();
        updateOrientationLabel();
        dockPane.setOrientation(orientation);
        onOrientationToggled();
    }

    protected void updateOrientationLabel() {
        orientationLabel.setText("Current orientation: " + (orientation.isHorizontal() ? "HORIZONTAL" : "VERTICAL"));
    }

    private void confirmExit() {
        if (AlertUtil.showConfirmation(nav.window(), "Exit Game", exitPrompt())) {
            onExitConfirmed();
            audio.stopBgm();
            audio.playMenuMusic();
            nav.showMainMenu();
        }
    }
}