package com.ivan.battleship;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

public class BattleshipView extends View {
    private final BattleshipGame game = new BattleshipGame();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean showEnemy = true;
    private String message = "Ваш ход — стреляйте по полю врага";
    private final RectF boardRect = new RectF();
    private final RectF newGameRect = new RectF();
    private final RectF toggleRect = new RectF();
    private final RectF rearrangeRect = new RectF();
    private boolean aiBusy = false;

    private final int bg = 0xFF071B2F;
    private final int panel = 0xFF0D2B47;
    private final int water = 0xFF0F5D82;
    private final int water2 = 0xFF126B94;
    private final int line = 0xFF77B8D1;
    private final int ship = 0xFF84D8E7;
    private final int hit = 0xFFFF6B5F;
    private final int miss = 0xFFE4EEF2;
    private final int text = 0xFFF5FBFF;
    private final int accent = 0xFFFFC857;

    public BattleshipView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        setBackgroundColor(bg);
        setFocusable(true);
    }

    private float dp(float v) { return v * density; }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float top = dp(24);

        p.setColor(text);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(28));
        p.setFakeBoldText(true);
        c.drawText("МОРСКОЙ БОЙ", w / 2f, top + dp(28), p);
        p.setFakeBoldText(false);

        p.setColor(0xFFB8D8E8);
        p.setTextSize(dp(14));
        c.drawText(message, w / 2f, top + dp(57), p);

        float btnY = top + dp(76);
        float gap = dp(8);
        float margin = dp(16);
        float btnW = (w - margin * 2 - gap) / 2f;
        newGameRect.set(margin, btnY, margin + btnW, btnY + dp(44));
        toggleRect.set(margin + btnW + gap, btnY, w - margin, btnY + dp(44));
        drawButton(c, newGameRect, "Новая игра", false);
        drawButton(c, toggleRect, showEnemy ? "Показать моё поле" : "Поле врага", true);

        float labelY = btnY + dp(72);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(dp(15));
        p.setColor(showEnemy ? accent : ship);
        p.setFakeBoldText(true);
        c.drawText(showEnemy ? "ПОЛЕ ВРАГА" : "МОЁ ПОЛЕ", margin, labelY, p);
        p.setFakeBoldText(false);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xFFB8D8E8);
        String score = "Ваши: " + game.getPlayer().shipsLeft() + "   Враг: " + game.getEnemy().shipsLeft();
        c.drawText(score, w - margin, labelY, p);

        float gridTop = labelY + dp(18);
        float side = Math.min(w - dp(52), getHeight() - gridTop - dp(130));
        side = Math.max(side, dp(260));
        float left = (w - side) / 2f + dp(10);
        boardRect.set(left, gridTop + dp(12), left + side - dp(10), gridTop + dp(12) + side - dp(10));
        drawBoard(c, showEnemy ? game.getEnemy() : game.getPlayer(), showEnemy);

        float bottomY = boardRect.bottom + dp(18);
        rearrangeRect.set(margin, bottomY, w - margin, bottomY + dp(46));
        boolean canRearrange = !showEnemy && game.isPlayerTurn() && !game.isGameOver();
        drawButton(c, rearrangeRect, "Переставить корабли", canRearrange);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(12));
        p.setColor(0xFF7FA9BC);
        c.drawText("Корабли не соприкасаются. После попадания ход продолжается.", w / 2f, bottomY + dp(72), p);
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

    private void drawBoard(Canvas c, BattleshipGame.Board board, boolean enemyBoard) {
        float cell = boardRect.width() / 10f;
        p.setTextSize(Math.max(dp(10), cell * 0.30f));
        p.setColor(0xFF8DB8CB);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 10; i++) {
            char ch = (char) ('A' + i);
            c.drawText(String.valueOf(ch), boardRect.left + cell * (i + .5f), boardRect.top - dp(5), p);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(String.valueOf(i + 1), boardRect.left - dp(6), boardRect.top + cell * (i + .5f) - (p.ascent()+p.descent())/2, p);
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

                boolean visibleShip = board.hasShip(x,y) && (!enemyBoard || (game.isGameOver() && !game.playerWon()));
                if (visibleShip) {
                    p.setColor(ship);
                    RectF sr = new RectF(l + cell*.13f, t + cell*.13f, l + cell*.87f, t + cell*.87f);
                    c.drawRoundRect(sr, cell*.16f, cell*.16f, p);
                }

                if (board.wasShot(x,y)) {
                    if (board.hasShip(x,y)) {
                        p.setColor(hit);
                        p.setStrokeWidth(Math.max(dp(2), cell * .08f));
                        p.setStyle(Paint.Style.STROKE);
                        c.drawLine(l + cell*.27f, t + cell*.27f, l + cell*.73f, t + cell*.73f, p);
                        c.drawLine(l + cell*.73f, t + cell*.27f, l + cell*.27f, t + cell*.73f, p);
                        p.setStyle(Paint.Style.FILL);
                    } else {
                        p.setColor(miss);
                        c.drawCircle(l + cell*.5f, t + cell*.5f, Math.max(dp(2.1f), cell*.08f), p);
                    }
                }
                stroke.setColor(line);
                stroke.setStrokeWidth(dp(.7f));
                c.drawRect(r, stroke);
            }
        }
        stroke.setColor(0xFFA7D5E7);
        stroke.setStrokeWidth(dp(2));
        c.drawRect(boardRect, stroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();

        if (newGameRect.contains(x, y)) {
            game.newGame();
            aiBusy = false;
            showEnemy = true;
            message = "Ваш ход — стреляйте по полю врага";
            vibrate(20);
            invalidate();
            return true;
        }
        if (toggleRect.contains(x, y)) {
            showEnemy = !showEnemy;
            invalidate();
            return true;
        }
        if (rearrangeRect.contains(x, y) && !showEnemy && game.isPlayerTurn() && !game.isGameOver()) {
            game.rearrangePlayer();
            message = "Ваш флот переставлен";
            vibrate(15);
            invalidate();
            return true;
        }

        if (!showEnemy || aiBusy || !game.isPlayerTurn() || game.isGameOver()) return true;
        if (boardRect.contains(x, y)) {
            int cx = (int)((x - boardRect.left) / (boardRect.width()/10f));
            int cy = (int)((y - boardRect.top) / (boardRect.height()/10f));
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

    private void vibrate(long ms) {
        Vibrator v = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else
            v.vibrate(ms);
    }
}
