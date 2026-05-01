/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    void createsInstanceWithDefaultConstructor() {
        DefaultConstructibleBean value = ClassUtil.createInstance(DefaultConstructibleBean.class, false);

        assertThat(value.getName()).isEqualTo("constructed");
        assertThat(value.getCount()).isEqualTo(42);
    }

    public static class DefaultConstructibleBean {
        private final String name;
        private final int count;

        public DefaultConstructibleBean() {
            this.name = "constructed";
            this.count = 42;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }
}
