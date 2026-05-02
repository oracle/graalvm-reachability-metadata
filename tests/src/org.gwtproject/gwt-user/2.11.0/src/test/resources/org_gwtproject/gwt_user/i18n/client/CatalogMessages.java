package org_gwtproject.gwt_user.i18n.client;

import com.google.gwt.i18n.client.LocalizableResource.Generate;
import com.google.gwt.i18n.client.Messages;

@Generate(
        format = {
                "com.google.gwt.i18n.rebind.format.PropertiesFormat",
                "com.google.gwt.i18n.server.PropertyCatalogFactory"
        },
        fileName = "catalog-source",
        locales = {"default"}
)
public interface CatalogMessages extends Messages {
    String greeting(String subject);
}
