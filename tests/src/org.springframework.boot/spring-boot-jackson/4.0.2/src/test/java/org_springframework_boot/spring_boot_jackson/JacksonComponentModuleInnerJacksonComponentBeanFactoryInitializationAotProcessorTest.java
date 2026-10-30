/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonComponentModuleInnerJacksonComponentBeanFactoryInitializationAotProcessorTest {
    @Test
    void contributesConstructorHintsForNestedJacksonCodecs() {
        RuntimeHints hints = new RuntimeHints();
        GenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(getClass())), new InMemoryGeneratedFiles(), hints);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("referenceCodecs", ReferenceCodecs.class);
            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        }

        assertThat(hints.reflection().getTypeHint(ReferenceCodecs.class)).isNotNull();
        assertThat(hints.reflection().getTypeHint(ReferenceCodecs.Serializer.class)).isNotNull()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection().getTypeHint(ReferenceCodecs.Deserializer.class)).isNotNull()
                .satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection().getTypeHint(ReferenceCodecs.Description.class)).isNull();
    }

    public record Reference(String value) {
    }

    @JacksonComponent
    public static class ReferenceCodecs {
        public static class Serializer extends ValueSerializer<Reference> {
            @Override
            public void serialize(Reference value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(value.value());
            }
        }

        public static class Deserializer extends ValueDeserializer<Reference> {
            @Override
            public Reference deserialize(JsonParser parser, DeserializationContext context) {
                return new Reference(parser.getString());
            }
        }

        public static class Description {
        }
    }
}
