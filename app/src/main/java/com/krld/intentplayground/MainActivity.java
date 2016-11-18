package com.krld.intentplayground;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.krld.intentplayground.services.PhotosCatcherJobService;
import com.krld.intentplayground.services.PhotosUploaderJobService;

public class MainActivity extends AppCompatActivity {

    @SuppressWarnings("unused")
    private static final String LOG_TAG = "MainActivity";

    public static final int REQ_PHOTOS_PERM = 1;
    private SharedPreferences sharedPrefs;

    private DropboxAPI<AndroidAuthSession> dbApi;

    private boolean waitingDropBoxAuth;
    private boolean dialogIsPop;

    private int queuedCount;
    private int syncedCount;

    private boolean isSyncing;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.ACTION_COUNTERS_UPDATED)) {
                queuedCount = intent.getIntExtra(Constants.EXTRA_QUEUED_COUNT, 0);
                syncedCount = intent.getIntExtra(Constants.EXTRA_SYNCED_COUNT, 0);
                updateCounts();
                updateState();
            } else if (intent.getAction().equals(Constants.ACTION_SYNCING_STATE)) {
                isSyncing = intent.getBooleanExtra(Constants.EXTRA_SYNCING_IN_PROGRESS, false);
                updateState();
            }
        }
    };

    private TextView queuedTextView;
    private TextView syncedTextView;
    private TextView stateTextView;
    private LocalBroadcastManager broadcastManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_a);
        sharedPrefs = MyApp.getInstance().getSharedPrefs();

        String accessToken = sharedPrefs.getString(Constants.DROP_BOX_ACCESS_TOKEN, null);

        if (accessToken == null) {
            showAuthDropBoxDialog();
        } else {
            schedulePhotoJobService();
        }

        queuedTextView = (TextView) findViewById(R.id.queued);
        syncedTextView = (TextView) findViewById(R.id.synced);
        stateTextView = (TextView) findViewById(R.id.state);

        queuedCount = PhotosUploaderJobService.getQueuedCount();
        syncedCount = PhotosUploaderJobService.getSyncedCount();
        updateCounts();
        updateState();

        broadcastManager = LocalBroadcastManager.getInstance(this);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_COUNTERS_UPDATED);
        intentFilter.addAction(Constants.ACTION_SYNCING_STATE);
        broadcastManager.registerReceiver(broadcastReceiver, intentFilter);
    }


    private void updateCounts() {
        queuedTextView.setText(getString(R.string.queued_photos, queuedCount));
        syncedTextView.setText(getString(R.string.synced_photos, syncedCount));
    }

    private void updateState() {
        stateTextView.setText(isSyncing ? R.string.syncing : queuedCount == 0 ? R.string.all_photos_are_synced : R.string.waiting);
    }

    private void requestDropBoxAuth() {
        AppKeyPair appKeys = new AppKeyPair(BuildConfig.DROP_BOX_API_KEY, BuildConfig.DROP_BOX_API_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        dbApi = new DropboxAPI<>(session);

        // MyActivity below should be your activity class name
        dbApi.getSession().startOAuth2Authentication(MainActivity.this);
        waitingDropBoxAuth = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingDropBoxAuth) {
            waitingDropBoxAuth = false;
            if (dbApi.getSession().authenticationSuccessful()) {
                try {
                    dbApi.getSession().finishAuthentication();
                    sharedPrefs.edit().putString(Constants.DROP_BOX_ACCESS_TOKEN, dbApi.getSession().getOAuth2AccessToken()).apply();
                    showToast(R.string.success_dropbox_authentication);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    showAuthDropBoxDialog();
                    return;
                }
                schedulePhotoJobService();
            } else {
                showAuthDropBoxDialog();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        broadcastManager.unregisterReceiver(broadcastReceiver);
    }

    private void showAuthDropBoxDialog() {
        if (dialogIsPop) {
            return;
        }
        dialogIsPop = true;
        new AlertDialog.Builder(this)
                .setMessage(R.string.authentication_dropbox)
                .setCancelable(false)
                .setPositiveButton(R.string.authenticate,
                        (dialog, which) -> {
                            dialogIsPop = false;
                            requestDropBoxAuth();
                        })
                .show();
    }

    private void schedulePhotoJobService() {
        PhotosUploaderJobService.scheduleJob(this);

        if (!PhotosCatcherJobService.isScheduled(this)) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                showRequestStoragePermissionDialog();
            } else {
                PhotosCatcherJobService.scheduleJob(MainActivity.this);
            }
        }
    }

    private void showRequestStoragePermissionDialog() {
        if (dialogIsPop) {
            return;
        }
        dialogIsPop = true;
        new AlertDialog.Builder(this)
                .setMessage(R.string.need_read_storage_permission)
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> {
                            dialogIsPop = false;
                            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PHOTOS_PERM);
                        }
                )
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQ_PHOTOS_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                PhotosCatcherJobService.scheduleJob(MainActivity.this);
            } else {
                showRequestStoragePermissionDialog();
            }
        }
    }

    private void showToast(int msgResId) {
        Toast.makeText(this, msgResId, Toast.LENGTH_LONG).show();
    }
}
