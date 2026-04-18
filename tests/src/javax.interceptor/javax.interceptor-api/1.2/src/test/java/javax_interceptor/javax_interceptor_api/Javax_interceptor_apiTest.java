/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_interceptor.javax_interceptor_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.ExcludeClassInterceptors;
import javax.interceptor.ExcludeDefaultInterceptors;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.Interceptors;
import javax.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;

class Javax_interceptor_apiTest {
    @Test
    void annotationsExposeExpectedRuntimeMetadata() {
        assertRuntimeAnnotationContract(Interceptor.class, true, ElementType.TYPE);
        assertRuntimeAnnotationContract(InterceptorBinding.class, true, ElementType.ANNOTATION_TYPE);
        assertRuntimeAnnotationContract(AroundInvoke.class, false, ElementType.METHOD);
        assertRuntimeAnnotationContract(AroundTimeout.class, false, ElementType.METHOD);
        assertRuntimeAnnotationContract(AroundConstruct.class, false, ElementType.METHOD);
        assertRuntimeAnnotationContract(ExcludeClassInterceptors.class, false, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertRuntimeAnnotationContract(ExcludeDefaultInterceptors.class, false, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertRuntimeAnnotationContract(Interceptors.class, false, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
    }

    @Test
    void interceptorAnnotationsCanBeReadFromAnnotatedTypesMembersAndConstructors() throws Exception {
        Logged logged = InterceptedComponent.class.getAnnotation(Logged.class);
        assertThat(logged).isNotNull();
        assertThat(logged.value()).isEqualTo("component");
        assertThat(logged.annotationType()).isSameAs(Logged.class);
        assertThat(Logged.class.getAnnotation(InterceptorBinding.class)).isNotNull();

        Interceptor interceptor = ExampleInterceptor.class.getAnnotation(Interceptor.class);
        assertThat(interceptor).isNotNull();
        assertThat(interceptor.annotationType()).isSameAs(Interceptor.class);

        assertThat(ExampleInterceptor.class.getDeclaredMethod("aroundInvoke", InvocationContext.class)
                .getAnnotation(AroundInvoke.class))
                .isNotNull();
        assertThat(ExampleInterceptor.class.getDeclaredMethod("aroundTimeout", InvocationContext.class)
                .getAnnotation(AroundTimeout.class))
                .isNotNull();
        assertThat(ExampleInterceptor.class.getDeclaredMethod("aroundConstruct", InvocationContext.class)
                .getAnnotation(AroundConstruct.class))
                .isNotNull();

        Interceptors typeInterceptors = InterceptedComponent.class.getAnnotation(Interceptors.class);
        assertThat(typeInterceptors).isNotNull();
        assertThat(typeInterceptors.value()).containsExactly(ExampleInterceptor.class, SecondaryInterceptor.class);
        assertThat(typeInterceptors.annotationType()).isSameAs(Interceptors.class);
        assertThat(InterceptedComponent.class.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();

        Constructor<InterceptedComponent> constructor = InterceptedComponent.class.getDeclaredConstructor(String.class);
        Interceptors constructorInterceptors = constructor.getAnnotation(Interceptors.class);
        assertThat(constructorInterceptors).isNotNull();
        assertThat(constructorInterceptors.value()).containsExactly(SecondaryInterceptor.class);
        assertThat(constructor.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();

        Method method = InterceptedComponent.class.getDeclaredMethod("businessMethod", String.class, Integer.class);
        Interceptors methodInterceptors = method.getAnnotation(Interceptors.class);
        assertThat(methodInterceptors).isNotNull();
        assertThat(methodInterceptors.value()).containsExactly(ExampleInterceptor.class);
        assertThat(method.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(method.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
    }

    @Test
    void invocationContextSupportsMethodInvocationStateAndProceed() throws Exception {
        InterceptedComponent target = new InterceptedComponent("method-target");
        Method method = InterceptedComponent.class.getDeclaredMethod("businessMethod", String.class, Integer.class);
        Map<String, Object> contextData = new LinkedHashMap<>();
        Object timer = new Object();
        AtomicInteger proceeds = new AtomicInteger();
        AtomicReference<RecordingInvocationContext> invocationContextRef = new AtomicReference<>();
        RecordingInvocationContext invocationContext = new RecordingInvocationContext(
                target,
                timer,
                method,
                null,
                new Object[] { "initial", 1 },
                contextData,
                () -> {
                    proceeds.incrementAndGet();
                    Object[] parameters = invocationContextRef.get().getParameters();
                    return parameters[0] + ":" + parameters[1] + ":" + target.name;
                });

        invocationContextRef.set(invocationContext);

        assertThat(invocationContext.getTarget()).isSameAs(target);
        assertThat(invocationContext.getTimer()).isSameAs(timer);
        assertThat(invocationContext.getMethod()).isSameAs(method);
        assertThat(invocationContext.getConstructor()).isNull();
        assertThat(invocationContext.getParameters()).containsExactly("initial", 1);

        invocationContext.setParameters(new Object[] { "updated", 2 });
        invocationContext.getContextData().put("step", "method");

        assertThat(invocationContext.getParameters()).containsExactly("updated", 2);
        assertThat(invocationContext.getContextData()).containsEntry("step", "method");
        assertThat(invocationContext.proceed()).isEqualTo("updated:2:method-target");
        assertThat(proceeds).hasValue(1);
    }

    @Test
    void invocationContextCanRepresentConstructorInvocation() throws Exception {
        Constructor<InterceptedComponent> constructor = InterceptedComponent.class.getDeclaredConstructor(String.class);
        Map<String, Object> contextData = new LinkedHashMap<>();
        AtomicInteger proceeds = new AtomicInteger();
        AtomicReference<RecordingInvocationContext> invocationContextRef = new AtomicReference<>();
        RecordingInvocationContext invocationContext = new RecordingInvocationContext(
                null,
                null,
                null,
                constructor,
                new Object[] { "constructed" },
                contextData,
                () -> {
                    proceeds.incrementAndGet();
                    Object[] parameters = invocationContextRef.get().getParameters();
                    return new InterceptedComponent((String) parameters[0]);
                });

        invocationContextRef.set(invocationContext);

        assertThat(invocationContext.getTarget()).isNull();
        assertThat(invocationContext.getTimer()).isNull();
        assertThat(invocationContext.getMethod()).isNull();
        assertThat(invocationContext.getConstructor()).isSameAs(constructor);
        assertThat(invocationContext.getParameters()).containsExactly("constructed");

        invocationContext.getContextData().put("step", "constructor");
        InterceptedComponent created = (InterceptedComponent) invocationContext.proceed();

        assertThat(created.name).isEqualTo("constructed");
        assertThat(invocationContext.getContextData()).containsEntry("step", "constructor");
        assertThat(proceeds).hasValue(1);
    }

    @Test
    void interceptorPriorityConstantsAreOrdered() {
        assertThat(Interceptor.Priority.PLATFORM_BEFORE).isEqualTo(0);
        assertThat(Interceptor.Priority.LIBRARY_BEFORE).isEqualTo(1000);
        assertThat(Interceptor.Priority.APPLICATION).isEqualTo(2000);
        assertThat(Interceptor.Priority.LIBRARY_AFTER).isEqualTo(3000);
        assertThat(Interceptor.Priority.PLATFORM_AFTER).isEqualTo(4000);
        assertThat(new int[] {
                Interceptor.Priority.PLATFORM_BEFORE,
                Interceptor.Priority.LIBRARY_BEFORE,
                Interceptor.Priority.APPLICATION,
                Interceptor.Priority.LIBRARY_AFTER,
                Interceptor.Priority.PLATFORM_AFTER
        }).isSorted();
    }

    private static void assertRuntimeAnnotationContract(
            Class<? extends Annotation> annotationType,
            boolean documented,
            ElementType... expectedTargets) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
        if (documented) {
            assertThat(annotationType.getAnnotation(Documented.class)).isNotNull();
        }
        else {
            assertThat(annotationType.getAnnotation(Documented.class)).isNull();
        }
    }


    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    private @interface Logged {
        String value();
    }

    @Interceptor
    @Logged("interceptor")
    private static final class ExampleInterceptor {
        @AroundConstruct
        public Object aroundConstruct(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }

        @AroundInvoke
        public Object aroundInvoke(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }

        @AroundTimeout
        public Object aroundTimeout(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }
    }

    @Interceptor
    private static final class SecondaryInterceptor {
    }

    @Logged("component")
    @Interceptors({ ExampleInterceptor.class, SecondaryInterceptor.class })
    @ExcludeDefaultInterceptors
    private static final class InterceptedComponent {
        private final String name;

        @ExcludeClassInterceptors
        @Interceptors(SecondaryInterceptor.class)
        private InterceptedComponent(String name) {
            this.name = name;
        }

        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        @Interceptors(ExampleInterceptor.class)
        public String businessMethod(String prefix, Integer count) {
            return prefix + count + name;
        }
    }

    @FunctionalInterface
    private interface ProceedAction {
        Object proceed() throws Exception;
    }

    private static final class RecordingInvocationContext implements InvocationContext {
        private final Object target;
        private final Object timer;
        private final Method method;
        private final Constructor<?> constructor;
        private final Map<String, Object> contextData;
        private final ProceedAction proceedAction;
        private Object[] parameters;

        private RecordingInvocationContext(
                Object target,
                Object timer,
                Method method,
                Constructor<?> constructor,
                Object[] parameters,
                Map<String, Object> contextData,
                ProceedAction proceedAction) {
            this.target = target;
            this.timer = timer;
            this.method = method;
            this.constructor = constructor;
            this.parameters = parameters;
            this.contextData = contextData;
            this.proceedAction = proceedAction;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Object getTimer() {
            return timer;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Constructor<?> getConstructor() {
            return constructor;
        }

        @Override
        public Object[] getParameters() {
            return parameters;
        }

        @Override
        public void setParameters(Object[] params) {
            this.parameters = params;
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() throws Exception {
            return proceedAction.proceed();
        }
    }
}
