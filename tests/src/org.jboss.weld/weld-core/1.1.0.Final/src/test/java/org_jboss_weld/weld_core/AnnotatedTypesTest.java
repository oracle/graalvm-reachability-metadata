/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Named;

import org.jboss.weld.introspector.WeldClass;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.util.AnnotatedTypes;
import org.junit.jupiter.api.Test;

public class AnnotatedTypesTest {
    @Test
    void includesAnnotationValuesInStableIdentifiers() {
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        try {
            WeldClass<NamedBean> type = transformer.loadClass(NamedBean.class);
            String id = AnnotatedTypes.createTypeId(type);
            assertThat(id).contains(NamedBean.class.getName(), "@javax.inject.Named(value=checkout)");
            assertThat(AnnotatedTypes.createTypeId(type)).isEqualTo(id);
            String first = AnnotatedTypes.createParameterId(String.class,
                    Set.of(new NamedValue("first")));
            String second = AnnotatedTypes.createParameterId(String.class,
                    Set.of(new NamedValue("second")));
            assertThat(first).contains("value=first").isNotEqualTo(second);
            assertThat(second).contains("value=second");
        } finally {
            transformer.cleanup();
        }
    }

    @Named("checkout")
    public static class NamedBean {
    }

    public static class NamedValue extends AnnotationLiteral<Named> implements Named {
        private static final long serialVersionUID = 1L;
        private final String value;

        public NamedValue(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
