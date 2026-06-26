/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_instrumentation_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class Opentelemetry_instrumentation_annotationsTest {
    @Test
    void annotationTypesDeclareRuntimeRetentionAndExpectedTargets() {
        assertRuntimeAnnotation(WithSpan.class, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertRuntimeAnnotation(
                AddingSpanAttributes.class, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertRuntimeAnnotation(SpanAttribute.class, ElementType.PARAMETER);
    }

    @Test
    void interfaceMethodAnnotationsExposeSpanConfigurationAndParameterAttributes()
            throws NoSuchMethodException {
        Method method = InstrumentedClient.class.getDeclaredMethod("send", String.class, String.class);

        WithSpan withSpan = method.getAnnotation(WithSpan.class);

        assertThat(withSpan).isNotNull();
        assertThat(withSpan.value()).isEqualTo("message.send");
        assertThat(withSpan.kind()).isEqualTo(SpanKind.PRODUCER);
        assertThat(method.getParameters()[0].getAnnotation(SpanAttribute.class).value())
                .isEqualTo("message.destination");
        assertThat(method.getParameters()[1].getAnnotation(SpanAttribute.class).value())
                .isEqualTo("message.id");
    }

    @Test
    void withSpanDefaultValuesAreAvailableFromAnnotatedMethod() throws NoSuchMethodException {
        Method method =
                InstrumentedOperations.class.getDeclaredMethod(
                        "operationWithDefaults", String.class);

        WithSpan withSpan = method.getAnnotation(WithSpan.class);

        assertThat(withSpan).isNotNull();
        assertThat(withSpan.value()).isEmpty();
        assertThat(withSpan.kind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(withSpan.inheritContext()).isTrue();
    }

    @Test
    void methodAnnotationsExposeConfiguredSpanNameKindContextAndParameterAttributes()
            throws NoSuchMethodException {
        Method method = InstrumentedOperations.class.getDeclaredMethod(
                "clientOperation", String.class, long.class, boolean.class);

        WithSpan withSpan = method.getAnnotation(WithSpan.class);

        assertThat(withSpan).isNotNull();
        assertThat(withSpan.value()).isEqualTo("inventory.lookup");
        assertThat(withSpan.kind()).isEqualTo(SpanKind.CLIENT);
        assertThat(withSpan.inheritContext()).isFalse();

        SpanAttribute firstParameterAttribute =
                method.getParameters()[0].getAnnotation(SpanAttribute.class);
        SpanAttribute secondParameterAttribute =
                method.getParameters()[1].getAnnotation(SpanAttribute.class);

        assertThat(firstParameterAttribute).isNotNull();
        assertThat(firstParameterAttribute.value()).isEqualTo("item.id");
        assertThat(secondParameterAttribute).isNotNull();
        assertThat(secondParameterAttribute.value()).isEqualTo("item.quantity");
        assertThat(method.getParameters()[2].isAnnotationPresent(SpanAttribute.class)).isFalse();
    }

    @Test
    void spanAttributeDefaultNameCanBeReadFromParameterAnnotation() throws NoSuchMethodException {
        Method method =
                InstrumentedOperations.class.getDeclaredMethod(
                        "operationWithDefaults", String.class);

        SpanAttribute spanAttribute = method.getParameters()[0].getAnnotation(SpanAttribute.class);

        assertThat(spanAttribute).isNotNull();
        assertThat(spanAttribute.value()).isEmpty();
    }

    @Test
    void addingSpanAttributesMarkerIsRuntimeVisibleOnMethodAndConstructor()
            throws NoSuchMethodException {
        Method method =
                InstrumentedOperations.class.getDeclaredMethod(
                        "addAttributes", String.class, int.class);
        Constructor<InstrumentedOperations> constructor =
                InstrumentedOperations.class.getDeclaredConstructor(String.class);

        assertThat(method.isAnnotationPresent(AddingSpanAttributes.class)).isTrue();
        assertThat(constructor.isAnnotationPresent(AddingSpanAttributes.class)).isTrue();

        SpanAttribute constructorAttribute =
                constructor.getParameters()[0].getAnnotation(SpanAttribute.class);
        assertThat(constructorAttribute).isNotNull();
        assertThat(constructorAttribute.value()).isEqualTo("component.name");
    }

    @Test
    void constructorWithSpanSupportsServerSpansAndExplicitContextInheritance()
            throws NoSuchMethodException {
        Constructor<ServerHandler> constructor =
                ServerHandler.class.getDeclaredConstructor(String.class);

        WithSpan withSpan = constructor.getAnnotation(WithSpan.class);

        assertThat(withSpan).isNotNull();
        assertThat(withSpan.value()).isEqualTo("server.handler");
        assertThat(withSpan.kind()).isEqualTo(SpanKind.SERVER);
        assertThat(withSpan.inheritContext()).isTrue();
        assertThat(constructor.getParameters()[0].getAnnotation(SpanAttribute.class).value())
                .isEqualTo("handler.route");
    }

    @Test
    void annotationsDoNotAlterConstructorOrMethodExecutionWithoutInstrumentation() {
        AnnotatedCalculator calculator = new AnnotatedCalculator("orders");

        assertThat(calculator.lookup("book", 3)).isEqualTo("orders:book:3");
        assertThat(calculator.enrich("request-7", 201)).isEqualTo("request-7=201");
    }

    private static void assertRuntimeAnnotation(
            Class<? extends Annotation> annotationType, ElementType... targets) {
        assertThat(annotationType.isAnnotation()).isTrue();

        Retention retention = annotationType.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);

        Target target = annotationType.getAnnotation(Target.class);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactlyInAnyOrder(targets);
    }

    public interface InstrumentedClient {
        @WithSpan(value = "message.send", kind = SpanKind.PRODUCER)
        void send(
                @SpanAttribute("message.destination") String destination,
                @SpanAttribute("message.id") String messageId);
    }

    public static final class InstrumentedOperations {
        @AddingSpanAttributes
        public InstrumentedOperations(@SpanAttribute("component.name") String componentName) {}

        @WithSpan
        public String operationWithDefaults(@SpanAttribute String value) {
            return value;
        }

        @WithSpan(value = "inventory.lookup", kind = SpanKind.CLIENT, inheritContext = false)
        public long clientOperation(
                @SpanAttribute("item.id") String itemId,
                @SpanAttribute("item.quantity") long quantity,
                boolean cached) {
            return cached ? 0 : quantity + itemId.length();
        }

        @AddingSpanAttributes
        public void addAttributes(@SpanAttribute("request.id") String requestId, int statusCode) {}
    }

    public static final class ServerHandler {
        @WithSpan(value = "server.handler", kind = SpanKind.SERVER, inheritContext = true)
        public ServerHandler(@SpanAttribute("handler.route") String route) {}
    }

    public static final class AnnotatedCalculator {
        private final String namespace;

        @WithSpan("calculator.create")
        public AnnotatedCalculator(@SpanAttribute("calculator.namespace") String namespace) {
            this.namespace = namespace;
        }

        @WithSpan(value = "calculator.lookup", kind = SpanKind.CONSUMER)
        public String lookup(
                @SpanAttribute("item.name") String itemName,
                @SpanAttribute("item.count") int itemCount) {
            return namespace + ":" + itemName + ":" + itemCount;
        }

        @AddingSpanAttributes
        public String enrich(
                @SpanAttribute("request.id") String requestId,
                @SpanAttribute("http.response.status_code") int statusCode) {
            return requestId + "=" + statusCode;
        }
    }
}
