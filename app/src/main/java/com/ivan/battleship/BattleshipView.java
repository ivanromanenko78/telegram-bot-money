package com.ivan.battleship;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BattleshipView extends View implements TextToSpeech.OnInitListener {
    private final BattleshipGame game = new BattleshipGame();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final MusicEngine musicEngine = new MusicEngine();
    private final ProgressManager progress;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean soundEnabled = true;
    private boolean hintsEnabled = false;
    private int selectedTrack = 0;
    private static final String[] MUSIC_NAMES = {
            "Океан", "Сонар", "Шторм", "Бой", "Спокойствие"
    };

    private boolean showEnemy = false;
    private boolean placementMode = true;
    private String message = "Расставьте свои корабли: перетаскивайте, тап — поворот";

    private final RectF boardRect = new RectF();
    private final RectF newGameRect = new RectF();
    private final RectF toggleRect = new RectF();
    private final RectF hintRect = new RectF();
    private final RectF bottomActionRect = new RectF();
    private final RectF playerFleetRect = new RectF();
    private final RectF enemyFleetRect = new RectF();
    private final RectF dockRect = new RectF();
    private final RectF miniBoardRect = new RectF();
    private final RectF endNewGameRect = new RectF();
    private final RectF endExitRect = new RectF();

    private boolean aiBusy = false;
    private boolean resultRecorded = false;
    private int lastAwardedPoints = 0;

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
        progress = new ProgressManager(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        setBackgroundColor(bg);
        setFocusable(true);
        for (int i = 0; i < lengths.length; i++) {
            setupHorizontal[i] = true;
            dockShipRects[i] = new RectF();
        }

        tts = new TextToSpeech(context, this);
        musicEngine.setTrack(selectedTrack);
        musicEngine.setVolume(0.12f);
        musicEngine.start();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int result = tts.setLanguage(new Locale("ru", "RU"));
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
            chooseSoftFemaleRussianVoice();
            tts.setSpeechRate(0.80f);
            tts.setPitch(1.02f);
        }
    }

    private void chooseSoftFemaleRussianVoice() {
        if (tts == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;

            Voice best = null;
            int bestScore = Integer.MIN_VALUE;

            for (Voice voice : voices) {
                Locale locale = voice.getLocale();
                if (locale == null || !"ru".equalsIgnoreCase(locale.getLanguage())) continue;

                String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.ROOT);
                int score = 0;
                if ("RU".equalsIgnoreCase(locale.getCountry())) score += 20;
                if (name.contains("female") || name.contains("woman") || name.contains("жен")) score += 100;
                if (name.contains("network") || name.contains("neural") || name.contains("wavenet")) score += 25;
                if (!voice.isNetworkConnectionRequired()) score += 5;

                if (score > bestScore) {
                    bestScore = score;
                    best = voice;
                }
            }

            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {}
    }

    public void release() {
        musicEngine.stop();
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
    }

    private void speak(String phrase) {
        if (soundEnabled && ttsReady && tts != null) {
            tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "battleship_voice");
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
        c.drawText(message, w / 2f, top + dp(50), p);

        drawProgressHeader(c, w, top + dp(67));

        float btnY = top + dp(82);
        if (placementMode) {
            float btnW = (w - margin * 2 - gap) / 2f;
            newGameRect.set(margin, btnY, margin + btnW, btnY + dp(44));
            toggleRect.set(margin + btnW + gap, btnY, w - margin, btnY + dp(44));
            hintRect.setEmpty();
            drawButton(c, newGameRect, "Новая игра", false);
            drawButton(c, toggleRect, "Авторасстановка", true);
        } else {
            float smallGap = dp(6);
            float btnW = (w - margin * 2 - smallGap * 2f) / 3f;
            newGameRect.set(margin, btnY, margin + btnW, btnY + dp(44));
            hintRect.set(newGameRect.right + smallGap, btnY,
                    newGameRect.right + smallGap + btnW, btnY + dp(44));
            toggleRect.set(hintRect.right + smallGap, btnY, w - margin, btnY + dp(44));

            drawButton(c, newGameRect, "Новая игра", false);
            drawButton(c, hintRect, hintsEnabled ? "Подсказки ✓" : "Подсказки ×", hintsEnabled);
            drawButton(c, toggleRect, soundEnabled ? "Музыка" : "Звук выкл", soundEnabled);
        }

        float labelY = btnY + dp(68);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(dp(15));
        p.setColor(placementMode ? ship : accent);
        p.setFakeBoldText(true);
        c.drawText(placementMode ? "РАССТАНОВКА ФЛОТА" : "ПОЛЕ ВРАГА", margin, labelY, p);
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
        float reservedBottom = placementMode ? dp(150) : dp(190);
        float side = Math.min(w - dp(56), getHeight() - gridTop - reservedBottom);
        side = Math.max(side, dp(240));
        float left = (w - side) / 2f;
        boardRect.set(left, gridTop, left + side, gridTop + side);

        if (placementMode) {
            drawBoardBackground(c);
            drawSetupShipsOnBoard(c);
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
            bottomActionRect.setEmpty();

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(13));
            p.setFakeBoldText(true);
            p.setColor(ship);
            c.drawText("МОЁ ПОЛЕ — АТАКИ КОМПЬЮТЕРА", w / 2f, bottomY + dp(14), p);
            p.setFakeBoldText(false);

            float miniSide = Math.min(dp(138), w * 0.34f);
            float miniTop = bottomY + dp(22);
            miniBoardRect.set((w - miniSide) / 2f, miniTop, (w + miniSide) / 2f, miniTop + miniSide);
            drawMiniPlayerBoard(c, miniBoardRect);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(11));
            p.setColor(0xFF7FA9BC);
            c.drawText("Огонь — попадание, точка — промах", w / 2f, miniBoardRect.bottom + dp(17), p);
        }

        if (placementMode && draggingShipId >= 0) {
            drawFloatingShip(c, draggingShipId, dragTouchX, dragTouchY);
        }

        if (!placementMode && game.isGameOver()) {
            drawEndOverlay(c, w, getHeight());
        }
    }

    private void drawProgressHeader(Canvas c, float w, float y) {
        String status;
        if (progress.isMaxLevel()) {
            status = "Ур." + progress.getLevelNumber() + " " + progress.getLevelName()
                    + "  •  " + progress.getPoints() + " оч."
                    + "  •  В " + progress.getWins() + " / П " + progress.getLosses()
                    + "  •  MAX";
        } else {
            status = "Ур." + progress.getLevelNumber() + " " + progress.getLevelName()
                    + "  •  " + progress.getPoints() + " оч."
                    + "  •  В " + progress.getWins() + " / П " + progress.getLosses()
                    + "  •  до след.: " + progress.getPointsToNextLevel();
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(false);
        p.setTextSize(dp(10.5f));
        p.setColor(0xFF8DB8CB);
        c.drawText(status, w / 2f, y, p);

        float margin = dp(24);
        float barTop = y + dp(5);
        float barHeight = dp(3.5f);
        RectF bgBar = new RectF(margin, barTop, w - margin, barTop + barHeight);
        p.setColor(0xFF16344B);
        c.drawRoundRect(bgBar, barHeight / 2f, barHeight / 2f, p);

        float progressWidth = bgBar.width() * progress.getLevelProgress();
        if (progressWidth > 0f) {
            RectF fillBar = new RectF(bgBar.left, bgBar.top, bgBar.left + progressWidth, bgBar.bottom);
            p.setColor(accent);
            c.drawRoundRect(fillBar, barHeight / 2f, barHeight / 2f, p);
        }
    }

    private void drawEndOverlay(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC03111F);
        c.drawRect(0, 0, w, h, p);

        String result = game.playerWon() ? "ПОБЕДА" : "ПОРАЖЕНИЕ";
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(dp(48));
        p.setColor(game.playerWon() ? accent : hit);
        c.drawText(result, w / 2f, h / 2f - dp(46), p);

        float margin = dp(34);
        float gap = dp(12);
        float buttonW = (w - margin * 2 - gap) / 2f;
        float top = h / 2f + dp(4);
        endNewGameRect.set(margin, top, margin + buttonW, top + dp(54));
        endExitRect.set(margin + buttonW + gap, top, w - margin, top + dp(54));

        drawButton(c, endNewGameRect, "Новая игра", true);
        drawButton(c, endExitRect, "Выйти", false);

        p.setTextSize(dp(16));
        p.setColor(text);
        p.setFakeBoldText(true);
        c.drawText("+" + lastAwardedPoints + " очков", w / 2f, top - dp(18), p);

        p.setTextSize(dp(13));
        p.setColor(0xFFB8D8E8);
        p.setFakeBoldText(false);
        c.drawText("Уровень " + progress.getLevelNumber() + " — " + progress.getLevelName(),
                w / 2f, top + dp(82), p);
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
        if (enemyBoard && hintsEnabled && !game.isGameOver()) {
            drawHints(c, board);
        }
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

    private void drawHints(Canvas c, BattleshipGame.Board board) {
        float cell = boardRect.width() / 10f;
        boolean hasOpenHit = false;

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                if (!board.wasShot(x, y) || !board.hasShip(x, y)) continue;
                int shipId = board.shipIdAt(x, y);
                if (shipId >= 0 && !board.isSunk(shipId)) {
                    hasOpenHit = true;
                }
            }
        }

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                if (board.wasShot(x, y)) continue;

                boolean recommended;
                if (hasOpenHit) {
                    recommended = isNextToOpenHit(board, x, y);
                } else {
                    recommended = ((x + y) & 1) == 0;
                }

                if (!recommended) continue;

                float l = boardRect.left + x * cell;
                float t = boardRect.top + y * cell;
                float inset = cell * .13f;
                RectF rr = new RectF(l + inset, t + inset, l + cell - inset, t + cell - inset);

                p.setStyle(Paint.Style.FILL);
                p.setColor(hasOpenHit ? 0x55FFD166 : 0x244CE7D3);
                c.drawRoundRect(rr, cell * .18f, cell * .18f, p);

                stroke.setStrokeWidth(hasOpenHit ? dp(2.0f) : dp(1.1f));
                stroke.setColor(hasOpenHit ? 0xFFFFD166 : 0xAA65E7D0);
                c.drawRoundRect(rr, cell * .18f, cell * .18f, stroke);
            }
        }
    }

    private boolean isNextToOpenHit(BattleshipGame.Board board, int x, int y) {
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : directions) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx < 0 || nx >= 10 || ny < 0 || ny >= 10) continue;
            if (!board.wasShot(nx, ny) || !board.hasShip(nx, ny)) continue;

            int shipId = board.shipIdAt(nx, ny);
            if (shipId >= 0 && !board.isSunk(shipId)) return true;
        }
        return false;
    }

    private void drawMiniPlayerBoard(Canvas c, RectF rect) {
        BattleshipGame.Board board = game.getPlayer();
        float cell = rect.width() / 10f;

        p.setStyle(Paint.Style.FILL);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                float l = rect.left + x * cell;
                float t = rect.top + y * cell;
                RectF r = new RectF(l, t, l + cell, t + cell);

                p.setColor(((x + y) & 1) == 0 ? water : water2);
                c.drawRect(r, p);

                if (board.hasShip(x, y)) {
                    p.setColor(ship);
                    RectF sr = new RectF(l + cell * .10f, t + cell * .10f, l + cell * .90f, t + cell * .90f);
                    c.drawRoundRect(sr, cell * .13f, cell * .13f, p);
                }

                if (board.wasShot(x, y)) {
                    if (board.hasShip(x, y)) {
                        drawFlame(c, l + cell * .5f, t + cell * .54f, cell * .34f);
                    } else {
                        p.setColor(miss);
                        c.drawCircle(l + cell * .5f, t + cell * .5f, Math.max(dp(1.2f), cell * .10f), p);
                    }
                }

                stroke.setColor(line);
                stroke.setStrokeWidth(Math.max(dp(.45f), cell * .035f));
                c.drawRect(r, stroke);
            }
        }

        stroke.setColor(0xFFA7D5E7);
        stroke.setStrokeWidth(dp(1.5f));
        c.drawRect(rect, stroke);
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

        if (game.isGameOver()) {
            if (endNewGameRect.contains(x, y)) {
                resetToPlacement();
                return true;
            }
            if (endExitRect.contains(x, y)) {
                release();
                Context context = getContext();
                if (context instanceof Activity) {
                    ((Activity) context).finish();
                }
                return true;
            }
            return true;
        }

        if (newGameRect.contains(x, y)) {
            resetToPlacement();
            return true;
        }
        if (hintRect.contains(x, y)) {
            hintsEnabled = !hintsEnabled;
            message = hintsEnabled ? "Подсказки включены." : "Подсказки выключены.";
            vibrate(12);
            invalidate();
            return true;
        }
        if (toggleRect.contains(x, y)) {
            showMusicDialog();
            return true;
        }

        if (aiBusy || !game.isPlayerTurn() || game.isGameOver()) return true;
        if (boardRect.contains(x, y)) {
            int cx = (int) ((x - boardRect.left) / (boardRect.width() / 10f));
            int cy = (int) ((y - boardRect.top) / (boardRect.height() / 10f));
            if (cx < 0 || cx >= 10 || cy < 0 || cy >= 10) return true;
            BattleshipGame.ShotResult r = game.playerShoot(cx, cy);
            if (r == BattleshipGame.ShotResult.ALREADY) return true;
            vibrate(r == BattleshipGame.ShotResult.MISS ? 18 : 45);

            if (game.isGameOver()) {
                message = "ПОБЕДА";
                recordBattleResult(true);
                speak("Убил. Победа.");
                invalidate();
                return true;
            }

            if (r == BattleshipGame.ShotResult.MISS) {
                speak("Мимо");
                message = "Мимо. Ход компьютера…";
                aiBusy = true;
                invalidate();
                postDelayed(this::runAiTurn, 420);
            } else if (r == BattleshipGame.ShotResult.SUNK) {
                speak("Убил");
                message = "Убил! Стреляйте ещё.";
                invalidate();
            } else {
                speak("Ранил");
                message = "Ранил! Стреляйте ещё.";
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
        if (!game.newGameWithPlayerPlacements(placements)) {
            placementMode = true;
            showEnemy = false;
            message = "Ошибка расстановки: корабль выходит за поле или касается другого.";
            vibrate(40);
            invalidate();
            return;
        }
        placementMode = false;
        showEnemy = true;
        aiBusy = false;
        resultRecorded = false;
        lastAwardedPoints = 0;
        message = "Ваш ход — стреляйте по полю врага";
        hintsEnabled = false;
        vibrate(18);
        invalidate();
    }

    private void autoArrangeSetup() {
        resetPlacementArrays();
        int[][] preset = {
                {0, 0, 0, 1},
                {1, 2, 2, 0},
                {2, 5, 0, 0},
                {3, 8, 0, 1},
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
        message = "Авторасстановка готова. Двигайте корабли пальцем, тап — поворот.";
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
        resultRecorded = false;
        lastAwardedPoints = 0;
        hintsEnabled = false;
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
            showEnemy = true;
            message = "ПОРАЖЕНИЕ";
            recordBattleResult(false);
            speak("Поражение");
            invalidate();
            return;
        }

        if (s.result == BattleshipGame.ShotResult.MISS) {
            aiBusy = false;
            showEnemy = true;
            message = "Компьютер: мимо. Ваш ход.";
            invalidate();
        } else if (s.result == BattleshipGame.ShotResult.SUNK) {
            showEnemy = true;
            message = "Компьютер: убил.";
            invalidate();
            postDelayed(this::runAiTurn, 620);
        } else {
            showEnemy = true;
            message = "Компьютер: ранил.";
            invalidate();
            postDelayed(this::runAiTurn, 620);
        }
    }

    private void recordBattleResult(boolean win) {
        if (resultRecorded) return;
        resultRecorded = true;
        lastAwardedPoints = win ? progress.recordWin() : progress.recordLoss();
    }

    private void showMusicDialog() {
        String[] items = {
                "Океан", "Сонар", "Шторм", "Бой", "Спокойствие", "Без звука"
        };
        int checked = soundEnabled ? selectedTrack : 5;

        new AlertDialog.Builder(getContext())
                .setTitle("Музыка и звук")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    if (which == 5) {
                        soundEnabled = false;
                        musicEngine.setVolume(0f);
                        if (tts != null) {
                            try { tts.stop(); } catch (Exception ignored) {}
                        }
                    } else {
                        selectedTrack = which;
                        soundEnabled = true;
                        musicEngine.setTrack(which);
                        musicEngine.setVolume(0.12f);
                    }
                    invalidate();
                    dialog.dismiss();
                })
                .setNegativeButton("Закрыть", null)
                .show();
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
