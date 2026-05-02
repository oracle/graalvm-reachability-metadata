package org_gwtproject.gwt_user.imagebundlebuilder.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.ImageBundle;

@SuppressWarnings("deprecation")
public final class ImageBundleBuilderEntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        CoverageImages images = GWT.create(CoverageImages.class);
        if (images.icon() == null) {
            throw new IllegalStateException("ImageBundle did not create an image prototype");
        }
    }

    interface CoverageImages extends ImageBundle {
        AbstractImagePrototype icon();
    }
}
