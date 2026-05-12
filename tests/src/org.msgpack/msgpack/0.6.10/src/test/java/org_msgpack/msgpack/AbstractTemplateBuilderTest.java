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

public class AbstractTemplateBuilderTest {
    @Test
    void buildsTemplateFromDeclaredFieldsDiscoveredOnClassHierarchy() throws IOException {
        final ReflectionTemplateBuilder builder = newReflectionTemplateBuilder();

        final Template<AutoFieldsChild> template = builder.buildTemplate(AutoFieldsChild.class);

        final AutoFieldsChild source = new AutoFieldsChild();
        source.baseNumber = 42;
        source.childText = "declared-fields";

        final byte[] packed = write(source, template);
        final AutoFieldsChild target = read(packed, new AutoFieldsChild(), template);

        assertThat(target.baseNumber).isEqualTo(42);
        assertThat(target.childText).isEqualTo("declared-fields");
    }

    @Test
    void buildsTemplateFromExplicitFieldListByResolvingDeclaredFieldNames() throws IOException {
        final ReflectionTemplateBuilder builder = newReflectionTemplateBuilder();
        final FieldList fieldList = new FieldList();
        fieldList.add("includedNumber", FieldOption.NOTNULLABLE);
        fieldList.add("includedText", FieldOption.OPTIONAL);

        final Template<ExplicitFieldsRecord> template = builder.buildTemplate(
                ExplicitFieldsRecord.class, fieldList);

        final ExplicitFieldsRecord source = new ExplicitFieldsRecord();
        source.includedNumber = 7;
        source.includedText = "listed-field";
        source.notListedText = "not serialized";

        final byte[] packed = write(source, template);
        final ExplicitFieldsRecord target = new ExplicitFieldsRecord();
        target.notListedText = "default value";
        final ExplicitFieldsRecord unpacked = read(packed, target, template);

        assertThat(unpacked.includedNumber).isEqualTo(7);
        assertThat(unpacked.includedText).isEqualTo("listed-field");
        assertThat(unpacked.notListedText).isEqualTo("default value");
    }

    private static ReflectionTemplateBuilder newReflectionTemplateBuilder() {
        return new ReflectionTemplateBuilder(new TemplateRegistry(null));
    }

    private static <T> byte[] write(T source, Template<T> template) throws IOException {
        final BufferPacker packer = new MessagePackBufferPacker(null);
        template.write(packer, source);
        return packer.toByteArray();
    }

    private static <T> T read(byte[] packed, T target, Template<T> template) throws IOException {
        final BufferUnpacker unpacker = new MessagePackBufferUnpacker(null).wrap(packed);
        return template.read(unpacker, target);
    }

    public static class AutoFieldsBase {
        public int baseNumber;
    }

    public static class AutoFieldsChild extends AutoFieldsBase {
        public String childText;
    }

    public static class ExplicitFieldsRecord {
        public int includedNumber;

        public String includedText;

        public String notListedText;
    }
}
