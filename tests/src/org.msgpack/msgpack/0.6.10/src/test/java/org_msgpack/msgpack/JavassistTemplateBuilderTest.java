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
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.builder.JavassistTemplateBuilder;
import org.msgpack.template.builder.JavassistTemplateBuilder.JavassistTemplate;
import org.msgpack.unpacker.Unpacker;

public class JavassistTemplateBuilderTest {
    @Test
    void loadsPrecompiledTemplateClassByGeneratedName() {
        final JavassistTemplateBuilder builder = new JavassistTemplateBuilder(new TemplateRegistry(null));

        final Template<LoadableRecord> template = builder.loadTemplate(LoadableRecord.class);

        assertThat(template).isInstanceOf(LoadableRecord_$$_Template.class);
        final LoadableRecord_$$_Template generatedTemplate = (LoadableRecord_$$_Template) template;
        assertThat(generatedTemplate.targetClass).isSameAs(LoadableRecord.class);
        assertThat(generatedTemplate.templates).hasSize(2);
    }

    public static class LoadableRecord {
        public int id;

        public String name;
    }

    @SuppressWarnings("checkstyle:TypeName")
    public static final class LoadableRecord_$$_Template extends JavassistTemplate<LoadableRecord> {
        public LoadableRecord_$$_Template(Class<LoadableRecord> targetClass, Template<?>[] templates) {
            super(targetClass, templates);
        }

        @Override
        public void write(Packer pk, LoadableRecord v) throws IOException {
        }

        @Override
        public void write(Packer pk, LoadableRecord v, boolean required) throws IOException {
        }

        @Override
        public LoadableRecord read(Unpacker u, LoadableRecord to) throws IOException {
            return to;
        }

        @Override
        public LoadableRecord read(Unpacker u, LoadableRecord to, boolean required) throws IOException {
            return to;
        }
    }
}
