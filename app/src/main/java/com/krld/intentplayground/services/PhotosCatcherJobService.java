package com.krld.intentplayground.services;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.krld.intentplayground.Constants;
import com.krld.intentplayground.MyApp;

import java.util.ArrayList;
import java.util.List;

//Almost copy of google api example
public class PhotosCatcherJobService extends JobService {

    private static final String LOG_TAG = "PhotosCatcherJobService";

    static final List<String> EXTERNAL_PATH_SEGMENTS = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPathSegments();
    static final String[] PROJECTION = new String[]{MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA, MediaStore.Images.ImageColumns.DATE_TAKEN};
    static final int PROJECTION_DATA = 1;
    static final int PROJECTION_DATE_TAKEN = 2;
    static final String DCIM_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
    static final JobInfo JOB_INFO;


    static {
        ComponentName serviceName = new ComponentName("com.krld.intentplayground", PhotosCatcherJobService.class.getName());
        JOB_INFO = new JobInfo.Builder(Constants.PHOTOS_CATCH_JOB_ID, serviceName)
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS)).build();
    }

    public static void scheduleJob(Context context) {
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
            if (jobs.get(i).getId() == Constants.PHOTOS_CATCH_JOB_ID) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    public static void cancelJob(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        js.cancel(Constants.PHOTOS_CATCH_JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        Log.i(LOG_TAG, "JOB STARTED!");

        if (params.getTriggeredContentAuthorities() != null) {
            if (params.getTriggeredContentUris() != null) {
                ArrayList<String> ids = new ArrayList<>();
                for (Uri uri : params.getTriggeredContentUris()) {
                    List<String> path = uri.getPathSegments();
                    if (path != null && path.size() == EXTERNAL_PATH_SEGMENTS.size() + 1) {
                        ids.add(path.get(path.size() - 1));
                    }
                }

                if (ids.size() > 0) {
                    StringBuilder selection = new StringBuilder();
                    for (int i = 0; i < ids.size(); i++) {
                        if (selection.length() > 0) {
                            selection.append(" OR ");
                        }
                        selection.append(MediaStore.Images.ImageColumns._ID);
                        selection.append("='");
                        selection.append(ids.get(i));
                        selection.append("'");
                    }
                    long latestDateTaken = MyApp.getInstance().getSharedPrefs().getLong(Constants.SP_LATEST_PHOTO_DATE_TAKEN, 0L);
                    long newLatestDateTaken = latestDateTaken;
                    List<String> newPhotos = new ArrayList<>();
                    Cursor cursor = null;
                    try {
                        cursor = getContentResolver().query(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                PROJECTION, selection.toString(), null, null);
                        while (cursor != null && cursor.moveToNext()) {
                            String dir = cursor.getString(PROJECTION_DATA);
                            if (dir.startsWith(DCIM_DIR)) {
                                long dateTaken = cursor.getLong(PROJECTION_DATE_TAKEN);
                                if (latestDateTaken >= dateTaken) {
                                    continue;
                                }
                                newLatestDateTaken = Math.max(dateTaken, latestDateTaken);
                                newPhotos.add(dir);
                            }
                        }
                    } catch (SecurityException e) {
                        //TODO handle
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    MyApp.getInstance().getSharedPrefs().edit().putLong(Constants.SP_LATEST_PHOTO_DATE_TAKEN, newLatestDateTaken).apply();
                    PhotosUploaderJobService.handle(newPhotos, this);
                }
            }
        }
        scheduleJob(this);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(LOG_TAG, "JOB STOP!");
        return false;
    }
}