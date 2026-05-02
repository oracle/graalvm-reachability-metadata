package org_gwtproject.gwt_user.messagesmethodcreator.client;

import com.google.gwt.i18n.client.Messages;

public interface PluralMessages extends Messages {
    @DefaultMessage("{0} files were uploaded")
    @AlternateMessage({"one", "One file was uploaded"})
    String uploadedFiles(@PluralCount int count);
}
