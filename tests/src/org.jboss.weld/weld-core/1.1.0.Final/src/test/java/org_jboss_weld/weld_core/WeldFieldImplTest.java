/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.weld.introspector.WeldClass;
import org.jboss.weld.introspector.WeldField;
import org.jboss.weld.introspector.jlr.WeldFieldImpl;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.junit.jupiter.api.Test;

public class WeldFieldImplTest {
    @Test
    void readsAndWritesFieldsThroughAnnotatedTypeModel() throws Exception {
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        try {
            WeldClass<ReflectionFixture> type = transformer.loadClass(ReflectionFixture.class);
            ReflectionFixture instance = new ReflectionFixture();
            WeldField<String, ?> hidden = type.getDeclaredWeldField("hidden");
            assertThat(hidden.get(instance)).isEqualTo("initial");
            hidden.set(instance, "updated");
            assertThat(hidden.get(instance)).isEqualTo("updated");
            WeldFieldImpl<String, ReflectionFixture> label = WeldFieldImpl.of(
                    type.getDeclaredWeldField("label").getJavaMember(), type, transformer);
            label.setOnInstance(instance, "injected");
            assertThat(instance.label).isEqualTo("injected");
            assertThat(label.get(instance)).isEqualTo("injected");
        } finally {
            transformer.cleanup();
        }
    }
}
