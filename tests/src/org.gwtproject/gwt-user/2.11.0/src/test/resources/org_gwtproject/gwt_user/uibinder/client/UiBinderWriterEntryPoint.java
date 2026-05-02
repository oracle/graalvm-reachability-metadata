package org_gwtproject.gwt_user.uibinder.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public final class UiBinderWriterEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        ClickRenderer renderer = GWT.create(ClickRenderer.class);
        SafeHtmlBuilder builder = new SafeHtmlBuilder();
        renderer.render(builder);
        if (builder.toSafeHtml().asString().isEmpty()) {
            throw new IllegalStateException("UiRenderer produced no markup");
        }
    }
}
