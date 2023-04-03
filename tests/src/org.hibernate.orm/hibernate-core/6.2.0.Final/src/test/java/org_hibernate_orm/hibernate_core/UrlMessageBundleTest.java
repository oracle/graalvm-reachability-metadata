/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import org.hibernate.internal.log.UrlMessageBundle;
import org.junit.jupiter.api.Test;

import java.net.URL;

public class UrlMessageBundleTest {

    @Test
    public void testLogger() throws Exception {
        UrlMessageBundle.URL_MESSAGE_LOGGER.logFileIsNotDirectory(new URL("file:"));
    }
}
