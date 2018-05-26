package org.tasks.backup;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.res.Resources;
import android.os.Handler;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.todoroo.andlib.utility.DialogUtilities;
import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;

import org.tasks.LocalBroadcastManager;
import org.tasks.R;
import org.tasks.data.Alarm;
import org.tasks.data.AlarmDao;
import org.tasks.data.Filter;
import org.tasks.data.FilterDao;
import org.tasks.data.GoogleTask;
import org.tasks.data.GoogleTaskDao;
import org.tasks.data.GoogleTaskList;
import org.tasks.data.GoogleTaskListDao;
import org.tasks.data.Location;
import org.tasks.data.LocationDao;
import org.tasks.data.Tag;
import org.tasks.data.TagDao;
import org.tasks.data.TagData;
import org.tasks.data.TagDataDao;
import org.tasks.data.TaskAttachment;
import org.tasks.data.TaskAttachmentDao;
import org.tasks.data.UserActivity;
import org.tasks.data.UserActivityDao;
import org.tasks.dialogs.DialogBuilder;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileReader;
import java.io.IOException;

import javax.inject.Inject;

import timber.log.Timber;

public class TasksJsonImporter {

    private final TagDataDao tagDataDao;
    private final UserActivityDao userActivityDao;
    private final DialogBuilder dialogBuilder;
    private final TaskDao taskDao;
    private final LocalBroadcastManager localBroadcastManager;
    private final AlarmDao alarmDao;
    private final TagDao tagDao;
    private final GoogleTaskDao googleTaskDao;
    private final GoogleTaskListDao googleTaskListDao;
    private final FilterDao filterDao;
    private final TaskAttachmentDao taskAttachmentDao;
    private final LocationDao locationDao;

    private Activity activity;
    private Handler handler;
    private int taskCount;
    private int importCount = 0;
    private int skipCount = 0;
    private int errorCount = 0;
    private ProgressDialog progressDialog;
    private String input;

    private void setProgressMessage(final String message) {
        handler.post(() -> progressDialog.setMessage(message));
    }

    @Inject
    public TasksJsonImporter(TagDataDao tagDataDao, UserActivityDao userActivityDao,
                             DialogBuilder dialogBuilder, TaskDao taskDao, LocationDao locationDao,
                             LocalBroadcastManager localBroadcastManager, AlarmDao alarmDao,
                             TagDao tagDao, GoogleTaskDao googleTaskDao, GoogleTaskListDao googleTaskListDao,
                             FilterDao filterDao, TaskAttachmentDao taskAttachmentDao) {
        this.tagDataDao = tagDataDao;
        this.userActivityDao = userActivityDao;
        this.dialogBuilder = dialogBuilder;
        this.taskDao = taskDao;
        this.locationDao = locationDao;
        this.localBroadcastManager = localBroadcastManager;
        this.alarmDao = alarmDao;
        this.tagDao = tagDao;
        this.googleTaskDao = googleTaskDao;
        this.googleTaskListDao = googleTaskListDao;
        this.filterDao = filterDao;
        this.taskAttachmentDao = taskAttachmentDao;
    }

    public void importTasks(Activity activity, String input, ProgressDialog progressDialog) {
        this.activity = activity;
        this.input = input;
        this.progressDialog = progressDialog;

        handler = new Handler();

        new Thread(() -> {
            try {
                performImport();
            } catch (IOException | XmlPullParserException e) {
                Timber.e(e, e.getMessage());
            }
        }).start();
    }

    private void performImport() throws IOException, XmlPullParserException {
        FileReader fileReader = new FileReader(input);
        String string = CharStreams.toString(fileReader);
        fileReader.close();
        Gson gson = new Gson();
        JsonObject input = gson.fromJson(string, JsonObject.class);

        try {
            JsonElement data = input.get("data");
            BackupContainer backupContainer = gson.fromJson(data, BackupContainer.class);
            for (TagData tagData : backupContainer.tags) {
                if (tagDataDao.getByUuid(tagData.getRemoteId()) == null) {
                    tagDataDao.createNew(tagData);
                }
            }
            for (GoogleTaskList googleTaskList : backupContainer.googleTaskLists) {
                if (googleTaskListDao.getByRemoteId(googleTaskList.getRemoteId()) == null) {
                    googleTaskListDao.insert(googleTaskList);
                }
            }
            for (Filter filter : backupContainer.filters) {
                if (filterDao.getByName(filter.getTitle()) == null) {
                    filterDao.insert(filter);
                }
            }
            for (BackupContainer.TaskBackup backup : backupContainer.tasks) {
                taskCount++;
                setProgressMessage(activity.getString(R.string.import_progress_read, taskCount));
                Task task = backup.task;
                if (taskDao.fetch(task.getUuid()) != null) {
                    skipCount++;
                    continue;
                }
                taskDao.createNew(task);
                long taskId = task.getId();
                String taskUuid = task.getUuid();
                for (Alarm alarm : backup.alarms) {
                    alarm.setTask(taskId);
                    alarmDao.insert(alarm);
                }
                for (UserActivity comment : backup.comments) {
                    comment.setTargetId(taskUuid);
                    userActivityDao.createNew(comment);
                }
                for (GoogleTask googleTask : backup.google) {
                    googleTask.setTask(taskId);
                    googleTaskDao.insert(googleTask);
                }
                for (Tag tag : backup.tags) {
                    tag.setTask(taskId);
                    tag.setTaskUid(taskUuid);
                    tagDao.insert(tag);
                }
                for (Location location : backup.locations) {
                    location.setTask(taskId);
                    locationDao.insert(location);
                }
                for (TaskAttachment attachment : backup.getAttachments()) {
                    attachment.setTaskId(taskUuid);
                    taskAttachmentDao.insert(attachment);
                }
                importCount++;
            }
        } finally {
            localBroadcastManager.broadcastRefresh();
            handler.post(() -> {
                if(progressDialog.isShowing()) {
                    DialogUtilities.dismissDialog(activity, progressDialog);
                    showSummary();
                }
            });
        }
    }

    private void showSummary() {
        Resources r = activity.getResources();
        dialogBuilder.newDialog()
                .setTitle(R.string.import_summary_title)
                .setMessage(activity.getString(R.string.import_summary_message,
                        input,
                        r.getQuantityString(R.plurals.Ntasks, taskCount, taskCount),
                        r.getQuantityString(R.plurals.Ntasks, importCount, importCount),
                        r.getQuantityString(R.plurals.Ntasks, skipCount, skipCount),
                        r.getQuantityString(R.plurals.Ntasks, errorCount, errorCount)))
                .setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss())
                .show();
    }
}
