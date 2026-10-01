package com.ivan.battleship;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private BattleshipView battleshipView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071B2F);
        getWindow().setNavigationBarColor(0xFF071B2F);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        battleshipView = new BattleshipView(this);
        setContentView(battleshipView);
    }

    @Override
    protected void onDestroy() {
        if (battleshipView != null) {
            battleshipView.release();
        }
        super.onDestroy();
    }
}
