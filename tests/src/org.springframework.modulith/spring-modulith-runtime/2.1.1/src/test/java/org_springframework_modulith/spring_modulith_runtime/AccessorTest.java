/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.asm.ASMUtil;
import org.springframework.modulith.runtime.minidev.asm.Accessor;

public class AccessorTest {
    @Test
    void describesBeanPropertiesWithBothBooleanGetterConventions() {
        Map<String, Accessor> properties = Arrays.stream(ASMUtil.getAccessors(ModuleSettings.class, null))
                .collect(Collectors.toMap(Accessor::getName, Function.identity()));

        assertThat(properties.keySet()).containsExactlyInAnyOrder("name", "active", "verified");
        assertProperty(properties.get("name"), String.class);
        assertProperty(properties.get("active"), boolean.class);
        assertProperty(properties.get("verified"), boolean.class);
    }

    private static void assertProperty(Accessor property, Class<?> expectedType) {
        assertThat(property.getType()).isEqualTo(expectedType);
        assertThat(property.getGenericType()).isEqualTo(expectedType);
        assertThat(property.isReadable()).isTrue();
        assertThat(property.isWritable()).isTrue();
        assertThat(property.isPublic()).isFalse();
    }

    public static class ModuleSettings {
        private String name;
        private boolean active;
        private boolean verified;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public boolean getVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }
    }
}
