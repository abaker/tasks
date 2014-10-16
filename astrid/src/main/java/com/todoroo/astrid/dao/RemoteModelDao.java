package com.todoroo.astrid.dao;

import com.todoroo.andlib.data.DatabaseDao;
import com.todoroo.astrid.data.RemoteModel;

import org.tasks.helper.UUIDHelper;

/**
 * This class is meant to be subclassed for daos whose models
 * require UUID generation (i.e., most RemoteModels). The createNew
 * method takes care of automatically generating a new UUID for each newly
 * created model if one doesn't already exist.
 * @author Sam
 *
 * @param <RTYPE>
 */
public class RemoteModelDao<RTYPE extends RemoteModel> extends DatabaseDao<RTYPE> {

    public RemoteModelDao(Class<RTYPE> modelClass) {
        super(modelClass);
    }

    @Override
    public boolean createNew(RTYPE item) {
        if (!item.containsValue(RemoteModel.UUID_PROPERTY) || RemoteModel.isUuidEmpty(item.getUuidProperty())) {
            item.setUuidProperty(UUIDHelper.newUUID());
        }
        return super.createNew(item);
    }
}
