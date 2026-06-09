/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.MethodAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodAccessTest {
    @Test
    void generatedMethodAccessorInvokesPublicMethods() {
        try {
            MethodAccess access = MethodAccess.get(AccessedService.class);
            MethodAccess cachedAccess = MethodAccess.get(AccessedService.class);
            AccessedService service = new AccessedService("prefix");

            int combineIndex = access.getIndex("combine", String.class, int.class);
            int resetIndex = access.getIndex("reset");
            int describeIndex = access.getIndex("describe", 1);

            assertThat(cachedAccess.getClass()).isEqualTo(access.getClass());
            assertThat(access.getMethodNames()).contains("combine", "reset", "describe", "parentName");
            assertThat(access.getParameterTypes()[combineIndex]).containsExactly(String.class, int.class);
            assertThat(access.getReturnTypes()[resetIndex]).isEqualTo(void.class);
            assertThat(access.invoke(service, combineIndex, "item", 3)).isEqualTo("prefix:item:3");
            assertThat(access.invoke(service, describeIndex, "value")).isEqualTo("prefix-value");
            assertThat(access.invoke(service, "parentName")).isEqualTo("parent");
            assertThat(access.invoke(service, resetIndex)).isNull();
            assertThat(service.getResetCount()).isEqualTo(1);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class ParentService {
        public String parentName() {
            return "parent";
        }
    }

    public static class AccessedService extends ParentService {
        private final String prefix;
        private int resetCount;

        public AccessedService(String prefix) {
            this.prefix = prefix;
        }

        public String combine(String value, int count) {
            return prefix + ":" + value + ":" + count;
        }

        public String describe(Object value) {
            return prefix + "-" + value;
        }

        public void reset() {
            resetCount++;
        }

        public int getResetCount() {
            return resetCount;
        }

        public static String staticMethod() {
            return "not exposed";
        }

        private String privateMethod() {
            return "not exposed";
        }
    }
}
