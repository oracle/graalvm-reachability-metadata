/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_spec_javax_interceptor.jboss_interceptors_api_1_1_spec;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.interceptor.AroundInvoke;
import javax.interceptor.AroundTimeout;
import javax.interceptor.ExcludeClassInterceptors;
import javax.interceptor.ExcludeDefaultInterceptors;
import javax.interceptor.Interceptor;
import javax.interceptor.InterceptorBinding;
import javax.interceptor.Interceptors;
import javax.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Jboss_interceptors_api_1_1_specTest {

    @Test
    void invocationContextExposesTargetMethodParametersContextDataTimerAndProceedResult() throws Exception {
        SampleTarget target = new SampleTarget();
        Method method = SampleTarget.class.getDeclaredMethod("greet", String.class, Integer.class);
        Object[] parameters = new Object[] {"native", 7};
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("traceId", "abc-123");
        AtomicInteger proceedCalls = new AtomicInteger();

        TestInvocationContext invocationContext = new TestInvocationContext(
                target,
                method,
                parameters,
                contextData,
                "scheduled-task",
                currentInvocationContext -> {
                    proceedCalls.incrementAndGet();
                    Object[] currentParameters = currentInvocationContext.getParameters();
                    return target.greet((String) currentParameters[0], (Integer) currentParameters[1]);
                }
        );

        assertThat(invocationContext.getTarget()).isSameAs(target);
        assertThat(invocationContext.getMethod()).isEqualTo(method);
        assertThat(invocationContext.getParameters()).containsExactly("native", 7);
        assertThat(invocationContext.getContextData()).containsEntry("traceId", "abc-123");
        assertThat(invocationContext.getTimer()).isEqualTo("scheduled-task");

        Object[] updatedParameters = new Object[] {"image", 9};
        invocationContext.setParameters(updatedParameters);
        assertThat(invocationContext.getParameters()).containsExactly("image", 9);

        Object proceedResult = invocationContext.proceed();
        assertThat(proceedResult).isEqualTo("image-9");
        assertThat(proceedCalls).hasValue(1);
    }

    @Test
    void interceptorAnnotationsCanBeAppliedAndReadFromTypesAndMethods() throws Exception {
        Method aroundInvokeMethod = SampleInterceptor.class.getDeclaredMethod("aroundInvoke", InvocationContext.class);
        Method aroundTimeoutMethod = SampleInterceptor.class.getDeclaredMethod("aroundTimeout", InvocationContext.class);
        Method interceptedMethod = InterceptedService.class.getDeclaredMethod("work");
        Class<SampleInterceptor> sampleInterceptorAnnotationAccess = SampleInterceptor.class;
        Class<InterceptedService> interceptedServiceAnnotationAccess = InterceptedService.class;
        Method interceptedMethodAnnotationAccess = interceptedMethod;
        Method aroundInvokeMethodAnnotationAccess = aroundInvokeMethod;
        Method aroundTimeoutMethodAnnotationAccess = aroundTimeoutMethod;

        assertThat(sampleInterceptorAnnotationAccess.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(sampleInterceptorAnnotationAccess.getAnnotation(SampleBinding.class)).isNotNull();
        assertThat(interceptedServiceAnnotationAccess.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(interceptedMethodAnnotationAccess.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(aroundInvokeMethodAnnotationAccess.getAnnotation(AroundInvoke.class)).isNotNull();
        assertThat(aroundTimeoutMethodAnnotationAccess.getAnnotation(AroundTimeout.class)).isNotNull();

        Interceptors classLevelInterceptors = interceptedServiceAnnotationAccess.getAnnotation(Interceptors.class);
        Interceptors methodLevelInterceptors = interceptedMethodAnnotationAccess.getAnnotation(Interceptors.class);

        assertThat(classLevelInterceptors).isNotNull();
        assertThat(classLevelInterceptors.value()).containsExactly(SampleInterceptor.class, SecondaryInterceptor.class);
        assertThat(methodLevelInterceptors).isNotNull();
        assertThat(methodLevelInterceptors.value()).containsExactly(SecondaryInterceptor.class);
    }

    @Test
    void interceptorBindingMembersAndMethodLevelDefaultExclusionAreRetainedAtRuntime() throws Exception {
        Method method = MethodBoundService.class.getDeclaredMethod("work");
        Class<MethodBoundService> methodBoundServiceAnnotationAccess = MethodBoundService.class;
        Method methodAnnotationAccess = method;

        ConfigurableBinding classBinding = methodBoundServiceAnnotationAccess.getAnnotation(ConfigurableBinding.class);
        ConfigurableBinding methodBinding = methodAnnotationAccess.getAnnotation(ConfigurableBinding.class);

        assertThat(classBinding).isNotNull();
        assertThat(classBinding.stage()).isEqualTo("default");
        assertThat(classBinding.enabled()).isTrue();
        assertThat(methodBinding).isNotNull();
        assertThat(methodBinding.stage()).isEqualTo("method");
        assertThat(methodBinding.enabled()).isFalse();
        assertThat(methodAnnotationAccess.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
    }

    @Test
    void aroundTimeoutInterceptorCanReadTimerAndShareContextDataWithProceed() throws Exception {
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("attempt", 2);
        AtomicInteger proceedCalls = new AtomicInteger();
        TimeoutAwareInterceptor interceptor = new TimeoutAwareInterceptor();

        TestInvocationContext invocationContext = new TestInvocationContext(
                null,
                null,
                new Object[0],
                contextData,
                "heartbeat",
                currentInvocationContext -> {
                    proceedCalls.incrementAndGet();
                    assertThat(currentInvocationContext.getContextData())
                            .containsEntry("attempt", 2)
                            .containsEntry("timerLabel", "heartbeat");
                    return "completed";
                }
        );

        Object result = interceptor.aroundTimeout(invocationContext);

        assertThat(result).isEqualTo("heartbeat:completed");
        assertThat(contextData).containsEntry("timerLabel", "heartbeat");
        assertThat(proceedCalls).hasValue(1);
    }

    @Test
    void interceptorApiAnnotationsDeclareExpectedRuntimeContracts() {
        assertAnnotationContract(AroundInvoke.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(AroundTimeout.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(ExcludeClassInterceptors.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(ExcludeDefaultInterceptors.class, RetentionPolicy.RUNTIME, false, ElementType.TYPE, ElementType.METHOD);
        assertAnnotationContract(Interceptor.class, RetentionPolicy.RUNTIME, true, ElementType.TYPE);
        assertAnnotationContract(InterceptorBinding.class, RetentionPolicy.RUNTIME, true, ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(Interceptors.class, RetentionPolicy.RUNTIME, false, ElementType.TYPE, ElementType.METHOD);
    }

    private static void assertAnnotationContract(
            Class<? extends Annotation> annotationType,
            RetentionPolicy retentionPolicy,
            boolean documented,
            ElementType... expectedTargets
    ) {
        Class<? extends Annotation> annotationTypeAnnotationAccess = annotationType;
        Retention retention = annotationTypeAnnotationAccess.getAnnotation(Retention.class);
        Target target = annotationTypeAnnotationAccess.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(retentionPolicy);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
        assertThat(annotationTypeAnnotationAccess.isAnnotationPresent(Documented.class)).isEqualTo(documented);
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SampleBinding {
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ConfigurableBinding {

        String stage() default "default";

        boolean enabled() default true;
    }

    @Interceptor
    @SampleBinding
    private static final class SampleInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }

        @AroundTimeout
        Object aroundTimeout(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }
    }

    private static final class SecondaryInterceptor {
    }

    private static final class TimeoutAwareInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext invocationContext) throws Exception {
            invocationContext.getContextData().put("timerLabel", invocationContext.getTimer());
            return invocationContext.getTimer() + ":" + invocationContext.proceed();
        }
    }

    @ExcludeDefaultInterceptors
    @Interceptors({SampleInterceptor.class, SecondaryInterceptor.class})
    private static final class InterceptedService {

        @ExcludeClassInterceptors
        @Interceptors({SecondaryInterceptor.class})
        void work() {
        }
    }

    @ConfigurableBinding
    private static final class MethodBoundService {

        @ConfigurableBinding(stage = "method", enabled = false)
        @ExcludeDefaultInterceptors
        void work() {
        }
    }

    private static final class SampleTarget {

        String greet(String prefix, Integer number) {
            return prefix + "-" + number;
        }
    }

    private interface ProceedAction {
        Object proceed(TestInvocationContext invocationContext) throws Exception;
    }

    private static final class TestInvocationContext implements InvocationContext {

        private final Object target;
        private final Method method;
        private final Map<String, Object> contextData;
        private final Object timer;
        private final ProceedAction proceedAction;
        private Object[] parameters;

        private TestInvocationContext(
                Object target,
                Method method,
                Object[] parameters,
                Map<String, Object> contextData,
                Object timer,
                ProceedAction proceedAction
        ) {
            this.target = target;
            this.method = method;
            this.parameters = parameters;
            this.contextData = contextData;
            this.timer = timer;
            this.proceedAction = proceedAction;
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getParameters() {
            return parameters;
        }

        @Override
        public void setParameters(Object[] parameters) {
            this.parameters = parameters;
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object getTimer() {
            return timer;
        }

        @Override
        public Object proceed() throws Exception {
            return proceedAction.proceed(this);
        }
    }
}
