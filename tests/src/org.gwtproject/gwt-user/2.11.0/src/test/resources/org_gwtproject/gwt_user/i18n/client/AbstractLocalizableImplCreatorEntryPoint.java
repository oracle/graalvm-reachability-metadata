package org_gwtproject.gwt_user.i18n.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public final class AbstractLocalizableImplCreatorEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        CatalogMessages messages = GWT.create(CatalogMessages.class);
        if (!"Hello catalog coverage".equals(messages.greeting("catalog coverage"))) {
            throw new IllegalStateException("Expected generated Messages implementation");
        }
    }
}
