package com.ivan.battleship;

import android.content.Context;
import android.content.SharedPreferences;

public class ProgressManager {
    public static final int WIN_POINTS = 100;
    public static final int LOSS_POINTS = 20;

    private static final int[] LEVEL_POINTS = {
            0, 300, 800, 1500, 2500, 4000, 6000, 9000
    };

    private static final String[] LEVEL_NAMES = {
            "Матрос",
            "Старшина",
            "Боцман",
            "Лейтенант",
            "Капитан",
            "Командор",
            "Контр-адмирал",
            "Адмирал"
    };

    private final SharedPreferences prefs;

    public ProgressManager(Context context) {
        prefs = context.getSharedPreferences("battleship_progress", Context.MODE_PRIVATE);
    }

    public int getPoints() {
        return prefs.getInt("points", 0);
    }

    public int getWins() {
        return prefs.getInt("wins", 0);
    }

    public int getLosses() {
        return prefs.getInt("losses", 0);
    }

    public int recordWin() {
        int newPoints = getPoints() + WIN_POINTS;
        prefs.edit()
                .putInt("points", newPoints)
                .putInt("wins", getWins() + 1)
                .apply();
        return WIN_POINTS;
    }

    public int recordLoss() {
        int newPoints = getPoints() + LOSS_POINTS;
        prefs.edit()
                .putInt("points", newPoints)
                .putInt("losses", getLosses() + 1)
                .apply();
        return LOSS_POINTS;
    }

    public int getLevelIndex() {
        int points = getPoints();
        int level = 0;
        for (int i = 0; i < LEVEL_POINTS.length; i++) {
            if (points >= LEVEL_POINTS[i]) level = i;
            else break;
        }
        return level;
    }

    public int getLevelNumber() {
        return getLevelIndex() + 1;
    }

    public String getLevelName() {
        return LEVEL_NAMES[getLevelIndex()];
    }

    public boolean isMaxLevel() {
        return getLevelIndex() == LEVEL_POINTS.length - 1;
    }

    public int getNextLevelThreshold() {
        int index = getLevelIndex();
        if (index >= LEVEL_POINTS.length - 1) return LEVEL_POINTS[LEVEL_POINTS.length - 1];
        return LEVEL_POINTS[index + 1];
    }

    public int getPointsToNextLevel() {
        if (isMaxLevel()) return 0;
        return Math.max(0, getNextLevelThreshold() - getPoints());
    }

    public float getLevelProgress() {
        int index = getLevelIndex();
        if (index >= LEVEL_POINTS.length - 1) return 1f;
        int start = LEVEL_POINTS[index];
        int end = LEVEL_POINTS[index + 1];
        return Math.max(0f, Math.min(1f, (getPoints() - start) / (float)(end - start)));
    }
}
