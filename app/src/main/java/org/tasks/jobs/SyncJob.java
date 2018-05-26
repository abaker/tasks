package org.tasks.jobs;

import android.support.annotation.NonNull;
import javax.inject.Inject;
import org.tasks.LocalBroadcastManager;
import org.tasks.caldav.CaldavSynchronizer;
import org.tasks.gtasks.GoogleTaskSynchronizer;
import org.tasks.injection.InjectingJob;
import org.tasks.injection.JobComponent;
import org.tasks.preferences.Preferences;
import timber.log.Timber;

public class SyncJob extends InjectingJob {

  private static final Object LOCK = new Object();

  @Inject CaldavSynchronizer caldavSynchronizer;
  @Inject GoogleTaskSynchronizer googleTaskSynchronizer;
  @Inject LocalBroadcastManager localBroadcastManager;
  @Inject Preferences preferences;

  @NonNull
  @Override
  protected Result onRunJob(@NonNull Params params) {
    super.onRunJob(params);

    synchronized (LOCK) {
      if (preferences.isSyncOngoing()) {
        return Result.RESCHEDULE;
      }
    }

    preferences.setSyncOngoing(true);
    localBroadcastManager.broadcastRefresh();
    try {
      caldavSynchronizer.sync();
      googleTaskSynchronizer.sync();
    } catch (Exception e) {
      Timber.e(e);
    } finally {
      preferences.setSyncOngoing(false);
      localBroadcastManager.broadcastRefresh();
    }
    return Result.SUCCESS;
  }

  @Override
  protected void inject(JobComponent component) {
    component.inject(this);
  }
}
