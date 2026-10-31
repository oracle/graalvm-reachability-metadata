/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.enterprise.inject.spi.AnnotatedConstructor;

import org.jboss.weld.introspector.WeldClass;
import org.jboss.weld.introspector.WeldConstructor;
import org.jboss.weld.introspector.jlr.WeldConstructorImpl;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.junit.jupiter.api.Test;

public class WeldConstructorImplTest {
    @Test
    void constructsInstancesFromAnnotatedConstructors() throws Exception {
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        try {
            WeldClass<ReflectionFixture> type = transformer.loadClass(ReflectionFixture.class);
            assertThat(type.getNoArgsWeldConstructor().newInstance().label).isEqualTo("default");
            for (AnnotatedConstructor<ReflectionFixture> annotated : type.getConstructors()) {
                WeldConstructor<ReflectionFixture> constructor = WeldConstructorImpl.of(annotated, type, transformer);
                String[] parameters = constructor.getSignature().getParameterTypes();
                if (parameters.length == 1 && parameters[0].equals(String.class.getName())) {
                    assertThat(constructor.newInstance("configured").label).isEqualTo("configured");
                } else if (parameters.length == 1 && parameters[0].equals("int")) {
                    assertThat(constructor.newInstance(23).label).isEqualTo("23");
                }
            }
            assertThat(type.getConstructors()).hasSize(3);
        } finally {
            transformer.cleanup();
        }
    }
}
