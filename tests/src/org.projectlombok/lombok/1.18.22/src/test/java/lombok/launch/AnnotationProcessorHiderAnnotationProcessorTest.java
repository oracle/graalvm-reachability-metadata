/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package lombok.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import org.junit.jupiter.api.Test;

class AnnotationProcessorHiderAnnotationProcessorTest {
    @Test
    void createsWrappedAnnotationProcessorAndDelegatesSupportedMetadata() {
        AnnotationProcessorHider.AnnotationProcessor processor = new AnnotationProcessorHider.AnnotationProcessor();

        assertThat(processor.getSupportedOptions()).isNotNull();
        assertThat(processor.getSupportedAnnotationTypes()).contains("*");
        assertThat(processor.getSupportedSourceVersion()).isEqualTo(SourceVersion.latest());
    }

    @Test
    void initMarksLombokAsInvokedAndWarnsForUnsupportedProcessingEnvironments() {
        AnnotationProcessorHider.AstModificationNotifierData.lombokInvoked = false;
        AnnotationProcessorHider.AnnotationProcessor processor = new AnnotationProcessorHider.AnnotationProcessor();
        StubProcessingEnvironment processingEnvironment = new StubProcessingEnvironment();

        processor.init(processingEnvironment);

        assertThat(AnnotationProcessorHider.AstModificationNotifierData.lombokInvoked).isTrue();
        assertThat(processingEnvironment.messager.lastKind).isEqualTo(Kind.WARNING);
        assertThat(processingEnvironment.messager.lastMessage)
                .contains("supported by lombok")
                .contains("OpenJDK javac")
                .contains("ECJ");
    }

    private static final class StubProcessingEnvironment implements ProcessingEnvironment {
        private final RecordingMessager messager = new RecordingMessager();

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
            return SourceVersion.latest();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    private static final class RecordingMessager implements Messager {
        private Kind lastKind;
        private String lastMessage;

        @Override
        public void printMessage(Kind kind, CharSequence message) {
            this.lastKind = kind;
            this.lastMessage = String.valueOf(message);
        }

        @Override
        public void printMessage(Kind kind, CharSequence message, Element element) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(Kind kind, CharSequence message, Element element, AnnotationMirror annotationMirror) {
            printMessage(kind, message);
        }

        @Override
        public void printMessage(
                Kind kind,
                CharSequence message,
                Element element,
                AnnotationMirror annotationMirror,
                AnnotationValue annotationValue) {
            printMessage(kind, message);
        }
    }
}
