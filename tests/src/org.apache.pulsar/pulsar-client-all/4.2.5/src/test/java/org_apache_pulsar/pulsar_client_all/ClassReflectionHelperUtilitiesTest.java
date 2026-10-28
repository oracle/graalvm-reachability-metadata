/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

public class ClassReflectionHelperUtilitiesTest {
    @Test
    void collectsDeclaredAndInheritedMethods() {
        ClassReflectionHelperImpl helper = new ClassReflectionHelperImpl();
        try {
            assertThat(helper.getAllMethods(ChildBean.class))
                    .extracting(wrapper -> wrapper.getMethod().getName())
                    .contains("childValue", "parentValue");
        } finally {
            helper.dispose();
        }
    }

    public static class ParentBean {
        public String parentValue() {
            return "parent";
        }
    }

    public static class ChildBean extends ParentBean {
        public String childValue() {
            return "child";
        }
    }
}
