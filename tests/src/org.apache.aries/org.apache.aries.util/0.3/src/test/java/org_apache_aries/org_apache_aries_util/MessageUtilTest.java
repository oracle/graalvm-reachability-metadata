/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries.org_apache_aries_util;

import java.util.ResourceBundle;

import org.apache.aries.util.nls.MessageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageUtilTest {
    private static final String BASE_NAME = "org.apache.aries.util.messages.UTILmessages";

    @BeforeEach
    void clearBundleCache() {
        ResourceBundle.clearCache();
    }

    @Test
    void loadsMessagesOutsideOsgi() {
        MessageUtil messages = MessageUtil.createMessageUtil((Bundle) null, BASE_NAME);

        assertEquals("UTIL0013E: The resource bundle format custom is not supported.",
                messages.getMessage("UTIL0013E", "custom"));
        assertEquals("unknown.message", messages.getMessage("unknown.message"));
    }

    @Test
    void resolvesMessagesUsingCallerContext() {
        MessageUtil messages = MessageUtil.createMessageUtil(MessageUtilTest.class, BASE_NAME);

        assertEquals("UTIL0010E: Unable to parse the version. Could not parse empty string.",
                messages.getMessage("UTIL0010E"));
    }

    @Test
    void loadsPropertiesThroughBundleResources() {
        MessageResourceBundle bundle = new MessageResourceBundle(false);
        MessageUtil messages = MessageUtil.createMessageUtil(bundle, BASE_NAME);

        assertEquals("UTIL0012E: The string [sample is not correctly delimited.",
                messages.getMessage("UTIL0012E", "[sample"));
        assertTrue(bundle.resourceRequests() > 0);
    }

    @Test
    void fallsBackToBundleEntriesForProperties() {
        MessageResourceBundle bundle = new MessageResourceBundle(true);
        MessageUtil messages = MessageUtil.createMessageUtil(bundle, BASE_NAME);

        assertEquals("UTIL0013E: The resource bundle format alternate is not supported.",
                messages.getMessage("UTIL0013E", "alternate"));
        assertTrue(bundle.entryRequests() > 0);
        assertEquals("unknown.message", messages.getMessage("unknown.message"));
    }
}
