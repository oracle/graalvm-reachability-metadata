/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.impl.ClusterSerializableUtils;
import io.vertx.core.shareddata.ClusterSerializable;
import io.vertx.core.spi.cluster.RegistrationInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ClusterSerializableUtilsTest {

    @Test
    void copyCreatesIndependentClusterSerializableInstance() {
        RegistrationInfo original = new RegistrationInfo("node-a", 42L, true);

        ClusterSerializable copied = ClusterSerializableUtils.copy(original);

        RegistrationInfo copiedRegistration = assertInstanceOf(RegistrationInfo.class, copied);
        assertNotSame(original, copiedRegistration);
        assertEquals(original, copiedRegistration);
    }
}
