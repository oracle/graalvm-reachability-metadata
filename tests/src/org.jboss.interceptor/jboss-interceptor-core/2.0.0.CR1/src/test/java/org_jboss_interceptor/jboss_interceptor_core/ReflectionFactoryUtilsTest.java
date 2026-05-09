/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.io.Serializable;
import java.lang.reflect.Constructor;

import org.jboss.interceptor.util.ReflectionFactoryUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionFactoryUtilsTest {

    @Test
    void createsSerializationConstructorWithoutInvokingTargetConstructor() throws Exception {
        final Constructor<ConstructorRequiredSample> constructor = ReflectionFactoryUtils
                .getReflectionFactoryConstructor(ConstructorRequiredSample.class);

        if (ReflectionFactoryUtils.isAvailable()) {
            assertThat(constructor).isNotNull();
            final ConstructorRequiredSample instance = constructor.newInstance();

            assertThat(instance).isNotNull();
            assertThat(instance.value()).isNull();
        } else {
            assertThat(constructor).isNull();
        }
    }

    public static final class ConstructorRequiredSample implements Serializable {
        private final String value;

        public ConstructorRequiredSample(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
