/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.lookup.ResourceBundleLookup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceBundleLookupTest {
    @Test
    void looksUpApplicationMessagesFromAResourceBundle() {
        ResourceBundleLookup lookup = new ResourceBundleLookup();
        assertEquals("Request accepted", lookup.lookup("org_apache_logging_log4j.log4j_core.messages:accepted"));
        assertEquals("Request rejected", lookup.lookup("org_apache_logging_log4j.log4j_core.messages:rejected"));
    }
}
