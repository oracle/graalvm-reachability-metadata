/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.javassist.tools.reflect.ClassMetaobject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ClassMetaobjectTest {
    @Test
    void createsInstancesAndDispatchesReflectiveMembers() throws Throwable {
        ClassMetaobject metaobject = new ClassMetaobject(
                new String[] {ReflectiveTarget.class.getName()});
        ReflectiveTarget target =
                (ReflectiveTarget) metaobject.newInstance(new Object[] {"value"});

        metaobject.trapFieldWrite("status", "ready");
        Object invoked = ClassMetaobject.invoke(target, 1, new Object[] {3});
        int upperMethod = metaobject.getMethodIndex("upper", new Class<?>[] {String.class});
        Object upper = metaobject.trapMethodcall(upperMethod, new Object[] {"pulsar"});

        assertThat(metaobject.getJavaClass()).isEqualTo(ReflectiveTarget.class);
        assertThat(metaobject.isInstance(target)).isTrue();
        assertThat(metaobject.trapFieldRead("status")).isEqualTo("ready");
        assertThat(invoked).isEqualTo("valuevaluevalue");
        assertThat(upper).isEqualTo("PULSAR");
        assertThat(metaobject.getMethodName(upperMethod)).isEqualTo("upper");
        assertThat(metaobject.getParameterTypes(upperMethod)).containsExactly(String.class);
        assertThat(metaobject.getReturnType(upperMethod)).isEqualTo(String.class);

        boolean previous = ClassMetaobject.useContextClassLoader;
        try {
            ClassMetaobject.useContextClassLoader = true;
            assertThat(new ClassMetaobject(new String[] {ReflectiveTarget.class.getName()})
                            .getJavaClass())
                    .isEqualTo(ReflectiveTarget.class);
        } finally {
            ClassMetaobject.useContextClassLoader = previous;
        }
    }

    public static final class ReflectiveTarget {
        public static String status = "new";

        private final String value;

        public ReflectiveTarget(String value) {
            this.value = value;
        }

        public static String _m_0_upper(String input) {
            return input.toUpperCase();
        }

        public String _m_1_repeat(int count) {
            return value.repeat(count);
        }
    }
}
