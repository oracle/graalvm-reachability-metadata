/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.map.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {
    @Test
    public void createsInstanceUsingDefaultConstructor() {
        PublicDefaultConstructor value = ClassUtil.createInstance(PublicDefaultConstructor.class, false);

        assertThat(value.getName()).isEqualTo("constructed");
    }

    public static final class PublicDefaultConstructor {
        private final String name;

        public PublicDefaultConstructor() {
            this.name = "constructed";
        }

        public String getName() {
            return name;
        }
    }
}
