/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.BindConstructorProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBindConstructorProviderInnerConstructorsTest {

    @Test
    void defaultProviderInspectsThisFieldBeforeDeducingBindConstructor() {
        Constructor<?> bindConstructor = BindConstructorProvider.DEFAULT
            .getBindConstructor(PropertiesWithEnclosingInstanceField.class, false);

        assertThat(bindConstructor).isNotNull();
        assertThat(bindConstructor.getDeclaringClass()).isEqualTo(PropertiesWithEnclosingInstanceField.class);
    }

    public static final class PropertiesWithEnclosingInstanceField {

        private final Object this$0 = new Object();

        private final String name;

        public PropertiesWithEnclosingInstanceField(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

    }

}
