package com.krld.intentplayground;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
    }

    private void requestPermissions() {
        // Here, thisActivity is the current activity
        List<String> perms = new ArrayList<>();
        checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE, perms);
        checkPerm(Manifest.permission.CAMERA, perms);
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    perms.toArray(new String[perms.size()]),
                    1);
        }
    }

    private void checkPerm(String permission, List<String> perms) {
        if (ContextCompat.checkSelfPermission(this,
                permission)
                != PackageManager.PERMISSION_GRANTED) {
            perms.add(permission);
        }
    }
}
