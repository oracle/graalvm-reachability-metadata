/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.gwt.i18n.tools.I18NSync;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

public class I18NSyncTest {
    private static final String MISSING_MESSAGES_INTERFACE =
            "org_gwtproject.gwt_user.i18nsync.MissingMessages";
    private static final String MISSING_MESSAGES_RESOURCE =
            "org_gwtproject/gwt_user/i18nsync/MissingMessages.properties";

    @Test
    public void reportsMissingResourceForMessagesInterfaceClassName() {
        assertThatExceptionOfType(FileNotFoundException.class)
                .isThrownBy(() -> I18NSync.createMessagesInterfaceFromClassName(
                        MISSING_MESSAGES_INTERFACE))
                .withMessageContaining(MISSING_MESSAGES_RESOURCE)
                .withMessageContaining(MISSING_MESSAGES_INTERFACE);
    }
}
