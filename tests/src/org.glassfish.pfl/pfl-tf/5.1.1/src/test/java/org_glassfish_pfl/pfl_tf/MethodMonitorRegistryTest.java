/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_tf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.glassfish.pfl.basic.contain.SynchronizedHolder;
import org.glassfish.pfl.tf.spi.MethodMonitor;
import org.glassfish.pfl.tf.spi.MethodMonitorFactory;
import org.glassfish.pfl.tf.spi.MethodMonitorFactoryDefaults;
import org.glassfish.pfl.tf.spi.MethodMonitorRegistry;
import org.glassfish.pfl.tf.spi.annotation.MethodMonitorGroup;
import org.junit.jupiter.api.Test;

public class MethodMonitorRegistryTest {
    @Test
    void registersReflectiveClassData() {
        MethodMonitorFactory factory = MethodMonitorFactoryDefaults.noOp();
        MethodMonitorRegistry.register(RegistryMonitor.class, factory);

        try {
            MethodMonitorRegistry.registerClass(RegistryTarget.class);

            assertThat(RegistryTarget.__$mm$__0).isNotNull();
            assertThat(MethodMonitorRegistry.getMethodNames(RegistryTarget.class))
                    .containsExactly("record");
            assertThat(MethodMonitorRegistry.getMethodIdentifier(RegistryTarget.class, "record"))
                    .isZero();
            assertThat(MethodMonitorRegistry.getMethodMonitorForClass(
                            RegistryTarget.class, RegistryMonitor.class)
                    .factory())
                    .isSameAs(factory);
        } finally {
            MethodMonitorRegistry.clear(RegistryMonitor.class);
        }
    }

    @Test
    void loadsMonitorAnnotationsFromAResourceBundle() {
        MethodMonitorRegistry.registerAnnotationFile("org_glassfish_pfl_pfl_tf.monitors");

        assertThat(MethodMonitorRegistry.getMMAnnotations())
                .contains("org_glassfish_pfl.pfl_tf.RegistryMonitor");
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@MethodMonitorGroup
@interface RegistryMonitor {
}

@RegistryMonitor
class RegistryTarget {
    @SuppressWarnings("checkstyle:StaticVariableName") static SynchronizedHolder<MethodMonitor> __$mm$__0;

    @RegistryMonitor
    void record() {
    }
}
