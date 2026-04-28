/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous6Test {
    @Test
    void privilegedExceptionActionRunSetsFieldValue() throws Exception {
        MutableTarget target = new MutableTarget("initial");
        Field valueField = MutableTarget.class.getField("value");
        Class<?> securityActions = Class.forName("javassist.util.proxy.SecurityActions");
        Method set = securityActions.getDeclaredMethod("set", Field.class, Object.class, Object.class);
        set.setAccessible(true);

        Object result = set.invoke(null, valueField, target, "updated");

        assertThat(result).isNull();
        assertThat(target.value()).isEqualTo("updated");
    }

    public static class MutableTarget {
        public String value;

        public MutableTarget(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
