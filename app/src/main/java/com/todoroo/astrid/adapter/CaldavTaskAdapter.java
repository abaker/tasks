package com.todoroo.astrid.adapter;

import static com.todoroo.andlib.utility.DateUtilities.now;

import com.todoroo.astrid.dao.TaskDao;
import com.todoroo.astrid.data.Task;
import org.tasks.data.CaldavDao;
import org.tasks.data.CaldavTask;
import org.tasks.data.TaskContainer;
import org.tasks.tasklist.ViewHolder;
import timber.log.Timber;

public final class CaldavTaskAdapter extends TaskAdapter {

  private final TaskDao taskDao;
  private final CaldavDao caldavDao;

  CaldavTaskAdapter(TaskDao taskDao, CaldavDao caldavDao) {
    this.taskDao = taskDao;
    this.caldavDao = caldavDao;
  }

  @Override
  public int getIndent(TaskContainer task) {
    return task.getIndent();
  }

  @Override
  public boolean canMove(ViewHolder sourceVh, ViewHolder targetVh) {
    TaskContainer source = sourceVh.task;
    int to = targetVh.getAdapterPosition();

    return !taskIsChild(source.getCaldavTask(), to);
  }

  @Override
  public int maxIndent(int previousPosition, TaskContainer task) {
    TaskContainer previous = getTask(previousPosition);
    return previous.getIndent() + 1;
  }

  @Override
  public int minIndent(int nextPosition, TaskContainer task) {
    return 0;
  }

  @Override
  public boolean supportsParentingOrManualSort() {
    return true;
  }

  @Override
  public void moved(int from, int to, int indent) {
    TaskContainer task = getTask(from);
    TaskContainer previous = to > 0 ? getTask(to-1) : null;

    long newParent = task.getParent();
    if (indent == 0) {
      newParent = 0;
    } else if (previous != null) {
      if (indent == previous.getIndent()) {
        newParent = previous.getParent();
      } else if (indent > previous.getIndent()) {
        newParent = previous.getId();
      }
    }

    // If nothing is changing, return
    if (newParent == task.getParent()) {
      return;
    }

    changeParent(task, newParent);

    Task update = task.getTask();
    update.setModificationDate(now());
    taskDao.save(update);
  }

  private void changeParent(TaskContainer task, long newParent) {
    CaldavTask caldavTask = task.getCaldavTask();

    if (newParent == 0) {
      caldavTask.setRemoteParent("");
      caldavTask.setParent(0);
    } else {
      CaldavTask parentTask = caldavDao.getTask(newParent);
      if (parentTask == null) {
        return;
      }
      caldavTask.setRemoteParent(parentTask.getRemoteId());
      caldavTask.setParent(newParent);
    }
    caldavDao.update(caldavTask);
  }

  private boolean taskIsChild(CaldavTask parent, int destinationIndex) {
    // Don't allow dropping a parent onto their child
    TaskContainer ownChildCheck = getTask(destinationIndex);
    long movingCaldavTaskId = parent.getId();
    int itemIndex = destinationIndex;
    // Iterate levels of the hierarchy
    while (ownChildCheck != null && ownChildCheck.getParent() != 0) {
      // If the task we're trying to move is a parent of the destination, cancel the move
      if (ownChildCheck.getParent() == movingCaldavTaskId) {
        return true;
      }

      // Loop through the items in the view above the current task, looking for the parent
      long searchParent = ownChildCheck.getParent();
      while (ownChildCheck.getId() != searchParent) {
        // Handle case of parent not found in search, which shouldn't ever occur
        if (itemIndex == 0) {
          Timber.w("Couldn't find parent");
          return true;
        }
        ownChildCheck = getTask(--itemIndex);
      }
    }
    return false;
  }
}