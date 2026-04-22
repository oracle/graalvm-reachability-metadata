/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

import org.junit.jupiter.api.Test;

public class AnnotationProcessorHiderAnnotationProcessorDynamicAccessTest {
    @Test
    void annotationProcessorCreatesWrappedProcessorAndInitializesIt() throws Throwable {
        Processor processor = (Processor) LombokLaunchTestSupport.newInstance(
                "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
                new Class<?>[0]);

        processor.init(new StubProcessingEnvironment());

        assertThat(processor.getSupportedAnnotationTypes()).contains("*");
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latest());
    }

    private static final class StubProcessingEnvironment implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return Collections.emptyMap();
        }

        @Override
        public Messager getMessager() {
            return NoOpMessager.INSTANCE;
        }

        @Override
        public javax.annotation.processing.Filer getFiler() {
            return null;
        }

        @Override
        public javax.lang.model.util.Elements getElementUtils() {
            return null;
        }

        @Override
        public javax.lang.model.util.Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    private enum NoOpMessager implements Messager {
        INSTANCE;

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element element, AnnotationMirror annotation) {
        }

        @Override
        public void printMessage(
                Diagnostic.Kind kind,
                CharSequence msg,
                Element element,
                AnnotationMirror annotation,
                AnnotationValue annotationValue) {
        }
    }
}
