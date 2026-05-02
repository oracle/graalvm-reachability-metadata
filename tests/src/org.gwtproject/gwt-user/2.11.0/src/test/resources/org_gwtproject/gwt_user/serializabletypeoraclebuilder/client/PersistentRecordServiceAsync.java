package org_gwtproject.gwt_user.serializabletypeoraclebuilder.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface PersistentRecordServiceAsync {
    void echo(PersistentRecord record, AsyncCallback<PersistentRecord> callback);
}
