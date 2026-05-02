package org_gwtproject.gwt_user.messagesmethodcreator.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public final class MessagesMethodCreatorEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        PluralMessages messages = GWT.create(PluralMessages.class);
        if (!"One file was uploaded".equals(messages.uploadedFiles(1))) {
            throw new IllegalStateException("Expected generated plural Messages implementation");
        }
    }
}
