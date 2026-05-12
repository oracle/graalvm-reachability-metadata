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
import org.msgpack.packer.BufferPacker;
import org.msgpack.packer.MessagePackBufferPacker;
import org.msgpack.template.FieldList;
import org.msgpack.template.FieldOption;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.builder.ReflectionTemplateBuilder;
import org.msgpack.unpacker.BufferUnpacker;
import org.msgpack.unpacker.MessagePackBufferUnpacker;

public class ReflectionTemplateBuilderInnerReflectionClassTemplateTest {
    @Test
    void createsTargetInstanceWhenReadingIntoNullTarget() throws IOException {
        final ReflectionTemplateBuilder builder = new ReflectionTemplateBuilder(new TemplateRegistry(null));
        final FieldList fields = new FieldList();
        fields.add("id", FieldOption.NOTNULLABLE);
        fields.add("name", FieldOption.NOTNULLABLE);
        final Template<InstantiatedRecord> template = builder.buildTemplate(InstantiatedRecord.class, fields);
        final InstantiatedRecord source = new InstantiatedRecord();
        source.id = 42;
        source.name = "reflection-template";

        final byte[] packed = write(source, template);
        final InstantiatedRecord unpacked = readIntoNullTarget(packed, template);

        assertThat(unpacked).isNotSameAs(source);
        assertThat(unpacked.id).isEqualTo(42);
        assertThat(unpacked.name).isEqualTo("reflection-template");
    }

    private static <T> byte[] write(final T source, final Template<T> template) throws IOException {
        final BufferPacker packer = new MessagePackBufferPacker(null);
        template.write(packer, source);
        return packer.toByteArray();
    }

    private static <T> T readIntoNullTarget(final byte[] packed, final Template<T> template) throws IOException {
        final BufferUnpacker unpacker = new MessagePackBufferUnpacker(null).wrap(packed);
        return template.read(unpacker, null);
    }

    public static class InstantiatedRecord {
        public int id;

        public String name;
    }
}
