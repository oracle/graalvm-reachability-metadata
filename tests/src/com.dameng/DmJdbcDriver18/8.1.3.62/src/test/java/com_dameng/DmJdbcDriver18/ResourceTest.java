/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.driver.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceTest {
    @Test
    void resolvesAndFormatsDriverErrorMessages() {
        assertFalse(Resource.get("error.invalidParameterValue").isBlank());
        String message = Resource.get("error.invalidDateTypeFormat", "invalid-date");
        assertTrue(message.contains("invalid-date"));
        assertFalse(message.contains("{0}"));
    }
}
