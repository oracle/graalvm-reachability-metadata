/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.auto.value.processor.AutoBuilderProcessor;
import com.google.auto.value.processor.AutoOneOfProcessor;
import com.google.auto.value.processor.AutoValueProcessor;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.Test;

public class ProcessorApiTest {
    @Test
    void exposesSupportedAnnotationsAndSourceVersions() {
        assertProcessor(new AutoValueProcessor(), "com.google.auto.value.AutoValue");
        assertProcessor(new AutoBuilderProcessor(), "com.google.auto.value.AutoBuilder");
        assertProcessor(new AutoOneOfProcessor(), "com.google.auto.value.AutoOneOf");
        assertProcessor(new AutoAnnotationProcessor(), "com.google.auto.value.AutoAnnotation");
    }

    private static void assertProcessor(Processor processor, String supportedAnnotation) {
        assertThat(processor.getSupportedAnnotationTypes()).isEqualTo(Set.of(supportedAnnotation));
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latestSupported());
    }
}
