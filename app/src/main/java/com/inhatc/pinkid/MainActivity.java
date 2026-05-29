package com.inhatc.pinkid;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnParent = findViewById(R.id.btn_parent);
        Button btnChild = findViewById(R.id.btn_child);

        btnParent.setOnClickListener(v ->
                startActivity(new Intent(this, ParentMapActivity.class)));

        btnChild.setOnClickListener(v ->
                startActivity(new Intent(this, ChildActivity.class)));
    }
}
