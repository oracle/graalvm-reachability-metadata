/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ShadowClassLoaderTest {

    private static final String LOMBOK_ANNOTATION_PROCESSOR =
            "lombok.launch.AnnotationProcessorHider$AnnotationProcessor";

    @Test
    void serviceLoaderBootstrapsLombokAnnotationProcessor() {
        try {
            final Processor processor = findLombokAnnotationProcessor();

            final Set<String> supportedAnnotationTypes = processor.getSupportedAnnotationTypes();
            final SourceVersion supportedSourceVersion = processor.getSupportedSourceVersion();

            assertThat(supportedAnnotationTypes).contains("*");
            assertThat(supportedSourceVersion).isNotNull();
        } catch (ServiceConfigurationError error) {
            if (!isUnsupportedNativeClassLoading(error)) {
                throw error;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Processor findLombokAnnotationProcessor() {
        final ServiceLoader<Processor> processors = ServiceLoader.load(Processor.class);
        for (Processor processor : processors) {
            if (LOMBOK_ANNOTATION_PROCESSOR.equals(processor.getClass().getName())) {
                return processor;
            }
        }
        throw new AssertionError("Lombok annotation processor service was not discovered");
    }

    private static boolean isUnsupportedNativeClassLoading(ServiceConfigurationError error) {
        final Throwable cause = error.getCause();
        return cause instanceof Error causeError
                && NativeImageSupport.isUnsupportedFeatureError(causeError);
    }
}
