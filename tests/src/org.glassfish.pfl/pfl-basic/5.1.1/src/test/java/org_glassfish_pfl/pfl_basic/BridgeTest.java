/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.glassfish.pfl.basic.reflection.Bridge;
import org.junit.jupiter.api.Test;

public class BridgeTest {
    public static class SerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;

        public SerializableValue() {
        }
    }

    @Test
    public void createsSerializationConstructors() {
        Bridge bridge = Bridge.get();

        assertThat(bridge.newConstructorForSerialization(SerializableValue.class)).isNotNull();
        assertThat(bridge.newConstructorForSerialization(SerializableValue.class,
                SerializableValue.class.getConstructors()[0])).isNotNull();
    }
}
