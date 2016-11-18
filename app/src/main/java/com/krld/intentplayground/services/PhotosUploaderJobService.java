package com.krld.intentplayground.services;

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.gson.Gson;
import com.krld.intentplayground.BuildConfig;
import com.krld.intentplayground.Constants;
import com.krld.intentplayground.MyApp;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;


public class PhotosUploaderJobService extends JobService {

    private static final String LOG_TAG = "PhotosUploaderService";

    // A pre-built JobInfo we use for scheduling our job.
    private static final JobInfo JOB_INFO;

    private static final int MAX_PHOTOS_PER_ATTEMPT = 15;
    private static final int MAX_FAILS_PER_ATTEMPT = 2;

    private static Gson gson = new Gson();

    static {
        ComponentName serviceName = new ComponentName("com.krld.intentplayground", PhotosUploaderJobService.class.getName());
        JOB_INFO = new JobInfo.Builder(Constants.PHOTOS_UPLOAD_JOB_ID, serviceName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NOT_ROAMING)
                /*.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) uncomment for decrease battery consumption
                .setRequiresCharging(true)*/
                .build();
    }

    private DropboxAPI dbApi;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(LOG_TAG, "JOB STARTED!");

        MyApp.getStageHandler().post(() -> {

            List<String> queuedPhotos = getQueuedPhotos();
            if (queuedPhotos.isEmpty()) {
                Log.i(LOG_TAG, "no queued photos");
                MyApp.getMainHandler().post(() -> jobFinished(params, false));
                return;
            }

            MyApp.getWorkerHandler().post(() -> {
                int successCount = 0;
                int failCount = 0;

                broadcastIsSyncing(true);
                for (String queuedPhoto : queuedPhotos) {
                    File file = new File(queuedPhoto);
                    boolean shouldIncrementSynced = false;
                    boolean fileExists = file.exists();
                    if (fileExists) {
                        try {
                            FileInputStream stream = new FileInputStream(file);
                            Log.i(LOG_TAG, "Started upload file " + queuedPhoto);
                            DropboxAPI.Entry response = getDbApi().putFile(file.getName(), stream, file.length(), null, null);
                            Log.i(LOG_TAG, "The uploaded file's rev is: " + response.rev);
                            successCount++;
                            shouldIncrementSynced = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            failCount++;
                        }
                    }
                    removePhotoFromQueueAndBroadcast(queuedPhoto, shouldIncrementSynced, fileExists);

                    if (successCount == MAX_PHOTOS_PER_ATTEMPT || failCount == MAX_FAILS_PER_ATTEMPT) {
                        break;
                    }
                }
                broadcastIsSyncing(false);
                Log.i(LOG_TAG, "Successfully upload " + successCount + " files");

                MyApp.getStageHandler().post(() -> {
                    int queuedCount = getQueuedCount();
                    MyApp.getMainHandler().post(() -> jobFinished(params, queuedCount > 0));
                });
            });
        });
        return true;
    }

    private void removePhotoFromQueueAndBroadcast(String queuedPhoto, boolean successUpload, boolean shouldRemovePhoto) {
        if (Looper.myLooper() != MyApp.getStageHandler().getLooper()) {
            MyApp.getStageHandler().post(() -> removePhotoFromQueueAndBroadcast(queuedPhoto, successUpload, shouldRemovePhoto));
            return;
        }
        if (shouldRemovePhoto) {
            List<String> queuedPhotos = getQueuedPhotos();
            if (queuedPhotos.remove(queuedPhoto)) {
                saveQueuedPhotos(queuedPhotos);
            }
        }

        int syncedCount = getSyncedCount();
        if (successUpload) {
            syncedCount += 1;
            saveSyncedCount(syncedCount);
        }

        broadcastCounts(this, getQueuedCount(), syncedCount);
    }

    private void broadcastIsSyncing(boolean isSyncing) {
        Intent update = new Intent(Constants.ACTION_SYNCING_STATE);
        update.putExtra(Constants.EXTRA_SYNCING_IN_PROGRESS, isSyncing);
        LocalBroadcastManager.getInstance(this).sendBroadcast(update);
    }

    private static void broadcastCounts(Context context, int queuedCount, int syncedCount) {
        Intent update = new Intent(Constants.ACTION_COUNTERS_UPDATED);
        update.putExtra(Constants.EXTRA_QUEUED_COUNT, queuedCount);
        update.putExtra(Constants.EXTRA_SYNCED_COUNT, syncedCount);
        LocalBroadcastManager.getInstance(context).sendBroadcast(update);
    }

    private DropboxAPI getDbApi() {
        if (dbApi == null) {
            AppKeyPair appKeys = new AppKeyPair(BuildConfig.DROP_BOX_API_KEY, BuildConfig.DROP_BOX_API_SECRET);

            String accessToken = MyApp.getInstance().getSharedPrefs().getString(Constants.DROP_BOX_ACCESS_TOKEN, null);
            if (accessToken == null) {
                throw new AssertionError("accessToken cannot be null");
            }

            AndroidAuthSession session = new AndroidAuthSession(appKeys, accessToken);
            dbApi = new DropboxAPI<>(session);
        }
        return dbApi;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(LOG_TAG, "JOB STOP!");
        return false;
    }

    public static void scheduleJob(Context context) {
        if (isScheduled(context)) {
            Log.i(LOG_TAG, "dont schedule job because already scheduled");
            return;
        }

        JobScheduler js = context.getSystemService(JobScheduler.class);
        js.schedule(JOB_INFO);
        Log.i(LOG_TAG, "JOB SCHEDULED!");
    }

    public static boolean isScheduled(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        List<JobInfo> jobs = js.getAllPendingJobs();
        //noinspection ConstantConditions
        if (jobs == null) {
            return false;
        }
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getId() == Constants.PHOTOS_UPLOAD_JOB_ID) {
                return true;
            }
        }
        return false;
    }

    public static void handle(List<String> newPhotos, Context context) {
        if (newPhotos == null || newPhotos.isEmpty() || context == null) {
            return;
        }
        MyApp.getStageHandler().post(() -> {
            List<String> queuedPhotos = getQueuedPhotos();
            queuedPhotos.addAll(newPhotos);
            broadcastCounts(context, queuedPhotos.size(), getSyncedCount());
            saveQueuedPhotos(queuedPhotos);
        });
        scheduleJob(context);
    }

    //TODO move data access methods to separate class?
    @SuppressLint("CommitPrefEdits")
    private static void saveQueuedPhotos(List<String> queuedPhotos) {
        MyApp.getInstance().getSharedPrefs().edit().putString(Constants.SP_QUEUED_PHOTOS, gson.toJson(queuedPhotos)).commit();
        MyApp.getInstance().getSharedPrefs().edit().putInt(Constants.SP_QUEUED_PHOTOS_COUNT, queuedPhotos.size()).commit();
    }

    public static int getQueuedCount() {
        return MyApp.getInstance().getSharedPrefs().getInt(Constants.SP_QUEUED_PHOTOS_COUNT, 0);
    }

    private static List<String> getQueuedPhotos() {
        SharedPreferences sharedPrefs = MyApp.getInstance().getSharedPrefs();
        String listAsString = sharedPrefs.getString(Constants.SP_QUEUED_PHOTOS, null);
        List<String> queuedPhotos;
        if (listAsString == null) {
            queuedPhotos = new ArrayList<>();
        } else {
            //noinspection unchecked
            queuedPhotos = (List<String>) gson.fromJson(listAsString, ArrayList.class);
        }
        return queuedPhotos;
    }


    public static int getSyncedCount() {
        return MyApp.getInstance().getSharedPrefs().getInt(Constants.SP_SYNCED_PHOTOS_COUNT, 0);
    }

    @SuppressLint("CommitPrefEdits")
    private void saveSyncedCount(int syncedCount) {
        MyApp.getInstance().getSharedPrefs().edit().putInt(Constants.SP_SYNCED_PHOTOS_COUNT, syncedCount).commit();
    }
}
