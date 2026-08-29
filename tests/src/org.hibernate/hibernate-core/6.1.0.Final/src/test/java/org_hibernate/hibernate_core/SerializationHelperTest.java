/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationHelperTest {

    @Test
    public void clonesSerializableApplicationValues() {
        Payload original = new Payload("hibernate");

        Payload copy = (Payload) SerializationHelper.clone(original);

        assertThat(copy.getValue()).isEqualTo("hibernate");
        assertThat(copy).isNotSameAs(original);
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public Payload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
