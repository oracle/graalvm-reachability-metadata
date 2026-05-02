package org_gwtproject.gwt_user.uibinder.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiRenderer;

public interface ClickRenderer extends UiRenderer {
    void render(SafeHtmlBuilder builder);

    void onBrowserEvent(ClickTarget target, NativeEvent event, Element parent);
}
