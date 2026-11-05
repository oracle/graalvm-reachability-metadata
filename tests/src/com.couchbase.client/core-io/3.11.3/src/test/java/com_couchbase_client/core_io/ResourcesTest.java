/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.deps.com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourcesTest {
    @Test
    void exercisesPublicApi() throws Exception {
        String relative = Resources.toString(Resources.getResource(Core.class, "version.properties"), StandardCharsets.UTF_8);
        String absolute = Resources.toString(Resources.getResource("com/couchbase/client/core/version.properties"), StandardCharsets.UTF_8);
        assertEquals(relative, absolute);
        assertTrue(relative.contains("version="));
        assertTrue(relative.contains("gitHash="));
    }
}
