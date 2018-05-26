package org.tasks.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface GoogleTaskListDao {

    @Query("SELECT * FROM google_task_lists WHERE _id = :id")
    GoogleTaskList getById(long id);

    @Query("SELECT * FROM google_task_lists WHERE deleted = 0 ORDER BY title ASC")
    List<GoogleTaskList> getActiveLists();

    @Query("SELECT * FROM google_task_lists WHERE remote_id = :remoteId LIMIT 1")
    GoogleTaskList getByRemoteId(String remoteId);

    @Query("SELECT * FROM google_task_lists")
    List<GoogleTaskList> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(GoogleTaskList googleTaskList);

    @Insert
    void insert(GoogleTaskList googleTaskList);

    @Query("DELETE FROM google_task_lists WHERE _id = :id")
    void deleteById(long id);
}
