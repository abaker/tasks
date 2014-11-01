/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.backup;

import android.os.Environment;

import java.io.File;

/**
 * Constants for backup XML attributes and nodes.
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class BackupConstants {

    // Do NOT edit the constants in this file! You will break compatibility with old backups

    // --- general xml

    /** Tag containing Astrid backup data */
    public static final String ASTRID_TAG = "astrid";

    /** Attribute indicating application version */
    public static final String ASTRID_ATTR_VERSION = "version";

    /** Attribute indicating backup file format */
    public static final String ASTRID_ATTR_FORMAT = "format";

    // --- format 2

    /** Tag containing a task */
    public static final String TASK_TAG = "task";

    /** Tag containing a metadata item */
    public static final String METADATA_TAG = "metadata";

    /** Tag containing a tagdata item */
    public static final String TAGDATA_TAG = "tagdata";

    /** Tag containing a timelog item */
    public static final String TIMELOG_TAG = "timelog";

    // --- general

    public static final String XML_ENCODING = "utf-8";

    public static final String ASTRID_DIR = "/astrid";

    public static final String EXPORT_FILE_NAME = "user.%s.xml";

    public static final String BACKUP_FILE_NAME = "auto.%s.xml";

    public static final String UPGRADE_FILE_NAME = "upgradefrom.%s.xml";

    // --- methods

    /**
     * @return export directory for tasks, or null if no SD card
     */
    public static File defaultExportDirectory() {
        String storageState = Environment.getExternalStorageState();
        if (storageState.equals(Environment.MEDIA_MOUNTED)) {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            path = path + ASTRID_DIR;
            return new File(path);
        }
        return null;
    }
}
