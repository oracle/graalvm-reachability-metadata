/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProcessorHiderInnerAnnotationProcessorTest {
    private static final String PROCESSOR_CLASS_NAME = "lombok.launch.AnnotationProcessorHider$AnnotationProcessor";

    @Test
    void serviceLoadedProcessorInitializesWrappedLombokProcessor() {
        try {
            Processor processor = loadLombokAnnotationProcessor();

            assertThat(processor.getSupportedAnnotationTypes()).contains("*");
            assertThat(processor.getSupportedSourceVersion()).isNotNull();

            MinimalProcessingEnvironment processingEnvironment = new MinimalProcessingEnvironment();
            processor.init(processingEnvironment);

            assertThat(processingEnvironment.diagnostics())
                    .anySatisfy(diagnostic -> assertThat(diagnostic).contains("compiler supported by lombok"));
        } catch (Error error) {
            if (isUnsupportedDynamicClassLoading(error)) {
                return;
            }
            throw error;
        }
    }

    private static Processor loadLombokAnnotationProcessor() {
        for (Processor processor : ServiceLoader.load(Processor.class)) {
            if (PROCESSOR_CLASS_NAME.equals(processor.getClass().getName())) {
                return processor;
            }
        }
        throw new AssertionError("Could not load Lombok annotation processor service");
    }

    private static boolean isUnsupportedDynamicClassLoading(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return true;
        }
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause instanceof Error causeError && NativeImageSupport.isUnsupportedFeatureError(causeError)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static final class MinimalProcessingEnvironment implements ProcessingEnvironment {
        private final RecordingMessager messager = new RecordingMessager();

        @Override
        public Map<String, String> getOptions() {
            return Collections.emptyMap();
        }

        @Override
        public Messager getMessager() {
            return messager;
        }

        @Override
        public Filer getFiler() {
            throw new UnsupportedOperationException("Filer is not used by this test");
        }

        @Override
        public Elements getElementUtils() {
            throw new UnsupportedOperationException("Elements are not used by this test");
        }

        @Override
        public Types getTypeUtils() {
            throw new UnsupportedOperationException("Types are not used by this test");
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        List<String> diagnostics() {
            return messager.diagnostics();
        }
    }

    private static final class RecordingMessager implements Messager {
        private final List<String> diagnostics = new ArrayList<>();

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message) {
            diagnostics.add(kind + ": " + message);
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence message,
                Element element,
                AnnotationMirror annotationMirror) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence message,
                Element element,
                AnnotationMirror annotationMirror,
                AnnotationValue annotationValue) {
            printMessage(kind, message);
        }

        List<String> diagnostics() {
            return List.copyOf(diagnostics);
        }
    }
}
