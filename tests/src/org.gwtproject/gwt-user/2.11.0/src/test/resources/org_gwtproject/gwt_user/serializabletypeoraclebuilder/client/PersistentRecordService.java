package org_gwtproject.gwt_user.serializabletypeoraclebuilder.client;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

@RemoteServiceRelativePath("persistentRecord")
public interface PersistentRecordService extends RemoteService {
    PersistentRecord echo(PersistentRecord record);
}
