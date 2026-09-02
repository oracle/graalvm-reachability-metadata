/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DecoratedObjectFactoryTest {
    @Test
    void createsObjectsThroughTheirNoArgumentConstructor() throws Exception {
        DecoratedObjectFactory factory = new DecoratedObjectFactory();

        CreatedObject created = factory.createInstance(CreatedObject.class);

        assertThat(created.getValue()).isEqualTo("created");
    }

    public static final class CreatedObject {
        private final String value;

        public CreatedObject() {
            value = "created";
        }

        public String getValue() {
            return value;
        }
    }
}
