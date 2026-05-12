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
import org.msgpack.template.builder.DefaultBuildContext;
import org.msgpack.template.builder.JavassistTemplateBuilder.JavassistTemplate;
import org.msgpack.unpacker.Unpacker;

public class DefaultBuildContextTest {
    @Test
    void instantiatesTemplateConstructorSelectedByBuildContext() throws Exception {
        final ExposedBuildContext context = new ExposedBuildContext();

        final Template<?> template = context.instantiate(StubJavassistTemplate.class);

        assertThat(template).isInstanceOf(StubJavassistTemplate.class);
        final StubJavassistTemplate generatedTemplate = (StubJavassistTemplate) template;
        assertThat(generatedTemplate.targetClass).isSameAs(String.class);
        assertThat(generatedTemplate.templates).isEmpty();
    }

    @Test
    void readsAndWritesFieldsThroughBuildContextAccessors() {
        final StubJavassistTemplate target = new StubJavassistTemplate(String.class, new Template<?>[0]);
        assertThat(target.targetClass).isSameAs(String.class);

        final Object fieldValue = DefaultBuildContext.readPrivateField(
                target, JavassistTemplate.class, "targetClass");
        assertThat(fieldValue).isSameAs(String.class);

        final CapturingTemplate capturingTemplate = new CapturingTemplate();
        DefaultBuildContext.writePrivateField(
                (Packer) null, target, JavassistTemplate.class, "targetClass", capturingTemplate);
        assertThat(capturingTemplate.getWrittenValue()).isSameAs(String.class);

        final ReplacingTemplate replacingTemplate = new ReplacingTemplate(Integer.class);
        DefaultBuildContext.readPrivateField(
                (Unpacker) null, target, JavassistTemplate.class, "targetClass", replacingTemplate);
        assertThat(target.targetClass).isSameAs(Integer.class);
    }

    private static final class ExposedBuildContext extends DefaultBuildContext {
        private ExposedBuildContext() {
            super(null);
            this.origClass = String.class;
            this.templates = new Template<?>[0];
        }

        private Template<?> instantiate(Class<?> templateClass) throws Exception {
            return buildInstance(templateClass);
        }
    }

    public static final class StubJavassistTemplate extends JavassistTemplate<Object> {
        @SuppressWarnings("unchecked")
        public StubJavassistTemplate(Class<?> targetClass, Template<?>[] templates) {
            super((Class<Object>) targetClass, templates);
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

    private static final class CapturingTemplate implements Template<Object> {
        private Object writtenValue;

        @Override
        public void write(Packer pk, Object v) throws IOException {
            this.writtenValue = v;
        }

        @Override
        public void write(Packer pk, Object v, boolean required) throws IOException {
            this.writtenValue = v;
        }

        @Override
        public Object read(Unpacker u, Object to) throws IOException {
            return to;
        }

        @Override
        public Object read(Unpacker u, Object to, boolean required) throws IOException {
            return to;
        }

        private Object getWrittenValue() {
            return this.writtenValue;
        }
    }

    private static final class ReplacingTemplate implements Template<Object> {
        private final Object replacement;

        private ReplacingTemplate(Object replacement) {
            this.replacement = replacement;
        }

        @Override
        public void write(Packer pk, Object v) throws IOException {
        }

        @Override
        public void write(Packer pk, Object v, boolean required) throws IOException {
        }

        @Override
        public Object read(Unpacker u, Object to) throws IOException {
            return this.replacement;
        }

        @Override
        public Object read(Unpacker u, Object to, boolean required) throws IOException {
            return this.replacement;
        }
    }
}
