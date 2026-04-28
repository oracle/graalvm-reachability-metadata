/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationProcessorHiderInnerAnnotationProcessorTest {

    @Test
    void createsWrappedProcessorAndInitializesIt() throws Exception {
        Class<?> processorClass = Class.forName("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
        Constructor<?> constructor = processorClass.getDeclaredConstructor();
        constructor.setAccessible(true);

        AbstractProcessor processor = (AbstractProcessor) constructor.newInstance();
        processor.init(new MinimalProcessingEnvironment());

        assertThat(processor.getSupportedAnnotationTypes()).contains("*");
        assertThat(processor.getSupportedSourceVersion()).isNotNull();
    }

    private static final class MinimalProcessingEnvironment implements ProcessingEnvironment {
        private final Messager messager = new NoOpMessager();

        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return messager;
        }

        @Override
        public Filer getFiler() {
            return null;
        }

        @Override
        public Elements getElementUtils() {
            return null;
        }

        @Override
        public Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public Locale getLocale() {
            return Locale.getDefault();
        }
    }

    private static final class NoOpMessager implements Messager {
        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
        }

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a, AnnotationValue v) {
        }
    }
}
