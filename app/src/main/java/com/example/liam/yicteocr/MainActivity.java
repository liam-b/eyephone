package com.example.liam.yicteocr;

import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import static android.util.Log.*;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Handler handler = new Handler();
    private long delay = 3000;

    public void afterCreated () {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);

        Log.i(TAG, "hi");

        Runnable runner = new Runnable() {
            public void run () {
                Log.i(TAG, "looping!");
                handler.postDelayed(this, delay);
            }
        };

        handler.postDelayed(runner, delay);
    }
}