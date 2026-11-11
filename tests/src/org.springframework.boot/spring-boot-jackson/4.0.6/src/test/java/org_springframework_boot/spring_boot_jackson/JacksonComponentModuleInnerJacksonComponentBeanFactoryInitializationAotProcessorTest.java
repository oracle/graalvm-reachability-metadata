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
import org.springframework.aot.generate.InMemoryGeneratedFiles;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import tools.jackson.databind.ValueSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonComponentModuleInnerJacksonComponentBeanFactoryInitializationAotProcessorTest {
    @Test
    void applicationAotProcessingRegistersOnlyConcreteNestedCodecs() {
        RuntimeHints hints = new RuntimeHints();
        DefaultGenerationContext generationContext = new DefaultGenerationContext(
                new ClassNameGenerator(ClassName.get(getClass())), new InMemoryGeneratedFiles(), hints);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("productCodeCodec", JacksonComponentModuleTest.ProductCodeComponent.class);
            context.registerBean("otherComponent", NonCodecComponent.class);

            new ApplicationContextAotGenerator().processAheadOfTime(context, generationContext);
        }

        assertThat(hints.reflection().getTypeHint(JacksonComponentModuleTest.ProductCodeComponent.class))
                .isNotNull();
        assertThat(hints.reflection().getTypeHint(JacksonComponentModuleTest.ProductCodeComponent.Serializer.class))
                .isNotNull().satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection().getTypeHint(JacksonComponentModuleTest.ProductCodeComponent.Deserializer.class))
                .isNotNull().satisfies(hint -> assertThat(hint.getMemberCategories())
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertThat(hints.reflection().getTypeHint(NonCodecComponent.Description.class)).isNull();
        assertThat(hints.reflection().getTypeHint(NonCodecComponent.AbstractSerializer.class)).isNull();
    }

    @JacksonComponent
    public static class NonCodecComponent {
        public static class Description {
        }

        public abstract static class AbstractSerializer extends ValueSerializer<JacksonComponentModuleTest.ProductCode> {
        }
    }
}
