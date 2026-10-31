/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.boot.jackson.JsonComponentModule;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonComponentModuleInnerJsonComponentBeanFactoryInitializationAotProcessorTest {

    @Test
    void registersNestedSerializerHintsDuringApplicationAotProcessing() {
        RuntimeHints hints = new RuntimeHints();
        DefaultGenerationContext generation = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(MailJson.class)), new InMemoryGeneratedFiles(), hints);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(MailJson.class);
            context.registerBean(JsonComponentModule.class);

            new ApplicationContextAotGenerator().processAheadOfTime(context, generation);

            assertThat(hints.reflection().getTypeHint(MailJson.class)).isNotNull();
            assertThat(hints.reflection().getTypeHint(MailJson.class).getMemberCategories())
                    .contains(MemberCategory.DECLARED_CLASSES);
            assertThat(hints.reflection().getTypeHint(MailJson.Serializer.class)).isNotNull();
            assertThat(hints.reflection().getTypeHint(MailJson.Serializer.class).getMemberCategories())
                    .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }

    @Test
    void moduleUsesTheDiscoveredNestedSerializer() throws IOException {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(MailJson.class);
            context.registerBean(JsonComponentModule.class);
            context.refresh();
            ObjectMapper mapper = new ObjectMapper().registerModule(context.getBean(JsonComponentModule.class));

            assertThat(mapper.writeValueAsString(new Mailbox("support"))).isEqualTo("\"mailbox:support\"");
        }
    }

    public record Mailbox(String name) {
    }

    @JsonComponent
    public static class MailJson {

        public static class Serializer extends JsonSerializer<Mailbox> {

            @Override
            public void serialize(Mailbox value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString("mailbox:" + value.name());
            }
        }
    }
}
