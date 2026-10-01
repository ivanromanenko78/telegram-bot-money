package com.ivan.battleship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BattleshipGame {
    public static final int SIZE = 10;
    public static final int[] FLEET = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    public enum ShotResult { MISS, HIT, SUNK, ALREADY }

    public static class ShipPlacement {
        public final int id;
        public final int len;
        public final int x;
        public final int y;
        public final boolean horizontal;

        public ShipPlacement(int id, int len, int x, int y, boolean horizontal) {
            this.id = id;
            this.len = len;
            this.x = x;
            this.y = y;
            this.horizontal = horizontal;
        }
    }

    public static class Board {
        private final int[][] ship = new int[SIZE][SIZE];
        private final boolean[][] shot = new boolean[SIZE][SIZE];
        private final Random random;
        private int shipsCount;

        Board(Random random) {
            this(random, true);
        }

        Board(Random random, boolean autoPlace) {
            this.random = random;
            clearBoard();
            shipsCount = FLEET.length;
            if (autoPlace) {
                autoPlaceAllShips();
            }
        }

        private void clearBoard() {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    ship[y][x] = -1;
                    shot[y][x] = false;
                }
            }
        }

        public void resetAndPlace() {
            clearBoard();
            shipsCount = FLEET.length;
            autoPlaceAllShips();
        }

        private void autoPlaceAllShips() {
            for (int id = 0; id < FLEET.length; id++) {
                placeShipRandom(id, FLEET[id]);
            }
        }

        private void placeShipRandom(int id, int len) {
            for (int tries = 0; tries < 5000; tries++) {
                boolean horizontal = random.nextBoolean();
                int x = random.nextInt(SIZE);
                int y = random.nextInt(SIZE);
                if (canPlaceShip(id, x, y, len, horizontal)) {
                    placeShipManual(id, x, y, len, horizontal);
                    return;
                }
            }
            throw new IllegalStateException("Could not place ship");
        }

        public void clearShipsAndShots() {
            clearBoard();
            shipsCount = FLEET.length;
        }

        public void clearShotsOnly() {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    shot[y][x] = false;
                }
            }
            shipsCount = FLEET.length;
        }

        public boolean canPlaceShip(int ignoreId, int x, int y, int len, boolean horizontal) {
            int endX = x + (horizontal ? len - 1 : 0);
            int endY = y + (horizontal ? 0 : len - 1);
            if (x < 0 || y < 0 || endX >= SIZE || endY >= SIZE) return false;

            for (int i = 0; i < len; i++) {
                int px = x + (horizontal ? i : 0);
                int py = y + (horizontal ? 0 : i);
                for (int yy = py - 1; yy <= py + 1; yy++) {
                    for (int xx = px - 1; xx <= px + 1; xx++) {
                        if (xx >= 0 && xx < SIZE && yy >= 0 && yy < SIZE) {
                            int otherId = ship[yy][xx];
                            if (otherId != -1 && otherId != ignoreId) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }

        public void removeShip(int id) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (ship[y][x] == id) ship[y][x] = -1;
                }
            }
        }

        public void placeShipManual(int id, int x, int y, int len, boolean horizontal) {
            for (int i = 0; i < len; i++) {
                int px = x + (horizontal ? i : 0);
                int py = y + (horizontal ? 0 : i);
                ship[py][px] = id;
            }
        }

        public ShotResult shoot(int x, int y) {
            if (!inBounds(x, y) || shot[y][x]) return ShotResult.ALREADY;
            shot[y][x] = true;
            int id = ship[y][x];
            if (id == -1) return ShotResult.MISS;
            if (isSunk(id)) {
                shipsCount--;
                markWaterAround(id);
                return ShotResult.SUNK;
            }
            return ShotResult.HIT;
        }

        private void markWaterAround(int id) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (ship[y][x] == id) {
                        for (int yy = y - 1; yy <= y + 1; yy++) {
                            for (int xx = x - 1; xx <= x + 1; xx++) {
                                if (inBounds(xx, yy) && ship[yy][xx] == -1) shot[yy][xx] = true;
                            }
                        }
                    }
                }
            }
        }

        public boolean isSunk(int id) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (ship[y][x] == id && !shot[y][x]) return false;
                }
            }
            return true;
        }

        public boolean hasShip(int x, int y) { return ship[y][x] != -1; }
        public int shipIdAt(int x, int y) { return ship[y][x]; }
        public boolean wasShot(int x, int y) { return shot[y][x]; }
        public boolean allSunk() { return shipsCount <= 0; }
        public int shipsLeft() { return shipsCount; }
        private boolean inBounds(int x, int y) { return x >= 0 && x < SIZE && y >= 0 && y < SIZE; }
    }

    private final Random random = new Random();
    private Board player;
    private Board enemy;
    private boolean gameOver;
    private boolean playerTurn = true;
    private final ArrayList<int[]> aiTargets = new ArrayList<>();

    public BattleshipGame() { newGame(); }

    public void newGame() {
        player = new Board(random);
        enemy = new Board(random);
        gameOver = false;
        playerTurn = true;
        aiTargets.clear();
    }

    public void newGameWithPlayerPlacements(List<ShipPlacement> placements) {
        player = new Board(random, false);
        player.clearShipsAndShots();
        for (ShipPlacement placement : placements) {
            player.placeShipManual(placement.id, placement.x, placement.y, placement.len, placement.horizontal);
        }
        player.clearShotsOnly();
        enemy = new Board(random);
        gameOver = false;
        playerTurn = true;
        aiTargets.clear();
    }

    public void rearrangePlayer() {
        if (!gameOver && playerTurn && noPlayerShotsReceived()) player.resetAndPlace();
    }

    private boolean noPlayerShotsReceived() {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (player.wasShot(x, y)) return false;
        return true;
    }

    public ShotResult playerShoot(int x, int y) {
        if (gameOver || !playerTurn) return ShotResult.ALREADY;
        ShotResult result = enemy.shoot(x, y);
        if (result == ShotResult.ALREADY) return result;
        if (enemy.allSunk()) {
            gameOver = true;
            return result;
        }
        if (result == ShotResult.MISS) playerTurn = false;
        return result;
    }

    public AiShot aiShoot() {
        if (gameOver || playerTurn) return null;
        int[] cell = chooseAiCell();
        ShotResult result = player.shoot(cell[0], cell[1]);
        if (result == ShotResult.HIT) addNeighbors(cell[0], cell[1]);
        if (result == ShotResult.SUNK) aiTargets.clear();
        if (player.allSunk()) {
            gameOver = true;
        } else if (result == ShotResult.MISS) {
            playerTurn = true;
            aiTargets.clear();
        }
        return new AiShot(cell[0], cell[1], result);
    }

    private int[] chooseAiCell() {
        while (!aiTargets.isEmpty()) {
            int index = random.nextInt(aiTargets.size());
            int[] c = aiTargets.remove(index);
            if (!player.wasShot(c[0], c[1])) return c;
        }
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!player.wasShot(x, y)) cells.add(new int[]{x, y});
            }
        }
        Collections.shuffle(cells, random);
        return cells.get(0);
    }

    private void addNeighbors(int x, int y) {
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] q : d) {
            int nx = x + q[0], ny = y + q[1];
            if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE && !player.wasShot(nx, ny)) {
                aiTargets.add(new int[]{nx, ny});
            }
        }
    }

    public Board getPlayer() { return player; }
    public Board getEnemy() { return enemy; }
    public boolean isGameOver() { return gameOver; }
    public boolean isPlayerTurn() { return playerTurn; }
    public boolean playerWon() { return gameOver && enemy.allSunk(); }

    public static class AiShot {
        public final int x, y;
        public final ShotResult result;
        AiShot(int x, int y, ShotResult result) {
            this.x = x; this.y = y; this.result = result;
        }
    }
}
