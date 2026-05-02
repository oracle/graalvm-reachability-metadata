package org_gwtproject.gwt_user.clientbundle.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

public final class AbstractClientBundleGeneratorEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        CoverageResources resources = GWT.create(CoverageResources.class);
        if (!resources.message().getText().contains("client bundle")) {
            throw new IllegalStateException("TextResource content was not generated");
        }
    }

    interface CoverageResources extends ClientBundle {
        @Source("message.txt")
        TextResource message();
    }
}
