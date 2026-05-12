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
import org.msgpack.template.builder.BuildContext;
import org.msgpack.template.builder.FieldEntry;
import org.msgpack.template.builder.JavassistTemplateBuilder;
import org.msgpack.unpacker.Unpacker;

import javassist.CannotCompileException;
import javassist.NotFoundException;

public class BuildContextTest {
    @Test
    void loadReturnsNullWhenPrecompiledTemplateClassIsAbsent() {
        final ExposedBuildContext context = new ExposedBuildContext();

        final Template<?> template = context.loadGeneratedTemplate("org_msgpack.msgpack.BuildContextTestMissingTarget");

        assertThat(template).isNull();
        assertThat(context.loadedTemplateClass).isNull();
    }

    @Test
    void loadResolvesPrecompiledTemplateClassByGeneratedName() {
        final ExposedBuildContext context = new ExposedBuildContext();

        final Template<?> template = context.loadGeneratedTemplate(LoadableTarget.class.getName());

        assertThat(template).isSameAs(context.template);
        assertThat(context.loadedTemplateClass).isSameAs(LoadableTarget_$$_Template.class);
    }

    private static final class ExposedBuildContext extends BuildContext<FieldEntry> {
        private final Template<?> template = new MarkerTemplate();

        private Class<?> loadedTemplateClass;

        private ExposedBuildContext() {
            super((JavassistTemplateBuilder) null);
        }

        private Template<?> loadGeneratedTemplate(String className) {
            return load(className);
        }

        @Override
        protected Template<?> buildTemplate(Class<?> targetClass, FieldEntry[] entries, Template[] templates) {
            return null;
        }

        @Override
        protected void setSuperClass() throws CannotCompileException, NotFoundException {
        }

        @Override
        protected void buildConstructor() throws CannotCompileException, NotFoundException {
        }

        @Override
        protected Template<?> buildInstance(Class<?> templateClass) {
            this.loadedTemplateClass = templateClass;
            return this.template;
        }

        @Override
        protected String buildWriteMethodBody() {
            return "{}";
        }

        @Override
        protected String buildReadMethodBody() {
            return "return null;";
        }

        @Override
        protected void writeTemplate(
                Class<?> targetClass, FieldEntry[] entries, Template[] templates, String directoryName) {
        }

        @Override
        protected Template<?> loadTemplate(Class<?> targetClass, FieldEntry[] entries, Template[] templates) {
            return null;
        }
    }

    public static final class LoadableTarget {
    }

    @SuppressWarnings("checkstyle:TypeName")
    public static final class LoadableTarget_$$_Template {
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
}
