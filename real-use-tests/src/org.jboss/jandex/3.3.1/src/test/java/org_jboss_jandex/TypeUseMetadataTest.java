/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.TypeTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises Jandex's type-use metadata subsystem to build an application message validation plan.
public class TypeUseMetadataTest {
    @Test
    @Timeout(60)
    public void discoversValidationTargetsOnAnApplicationMessageContract() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(MessageEndpoint.class);
        Index index = indexer.complete();

        assertEquals(List.of("Header:METHOD_PARAMETER", "Payload:METHOD_PARAMETER", "Retryable:THROWS"),
                List.of(
                        annotationTarget(index, Header.class),
                        annotationTarget(index, Payload.class),
                        annotationTarget(index, Retryable.class)));
        assertEquals(List.of("java.lang.String", "java.lang.String", "java.io.IOException"),
                List.of(
                        annotationType(index, Header.class),
                        annotationType(index, Payload.class),
                        annotationType(index, Retryable.class)));
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static String annotationTarget(Index index, Class<? extends java.lang.annotation.Annotation> annotationType) {
        AnnotationInstance annotation = index.getAnnotations(DotName.createSimple(annotationType)).stream()
                .findFirst()
                .orElseThrow();
        TypeTarget target = assertInstanceOf(TypeTarget.class, annotation.target());
        return annotationType.getSimpleName() + ":" + target.usage();
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static String annotationType(Index index, Class<? extends java.lang.annotation.Annotation> annotationType) {
        AnnotationInstance annotation = index.getAnnotations(DotName.createSimple(annotationType)).stream()
                .findFirst()
                .orElseThrow();
        return annotation.target().asType().target().name().toString();
    }

    private static final class MessageEndpoint {
        public String decode(@Header String requestId, @Payload String payload) throws @Retryable IOException {
            return requestId + ":" + payload;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    private @interface Header {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    private @interface Payload {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    private @interface Retryable {
    }
}
