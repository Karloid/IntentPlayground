package com.krld.intentplayground;


import android.support.annotation.NonNull;

public class Constants {
    private static int id = 0;

    @NonNull
    private static String getId() {
        return String.valueOf(id++);
    }

    public static final int PHOTOS_CATCH_JOB_ID = id++;
    public static final int PHOTOS_UPLOAD_JOB_ID = id++;

    public static final String ACTION_COUNTERS_UPDATED = getId();
    public static final String EXTRA_SYNCED_COUNT = getId();
    public static final String EXTRA_QUEUED_COUNT = getId();

    public static final String ACTION_SYNCING_STATE = getId();
    public static final String EXTRA_SYNCING_IN_PROGRESS = getId();

    public static final String DROP_BOX_ACCESS_TOKEN = "DROP_BOX_ACCESS_TOKEN";

    public static final String SP_QUEUED_PHOTOS = "SP_QUEUED_PHOTOS";
    public static final String SP_QUEUED_PHOTOS_COUNT = "SP_QUEUED_PHOTOS_COUNT";
    public static final String SP_SYNCED_PHOTOS_COUNT = "SP_SYNCED_PHOTOS_COUNT";
    public static final String SP_LATEST_PHOTO_DATE_TAKEN = "SP_LATEST_PHOTO_DATE_TAKEN";
}
