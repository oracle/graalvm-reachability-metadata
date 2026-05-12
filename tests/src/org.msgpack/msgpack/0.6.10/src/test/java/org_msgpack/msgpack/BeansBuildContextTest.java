/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.msgpack.packer.Packer;
import org.msgpack.template.Template;
import org.msgpack.template.builder.BeansBuildContext;
import org.msgpack.unpacker.Unpacker;

public class BeansBuildContextTest {
    @Test
    void instantiatesBeansTemplateConstructorWithTargetClassAndNestedTemplates() throws Exception {
        final MarkerTemplate markerTemplate = new MarkerTemplate();
        final Template<?>[] nestedTemplates = new Template<?>[] {markerTemplate };
        final ExposedBeansBuildContext context = new ExposedBeansBuildContext(
                BeanTarget.class, nestedTemplates);

        final Template<?> template = context.instantiate(ConstructedBeansTemplate.class);

        assertThat(template).isInstanceOf(ConstructedBeansTemplate.class);
        final ConstructedBeansTemplate constructedTemplate = (ConstructedBeansTemplate) template;
        assertThat(constructedTemplate.targetClass).isSameAs(BeanTarget.class);
        assertThat(constructedTemplate.templates).containsExactly(markerTemplate);
    }

    private static final class ExposedBeansBuildContext extends BeansBuildContext {
        private ExposedBeansBuildContext(Class<?> targetClass, Template<?>[] templates) {
            super(null);
            this.origClass = targetClass;
            this.templates = templates;
        }

        private Template<?> instantiate(Class<?> templateClass) throws Exception {
            return buildInstance(templateClass);
        }
    }

    public static final class ConstructedBeansTemplate implements Template<Object> {
        private final Class<?> targetClass;

        private final Template<?>[] templates;

        public ConstructedBeansTemplate(Class<?> targetClass, Template<?>[] templates) {
            this.targetClass = targetClass;
            this.templates = templates;
        }

        @Override
        public void write(Packer pk, Object v) throws IOException {
        }

        @Override
        public void write(Packer pk, Object v, boolean required) throws IOException {
        }

        @Override
        public Object read(Unpacker u, Object to) throws IOException {
            return to;
        }

        @Override
        public Object read(Unpacker u, Object to, boolean required) throws IOException {
            return to;
        }
    }

    private static final class MarkerTemplate implements Template<Object> {
        @Override
        public void write(Packer pk, Object v) throws IOException {
        }

        @Override
        public void write(Packer pk, Object v, boolean required) throws IOException {
        }

        @Override
        public Object read(Unpacker u, Object to) throws IOException {
            return to;
        }

        @Override
        public Object read(Unpacker u, Object to, boolean required) throws IOException {
            return to;
        }
    }

    public static final class BeanTarget {
    }
}
