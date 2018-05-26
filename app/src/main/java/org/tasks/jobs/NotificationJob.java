package org.tasks.jobs;

import android.support.annotation.NonNull;
import java.util.List;
import javax.inject.Inject;
import org.tasks.BuildConfig;
import org.tasks.Notifier;
import org.tasks.injection.InjectingJob;
import org.tasks.injection.JobComponent;
import org.tasks.preferences.Preferences;

public class NotificationJob extends InjectingJob {

  @Inject Preferences preferences;
  @Inject Notifier notifier;
  @Inject NotificationQueue notificationQueue;

  @NonNull
  @Override
  protected Result onRunJob(@NonNull Params params) {
    super.onRunJob(params);

    if (!preferences.isCurrentlyQuietHours()) {
      List<? extends NotificationQueueEntry> overdueJobs = notificationQueue.getOverdueJobs();
      notifier.triggerTaskNotifications(overdueJobs);
      boolean success = notificationQueue.remove(overdueJobs);
      if (BuildConfig.DEBUG && !success) {
        throw new RuntimeException("Failed to remove jobs from queue");
      }
    }
    notificationQueue.scheduleNext();
    return Result.SUCCESS;
  }

  @Override
  protected void inject(JobComponent component) {
    component.inject(this);
  }
}
