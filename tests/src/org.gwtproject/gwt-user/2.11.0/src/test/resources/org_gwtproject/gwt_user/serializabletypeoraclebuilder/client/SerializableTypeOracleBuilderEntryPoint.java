package org_gwtproject.gwt_user.serializabletypeoraclebuilder.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public final class SerializableTypeOracleBuilderEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        PersistentRecordService service = GWT.create(PersistentRecordService.class);
        if (service == null) {
            throw new IllegalStateException("RPC proxy was not generated");
        }
    }
}
