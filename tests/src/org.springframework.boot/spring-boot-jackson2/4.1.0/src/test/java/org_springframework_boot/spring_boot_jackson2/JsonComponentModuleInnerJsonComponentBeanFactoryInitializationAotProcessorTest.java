/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_jackson2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.ClassNameGenerator;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.jackson2.JsonComponent;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

public class JsonComponentModuleInnerJsonComponentBeanFactoryInitializationAotProcessorTest {
    @Test
    void registersAotReflectionHintsForInnerJsonComponentClasses() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ComponentWithInnerSerializer.class);
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get("org.example", "TestApplication")), new InMemoryGeneratedFiles());

        try {
            ClassName initializer = new ApplicationContextAotGenerator().processAheadOfTime(applicationContext,
                    generationContext);

            assertThat(initializer).isNotNull();
            TypeHint componentHint = generationContext.getRuntimeHints().reflection()
                    .getTypeHint(ComponentWithInnerSerializer.class);
            TypeHint serializerHint = generationContext.getRuntimeHints().reflection()
                    .getTypeHint(ComponentWithInnerSerializer.Serializer.class);
            assertThat(componentHint).isNotNull();
            assertThat(serializerHint).isNotNull();
            assertThat(serializerHint.getMemberCategories())
                    .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
        finally {
            applicationContext.close();
        }
    }

    @JsonComponent
    public static class ComponentWithInnerSerializer {
        public static class Serializer extends JsonSerializer<Example> {
            @Override
            public void serialize(Example value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(value.value());
            }
        }
    }

    private record Example(String value) {
    }
}
