package org_gwtproject.gwt_user.serializabletypeoraclebuilder.client;

import com.google.gwt.user.client.rpc.IsSerializable;

import javax.jdo.annotations.PersistenceCapable;

@PersistenceCapable(detachable = "true")
public final class PersistentRecord implements IsSerializable {
    private String name;

    public PersistentRecord() {
    }

    public PersistentRecord(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
