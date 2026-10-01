package com.ivan.battleship;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BattleshipView extends View {
    private final BattleshipGame game = new BattleshipGame();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private boolean showEnemy = false;
    private boolean placementMode = true;
    private String message = "Расставьте свои корабли: перетаскивайте, тап — поворот";

    private final RectF boardRect = new RectF();
    private final RectF newGameRect = new RectF();
    private final RectF toggleRect = new RectF();
    private final RectF bottomActionRect = new RectF();
    private final RectF playerFleetRect = new RectF();
    private final RectF enemyFleetRect = new RectF();
    private final RectF dockRect = new RectF();

    private boolean aiBusy = false;

    private final int bg = 0xFF071B2F;
    private final int panel = 0xFF0D2B47;
    private final int water = 0xFF0F5D82;
    private final int water2 = 0xFF126B94;
    private final int line = 0xFF77B8D1;
    private final int ship = 0xFF84D8E7;
    private final int shipDark = 0xFF4AA9BB;
    private final int hit = 0xFFFF6B5F;
    private final int miss = 0xFFE4EEF2;
    private final int text = 0xFFF5FBFF;
    private final int accent = 0xFFFFC857;
    private final int sunk = 0xFF7A8793;
    private final int flame1 = 0xFFFFC13D;
    private final int flame2 = 0xFFFF7A31;
    private final int flame3 = 0xFFFF4D1D;

    private final int[] lengths = BattleshipGame.FLEET;
    private final boolean[] setupPlaced = new boolean[lengths.length];
    private final boolean[] setupHorizontal = new boolean[lengths.length];
    private final int[] setupX = new int[lengths.length];
    private final int[] setupY = new int[lengths.length];
    private final RectF[] dockShipRects = new RectF[lengths.length];

    private int draggingShipId = -1;
    private boolean draggingWasPlaced = false;
    private int dragOriginalX = -1;
    private int dragOriginalY = -1;
    private boolean dragOriginalHorizontal = true;
    private float dragTouchX;
    private float dragTouchY;
    private float dragStartX;
    private float dragStartY;
    private boolean dragMoved = false;

    public BattleshipView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        setBackgroundColor(bg);
        setFocusable(true);
        for (int i = 0; i < lengths.length; i++) {
            setupHorizontal[i] = true;
            dockShipRects[i] = new RectF();
        }
    }

    private float dp(float v) { return v * density; }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float top = dp(20);
        float margin = dp(16);
        float gap = dp(8);

        p.setColor(text);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(28));
        p.setFakeBoldText(true);
        c.drawText("МОРСКОЙ БОЙ", w / 2f, top + dp(28), p);
        p.setFakeBoldText(false);

        p.setColor(0xFFB8D8E8);
        p.setTextSize(dp(13));
        c.drawText(message, w / 2f, top + dp(52), p);

        float btnY = top + dp(70);
        float btnW = (w - margin * 2 - gap) / 2f;
        newGameRect.set(margin, btnY, margin + btnW, btnY + dp(44));
        toggleRect.set(margin + btnW + gap, btnY, w - margin, btnY + dp(44));
        drawButton(c, newGameRect, "Новая игра", false);
        drawButton(c, toggleRect, placementMode ? "Авторасстановка" : (showEnemy ? "Показать моё поле" : "Поле врага"), true);

        float labelY = btnY + dp(68);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(dp(15));
        p.setColor(placementMode ? ship : (showEnemy ? accent : ship));
        p.setFakeBoldText(true);
        c.drawText(placementMode ? "РАССТАНОВКА ФЛОТА" : (showEnemy ? "ПОЛЕ ВРАГА" : "МОЁ ПОЛЕ"), margin, labelY, p);
        p.setFakeBoldText(false);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xFFB8D8E8);
        String score = "Ваши: " + game.getPlayer().shipsLeft() + "   Враг: " + game.getEnemy().shipsLeft();
        c.drawText(score, w - margin, labelY, p);

        float fleetsTop = labelY + dp(18);
        float fleetH = dp(42);
        playerFleetRect.set(margin, fleetsTop, w - margin, fleetsTop + fleetH);
        enemyFleetRect.set(margin, fleetsTop + fleetH + dp(6), w - margin, fleetsTop + fleetH * 2 + dp(6));
        drawFleetRow(c, playerFleetRect, "Ваш флот", false);
        drawFleetRow(c, enemyFleetRect, "Флот врага", true);

        float gridTop = enemyFleetRect.bottom + dp(12);
        float reservedBottom = placementMode ? dp(150) : dp(86);
        float side = Math.min(w - dp(56), getHeight() - gridTop - reservedBottom);
        side = Math.max(side, dp(240));
        float left = (w - side) / 2f;
        boardRect.set(left, gridTop, left + side, gridTop + side);

        if (placementMode || !showEnemy) {
            drawBoardBackground(c);
            if (placementMode) drawSetupShipsOnBoard(c);
            else drawBoard(c, game.getPlayer(), false);
        } else {
            drawBoard(c, game.getEnemy(), true);
        }

        float bottomY = boardRect.bottom + dp(14);
        if (placementMode) {
            dockRect.set(margin, bottomY, w - margin, bottomY + dp(58));
            drawDock(c, dockRect);
            bottomActionRect.set(margin, dockRect.bottom + dp(10), w - margin, dockRect.bottom + dp(54));
            drawButton(c, bottomActionRect, allShipsPlaced() ? "Готово — начать игру" : "Сначала расставьте все корабли", allShipsPlaced());
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(12));
            p.setColor(0xFF7FA9BC);
            c.drawText("Тап по кораблю — поворот. Перетащите корабль на поле пальцем.", w / 2f, bottomActionRect.bottom + dp(18), p);
        } else {
            bottomActionRect.set(margin, bottomY, w - margin, bottomY + dp(46));
            boolean canManual = !game.isGameOver() && game.isPlayerTurn() && noPlayerShotsReceived();
            drawButton(c, bottomActionRect, canManual ? "Ручная расстановка" : "Расстановка недоступна после начала боя", canManual);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(12));
            p.setColor(0xFF7FA9BC);
            c.drawText("Внизу показаны все корабли. Серые — потоплены.", w / 2f, bottomActionRect.bottom + dp(18), p);
        }

        if (placementMode && draggingShipId >= 0) {
            drawFloatingShip(c, draggingShipId, dragTouchX, dragTouchY);
        }
    }

    private void drawBoardBackground(Canvas c) {
        float cell = boardRect.width() / 10f;
        p.setTextSize(Math.max(dp(10), cell * 0.30f));
        p.setColor(0xFF8DB8CB);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 10; i++) {
            char ch = (char) ('A' + i);
            c.drawText(String.valueOf(ch), boardRect.left + cell * (i + .5f), boardRect.top - dp(5), p);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.valueOf(i + 1), boardRect.left - dp(6), boardRect.top + cell * (i + .5f) - (p.ascent() + p.descent()) / 2f, p);
            p.setTextAlign(Paint.Align.CENTER);
        }
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                float l = boardRect.left + x * cell;
                float t = boardRect.top + y * cell;
                RectF r = new RectF(l, t, l + cell, t + cell);
                p.setColor(((x + y) & 1) == 0 ? water : water2);
                p.setStyle(Paint.Style.FILL);
                c.drawRect(r, p);
                stroke.setColor(line);
                stroke.setStrokeWidth(dp(.7f));
                c.drawRect(r, stroke);
            }
        }
        stroke.setColor(0xFFA7D5E7);
        stroke.setStrokeWidth(dp(2));
        c.drawRect(boardRect, stroke);
    }

    private void drawBoard(Canvas c, BattleshipGame.Board board, boolean enemyBoard) {
        drawBoardBackground(c);
        float cell = boardRect.width() / 10f;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                float l = boardRect.left + x * cell;
                float t = boardRect.top + y * cell;

                boolean visibleShip = board.hasShip(x, y) && (!enemyBoard || (game.isGameOver() && !game.playerWon()));
                if (visibleShip) drawShipCell(c, l, t, cell, ship, shipDark);

                if (board.wasShot(x, y)) {
                    if (board.hasShip(x, y)) drawFlame(c, l + cell * .5f, t + cell * .5f, cell * .44f);
                    else {
                        p.setColor(miss);
                        c.drawCircle(l + cell * .5f, t + cell * .5f, Math.max(dp(2.1f), cell * .08f), p);
                    }
                }
            }
        }
    }

    private void drawShipCell(Canvas c, float l, float t, float cell, int fill, int inner) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        RectF sr = new RectF(l + cell * .10f, t + cell * .10f, l + cell * .90f, t + cell * .90f);
        c.drawRoundRect(sr, cell * .16f, cell * .16f, p);
        p.setColor(inner);
        RectF ir = new RectF(l + cell * .19f, t + cell * .19f, l + cell * .81f, t + cell * .81f);
        c.drawRoundRect(ir, cell * .12f, cell * .12f, p);
    }

    private void drawFlame(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(flame3);
        c.drawCircle(cx, cy + r * 0.05f, r * 0.95f, p);
        p.setColor(flame2);
        c.drawCircle(cx - r * 0.18f, cy + r * 0.05f, r * 0.68f, p);
        c.drawCircle(cx + r * 0.15f, cy - r * 0.08f, r * 0.62f, p);
        p.setColor(flame1);
        c.drawCircle(cx, cy - r * 0.08f, r * 0.42f, p);
        Path path = new Path();
        path.moveTo(cx, cy - r * 1.15f);
        path.quadTo(cx + r * 0.45f, cy - r * 0.45f, cx, cy + r * 0.15f);
        path.quadTo(cx - r * 0.45f, cy - r * 0.45f, cx, cy - r * 1.15f);
        p.setColor(flame2);
        c.drawPath(path, p);
    }

    private void drawButton(Canvas c, RectF r, String label, boolean active) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(active ? 0xFF174D70 : panel);
        c.drawRoundRect(r, dp(12), dp(12), p);
        stroke.setColor(active ? 0xFF4BB5E8 : 0xFF2B5976);
        c.drawRoundRect(r, dp(12), dp(12), stroke);
        p.setColor(text);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(14));
        p.setFakeBoldText(true);
        float y = r.centerY() - (p.ascent() + p.descent()) / 2f;
        c.drawText(label, r.centerX(), y, p);
        p.setFakeBoldText(false);
    }

    private void drawFleetRow(Canvas c, RectF r, String title, boolean enemyFleet) {
        p.setColor(panel);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(r, dp(12), dp(12), p);
        stroke.setColor(0xFF2B5976);
        c.drawRoundRect(r, dp(12), dp(12), stroke);

        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(dp(12));
        p.setColor(0xFFB8D8E8);
        c.drawText(title, r.left + dp(10), r.top + dp(14), p);

        float iconY = r.centerY() + dp(4);
        float x = r.left + dp(10);
        float unit = Math.max(dp(8), Math.min(dp(12), (r.width() - dp(30)) / 26f));

        for (int id = 0; id < lengths.length; id++) {
            int len = lengths[id];
            boolean sunkState = enemyFleet ? game.getEnemy().isSunk(id) : game.getPlayer().isSunk(id);
            int fill = sunkState ? sunk : ship;
            for (int i = 0; i < len; i++) {
                float left = x + i * unit * 1.05f;
                RectF cell = new RectF(left, iconY - unit * 0.55f, left + unit * 0.82f, iconY + unit * 0.27f);
                p.setColor(fill);
                c.drawRoundRect(cell, unit * 0.2f, unit * 0.2f, p);
                if (sunkState) {
                    stroke.setColor(hit);
                    stroke.setStrokeWidth(dp(1.5f));
                    c.drawLine(cell.left, cell.top, cell.right, cell.bottom, stroke);
                    c.drawLine(cell.right, cell.top, cell.left, cell.bottom, stroke);
                }
            }
            x += len * unit * 1.05f + unit * 0.7f;
        }
    }

    private void drawDock(Canvas c, RectF r) {
        p.setColor(panel);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(r, dp(12), dp(12), p);
        stroke.setColor(0xFF2B5976);
        c.drawRoundRect(r, dp(12), dp(12), stroke);

        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(dp(12));
        p.setColor(0xFFB8D8E8);
        c.drawText("Перетащите корабли на поле", r.left + dp(10), r.top + dp(14), p);

        float cursorX = r.left + dp(10);
        float y = r.top + dp(22);
        float cell = Math.min(dp(22), (r.width() - dp(30)) / 15f);

        for (int id = 0; id < lengths.length; id++) {
            if (setupPlaced[id] && draggingShipId != id) {
                dockShipRects[id].setEmpty();
                continue;
            }
            float drawLen = setupHorizontal[id] ? lengths[id] * cell : cell;
            float drawH = setupHorizontal[id] ? cell : lengths[id] * cell;
            if (cursorX + drawLen > r.right - dp(10)) {
                y += dp(28);
                cursorX = r.left + dp(10);
            }
            RectF shipRect = dockShipRects[id];
            shipRect.set(cursorX, y, cursorX + drawLen, y + drawH);
            if (draggingShipId != id) drawPlacementShip(c, id, shipRect.left, shipRect.top);
            cursorX = shipRect.right + dp(10);
        }
    }

    private void drawSetupShipsOnBoard(Canvas c) {
        for (int id = 0; id < lengths.length; id++) {
            if (setupPlaced[id] && draggingShipId != id) {
                drawPlacementShipAtCell(c, id, setupX[id], setupY[id], setupHorizontal[id]);
            }
        }
    }

    private void drawPlacementShipAtCell(Canvas c, int id, int x, int y, boolean horizontal) {
        float cell = boardRect.width() / 10f;
        float left = boardRect.left + x * cell;
        float top = boardRect.top + y * cell;
        float width = horizontal ? lengths[id] * cell : cell;
        float height = horizontal ? cell : lengths[id] * cell;
        RectF rr = new RectF(left + cell * .08f, top + cell * .08f, left + width - cell * .08f, top + height - cell * .08f);
        p.setColor(ship);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(rr, cell * .18f, cell * .18f, p);
        p.setColor(shipDark);
        RectF inner = new RectF(rr.left + cell * .10f, rr.top + cell * .10f, rr.right - cell * .10f, rr.bottom - cell * .10f);
        c.drawRoundRect(inner, cell * .14f, cell * .14f, p);
    }

    private void drawPlacementShip(Canvas c, int id, float left, float top) {
        float cell = Math.min(dp(22), boardRect.width() / 10f);
        float width = setupHorizontal[id] ? lengths[id] * cell : cell;
        float height = setupHorizontal[id] ? cell : lengths[id] * cell;
        RectF rr = new RectF(left, top, left + width, top + height);
        p.setColor(ship);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(rr, cell * .18f, cell * .18f, p);
        p.setColor(shipDark);
        RectF inner = new RectF(rr.left + cell * .09f, rr.top + cell * .09f, rr.right - cell * .09f, rr.bottom - cell * .09f);
        c.drawRoundRect(inner, cell * .14f, cell * .14f, p);
    }

    private void drawFloatingShip(Canvas c, int id, float x, float y) {
        float cell = boardRect.width() / 10f;
        float width = setupHorizontal[id] ? lengths[id] * cell : cell;
        float height = setupHorizontal[id] ? cell : lengths[id] * cell;
        float left = x - width / 2f;
        float top = y - height / 2f;
        RectF rr = new RectF(left, top, left + width, top + height);
        p.setColor(0xCC84D8E7);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(rr, cell * .18f, cell * .18f, p);
        stroke.setColor(accent);
        stroke.setStrokeWidth(dp(2));
        c.drawRoundRect(rr, cell * .18f, cell * .18f, stroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        if (placementMode) return handlePlacementTouch(e, x, y);
        if (e.getAction() != MotionEvent.ACTION_UP) return true;

        if (newGameRect.contains(x, y)) {
            resetToPlacement();
            return true;
        }
        if (toggleRect.contains(x, y)) {
            showEnemy = !showEnemy;
            invalidate();
            return true;
        }
        if (bottomActionRect.contains(x, y) && !game.isGameOver() && game.isPlayerTurn() && noPlayerShotsReceived()) {
            enterPlacementFromCurrentBoard();
            return true;
        }

        if (!showEnemy || aiBusy || !game.isPlayerTurn() || game.isGameOver()) return true;
        if (boardRect.contains(x, y)) {
            int cx = (int) ((x - boardRect.left) / (boardRect.width() / 10f));
            int cy = (int) ((y - boardRect.top) / (boardRect.height() / 10f));
            if (cx < 0 || cx >= 10 || cy < 0 || cy >= 10) return true;
            BattleshipGame.ShotResult r = game.playerShoot(cx, cy);
            if (r == BattleshipGame.ShotResult.ALREADY) return true;
            vibrate(r == BattleshipGame.ShotResult.MISS ? 18 : 45);
            if (game.isGameOver()) {
                message = game.playerWon() ? "Победа! Флот врага уничтожен." : "Поражение";
                invalidate();
                return true;
            }
            if (r == BattleshipGame.ShotResult.MISS) {
                message = "Мимо. Ход компьютера…";
                aiBusy = true;
                invalidate();
                postDelayed(this::runAiTurn, 420);
            } else if (r == BattleshipGame.ShotResult.SUNK) {
                message = "Корабль потоплен! Стреляйте ещё.";
                invalidate();
            } else {
                message = "Попадание! Стреляйте ещё.";
                invalidate();
            }
        }
        return true;
    }

    private boolean handlePlacementTouch(MotionEvent e, float x, float y) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (newGameRect.contains(x, y)) {
                    resetToPlacement();
                    return true;
                }
                if (toggleRect.contains(x, y)) {
                    autoArrangeSetup();
                    return true;
                }
                if (bottomActionRect.contains(x, y) && allShipsPlaced()) {
                    startGameFromPlacement();
                    return true;
                }
                startDraggingIfHit(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingShipId >= 0) {
                    dragTouchX = x;
                    dragTouchY = y;
                    if (Math.abs(x - dragStartX) > dp(5) || Math.abs(y - dragStartY) > dp(5)) dragMoved = true;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (draggingShipId >= 0) {
                    finishDragging(x, y);
                    invalidate();
                }
                return true;
        }
        return true;
    }

    private void startDraggingIfHit(float x, float y) {
        draggingShipId = findShipAtBoardPoint(x, y);
        if (draggingShipId >= 0) {
            draggingWasPlaced = true;
            dragOriginalX = setupX[draggingShipId];
            dragOriginalY = setupY[draggingShipId];
            dragOriginalHorizontal = setupHorizontal[draggingShipId];
            setupPlaced[draggingShipId] = false;
        } else {
            draggingShipId = findShipAtDockPoint(x, y);
            draggingWasPlaced = false;
            if (draggingShipId >= 0) {
                dragOriginalX = -1;
                dragOriginalY = -1;
                dragOriginalHorizontal = setupHorizontal[draggingShipId];
            }
        }
        if (draggingShipId >= 0) {
            dragStartX = dragTouchX = x;
            dragStartY = dragTouchY = y;
            dragMoved = false;
            vibrate(10);
            invalidate();
        }
    }

    private void finishDragging(float x, float y) {
        int id = draggingShipId;
        if (!dragMoved) {
            boolean newOrientation = !setupHorizontal[id];
            if (draggingWasPlaced) {
                if (canPlaceSetupShip(id, dragOriginalX, dragOriginalY, newOrientation)) setupHorizontal[id] = newOrientation;
                else setupHorizontal[id] = dragOriginalHorizontal;
                setupPlaced[id] = true;
                setupX[id] = dragOriginalX;
                setupY[id] = dragOriginalY;
            } else {
                setupHorizontal[id] = newOrientation;
            }
        } else {
            if (!dropSetupShip(id, x, y) && draggingWasPlaced) {
                setupPlaced[id] = true;
                setupX[id] = dragOriginalX;
                setupY[id] = dragOriginalY;
                setupHorizontal[id] = dragOriginalHorizontal;
            }
        }
        draggingShipId = -1;
        dragMoved = false;
    }

    private boolean dropSetupShip(int id, float x, float y) {
        if (!boardRect.contains(x, y)) return false;
        float cell = boardRect.width() / 10f;
        int startX = Math.round((x - boardRect.left) / cell - (setupHorizontal[id] ? lengths[id] / 2f : 0.5f));
        int startY = Math.round((y - boardRect.top) / cell - (setupHorizontal[id] ? 0.5f : lengths[id] / 2f));
        if (canPlaceSetupShip(id, startX, startY, setupHorizontal[id])) {
            setupPlaced[id] = true;
            setupX[id] = startX;
            setupY[id] = startY;
            return true;
        }
        return false;
    }

    private int findShipAtBoardPoint(float x, float y) {
        if (!boardRect.contains(x, y)) return -1;
        for (int id = lengths.length - 1; id >= 0; id--) {
            if (!setupPlaced[id]) continue;
            if (getPlacedShipRect(id).contains(x, y)) return id;
        }
        return -1;
    }

    private int findShipAtDockPoint(float x, float y) {
        for (int id = lengths.length - 1; id >= 0; id--) if (dockShipRects[id].contains(x, y)) return id;
        return -1;
    }

    private RectF getPlacedShipRect(int id) {
        float cell = boardRect.width() / 10f;
        float left = boardRect.left + setupX[id] * cell;
        float top = boardRect.top + setupY[id] * cell;
        float width = setupHorizontal[id] ? lengths[id] * cell : cell;
        float height = setupHorizontal[id] ? cell : lengths[id] * cell;
        return new RectF(left, top, left + width, top + height);
    }

    private boolean canPlaceSetupShip(int shipId, int sx, int sy, boolean horizontal) {
        int len = lengths[shipId];
        int endX = sx + (horizontal ? len - 1 : 0);
        int endY = sy + (horizontal ? 0 : len - 1);
        if (sx < 0 || sy < 0 || endX >= 10 || endY >= 10) return false;

        for (int id = 0; id < lengths.length; id++) {
            if (id == shipId || !setupPlaced[id]) continue;
            int ox = setupX[id];
            int oy = setupY[id];
            int olen = lengths[id];
            boolean oh = setupHorizontal[id];
            for (int a = 0; a < len; a++) {
                int px = sx + (horizontal ? a : 0);
                int py = sy + (horizontal ? 0 : a);
                for (int b = 0; b < olen; b++) {
                    int qx = ox + (oh ? b : 0);
                    int qy = oy + (oh ? 0 : b);
                    if (Math.abs(px - qx) <= 1 && Math.abs(py - qy) <= 1) return false;
                }
            }
        }
        return true;
    }

    private boolean allShipsPlaced() {
        for (boolean placed : setupPlaced) if (!placed) return false;
        return true;
    }

    private void startGameFromPlacement() {
        List<BattleshipGame.ShipPlacement> placements = new ArrayList<>();
        for (int id = 0; id < lengths.length; id++) {
            placements.add(new BattleshipGame.ShipPlacement(id, lengths[id], setupX[id], setupY[id], setupHorizontal[id]));
        }
        game.newGameWithPlayerPlacements(placements);
        placementMode = false;
        showEnemy = true;
        aiBusy = false;
        message = "Ваш ход — стреляйте по полю врага";
        vibrate(18);
        invalidate();
    }

    private void autoArrangeSetup() {
        resetPlacementArrays();
        int[][] preset = {
                {0, 0, 0, 1},
                {1, 2, 2, 0},
                {2, 5, 0, 0},
                {3, 9, 0, 1},
                {4, 0, 4, 0},
                {5, 7, 4, 1},
                {6, 3, 6, 0},
                {7, 5, 7, 0},
                {8, 9, 6, 1},
                {9, 1, 9, 0}
        };
        for (int[] s : preset) {
            int id = s[0];
            setupX[id] = s[1];
            setupY[id] = s[2];
            setupHorizontal[id] = s[3] == 1;
            setupPlaced[id] = true;
        }
        message = "Авторасстановка готова. Можно перетаскивать и поворачивать.";
        invalidate();
    }

    private void resetPlacementArrays() {
        for (int i = 0; i < lengths.length; i++) {
            setupPlaced[i] = false;
            setupHorizontal[i] = true;
            setupX[i] = 0;
            setupY[i] = 0;
        }
        draggingShipId = -1;
    }

    private void resetToPlacement() {
        game.newGame();
        placementMode = true;
        showEnemy = false;
        aiBusy = false;
        resetPlacementArrays();
        message = "Расставьте свои корабли: перетаскивайте, тап — поворот";
        invalidate();
    }

    private void enterPlacementFromCurrentBoard() {
        resetPlacementArrays();
        BattleshipGame.Board board = game.getPlayer();
        for (int id = 0; id < lengths.length; id++) {
            outer:
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    if (board.shipIdAt(x, y) == id) {
                        boolean horizontal = (x + 1 < 10 && board.shipIdAt(x + 1, y) == id);
                        setupPlaced[id] = true;
                        setupHorizontal[id] = horizontal;
                        setupX[id] = x;
                        setupY[id] = y;
                        break outer;
                    }
                }
            }
        }
        placementMode = true;
        showEnemy = false;
        message = "Измените расстановку и нажмите «Готово — начать игру»";
        invalidate();
    }

    private void runAiTurn() {
        BattleshipGame.AiShot s = game.aiShoot();
        if (s == null) {
            aiBusy = false;
            invalidate();
            return;
        }
        vibrate(s.result == BattleshipGame.ShotResult.MISS ? 16 : 40);
        if (game.isGameOver()) {
            aiBusy = false;
            showEnemy = false;
            message = "Поражение. Ваш флот уничтожен.";
            invalidate();
            return;
        }
        if (s.result == BattleshipGame.ShotResult.MISS) {
            aiBusy = false;
            showEnemy = true;
            message = "Компьютер промахнулся. Ваш ход.";
            invalidate();
        } else {
            showEnemy = false;
            message = s.result == BattleshipGame.ShotResult.SUNK ? "Компьютер потопил корабль…" : "Компьютер попал…";
            invalidate();
            postDelayed(this::runAiTurn, 520);
        }
    }

    private boolean noPlayerShotsReceived() {
        BattleshipGame.Board board = game.getPlayer();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) if (board.wasShot(x, y)) return false;
        }
        return true;
    }

    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }
}
