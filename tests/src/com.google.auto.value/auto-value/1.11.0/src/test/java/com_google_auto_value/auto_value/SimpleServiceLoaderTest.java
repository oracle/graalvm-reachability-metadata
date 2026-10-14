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
import com.google.auto.value.processor.SimpleServiceLoader;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.Processor;
import org.junit.jupiter.api.Test;

public class SimpleServiceLoaderTest {
    @Test
    void discoversAndConstructsRegisteredProcessors() {
        Set<Class<?>> processorTypes = new HashSet<>();
        for (Processor processor :
                SimpleServiceLoader.load(Processor.class, AutoValueProcessor.class.getClassLoader())) {
            processorTypes.add(processor.getClass());
        }

        assertThat(processorTypes)
                .contains(
                        AutoValueProcessor.class,
                        AutoBuilderProcessor.class,
                        AutoOneOfProcessor.class,
                        AutoAnnotationProcessor.class);
    }
}
