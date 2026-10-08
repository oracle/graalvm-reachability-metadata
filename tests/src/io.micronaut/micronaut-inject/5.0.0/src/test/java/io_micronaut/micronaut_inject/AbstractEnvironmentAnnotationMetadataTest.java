/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AbstractEnvironmentAnnotationMetadataTest {
    @Test
    void synthesizesEnvironmentAwareRepeatedAnnotations() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addRepeatable(
                EnvironmentTag.class.getName(),
                AnnotationValue.builder(EnvironmentTag.class).member("value", "primary").build());
        metadata.addDeclaredRepeatable(
                EnvironmentTag.class.getName(),
                AnnotationValue.builder(EnvironmentTag.class).member("value", "declared").build());

        try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {
            EnvironmentMetadata environmentMetadata =
                    new EnvironmentMetadata(metadata, context.getEnvironment());

            assertThat(environmentMetadata.synthesizeAnnotationsByType(EnvironmentTag.class))
                    .extracting(EnvironmentTag::value)
                    .containsExactly("primary", "declared");
            assertThat(
                            environmentMetadata.synthesizeDeclaredAnnotationsByType(
                                    EnvironmentTag.class))
                    .extracting(EnvironmentTag::value)
                    .containsExactly("declared");
        }
    }

    private static final class EnvironmentMetadata extends AbstractEnvironmentAnnotationMetadata {
        private final Environment environment;

        private EnvironmentMetadata(AnnotationMetadata metadata, Environment environment) {
            super(metadata);
            this.environment = environment;
        }

        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(EnvironmentTags.class)
    public @interface EnvironmentTag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface EnvironmentTags {
        EnvironmentTag[] value();
    }
}
