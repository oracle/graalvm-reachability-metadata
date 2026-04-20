/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_spec_javax_interceptor.jboss_interceptors_api_1_2_spec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class Jboss_interceptors_api_1_2_specTest {

    @Test
    void interceptorAnnotationsRetainConfiguredValuesAcrossSupportedTargets() throws Exception {
        Constructor<InterceptedComponent> constructor = InterceptedComponent.class.getDeclaredConstructor(String.class);
        Method interceptedMethod = InterceptedComponent.class.getDeclaredMethod("interceptedOperation");
        Method aroundConstructMethod = AuditingInterceptor.class.getDeclaredMethod("aroundConstruct", InvocationContext.class);
        Method aroundInvokeMethod = AuditingInterceptor.class.getDeclaredMethod("aroundInvoke", InvocationContext.class);
        Method aroundTimeoutMethod = AuditingInterceptor.class.getDeclaredMethod("aroundTimeout", InvocationContext.class);

        assertThat(AnnotationAccess.getAnnotation(InterceptedComponent.class, ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(InterceptedComponent.class, Interceptors.class).value())
                .containsExactly(AuditingInterceptor.class, MetricsInterceptor.class);

        assertThat(AnnotationAccess.getAnnotation(constructor, ExcludeClassInterceptors.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(constructor, ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(constructor, Interceptors.class).value())
                .containsExactly(AuditingInterceptor.class);

        assertThat(AnnotationAccess.getAnnotation(interceptedMethod, ExcludeClassInterceptors.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(interceptedMethod, ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(interceptedMethod, Interceptors.class).value())
                .containsExactly(MetricsInterceptor.class);

        assertThat(AnnotationAccess.getAnnotation(Audited.class, InterceptorBinding.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(AuditingInterceptor.class, Interceptor.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(AuditingInterceptor.class, Audited.class)).isNotNull();

        assertThat(AnnotationAccess.getAnnotation(aroundConstructMethod, AroundConstruct.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(aroundInvokeMethod, AroundInvoke.class)).isNotNull();
        assertThat(AnnotationAccess.getAnnotation(aroundTimeoutMethod, AroundTimeout.class)).isNotNull();
    }

    @Test
    void annotationTypesExposeExpectedRetentionTargetAndDocumentationContracts() throws Exception {
        assertRetentionAndTarget(AroundConstruct.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(AroundInvoke.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(AroundTimeout.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(ExcludeClassInterceptors.class, RetentionPolicy.RUNTIME, METHOD, CONSTRUCTOR);
        assertRetentionAndTarget(ExcludeDefaultInterceptors.class, RetentionPolicy.RUNTIME, TYPE, METHOD, CONSTRUCTOR);
        assertRetentionAndTarget(Interceptor.class, RetentionPolicy.RUNTIME, TYPE);
        assertRetentionAndTarget(InterceptorBinding.class, RetentionPolicy.RUNTIME, ANNOTATION_TYPE);
        assertRetentionAndTarget(Interceptors.class, RetentionPolicy.RUNTIME, TYPE, METHOD, CONSTRUCTOR);

        assertThat(AnnotationAccess.isAnnotationPresent(Interceptor.class, Documented.class)).isTrue();
        assertThat(AnnotationAccess.isAnnotationPresent(InterceptorBinding.class, Documented.class)).isTrue();
        assertThat(Interceptors.class.getDeclaredMethod("value").getReturnType()).isEqualTo(Class[].class);
    }

    @Test
    void invocationContextImplementationsSupportConstructorAndMethodFlows() throws Exception {
        Constructor<SampleService> constructor = SampleService.class.getDeclaredConstructor(String.class);
        Method joinMethod = SampleService.class.getDeclaredMethod("join", String.class, int.class);

        SimpleInvocationContext constructorContext = new SimpleInvocationContext(
                null,
                null,
                constructor,
                new Object[]{"native"},
                new LinkedHashMap<>(),
                "constructor-timer",
                context -> constructor.newInstance(context.getParameters()));

        assertThat(constructorContext.getTarget()).isNull();
        assertThat(constructorContext.getMethod()).isNull();
        assertThat(constructorContext.getConstructor()).isEqualTo(constructor);
        assertThat(constructorContext.getParameters()).containsExactly("native");
        assertThat(constructorContext.getContextData()).isEmpty();
        assertThat(constructorContext.getTimer()).isEqualTo("constructor-timer");

        constructorContext.getContextData().put("phase", "construct");
        SampleService constructed = (SampleService) constructorContext.proceed();

        assertThat(constructed.join("image", 2)).isEqualTo("native:imageimage");
        assertThat(constructorContext.getContextData()).containsEntry("phase", "construct");

        SimpleInvocationContext methodContext = new SimpleInvocationContext(
                constructed,
                joinMethod,
                null,
                new Object[]{"metadata", 1},
                new LinkedHashMap<>(),
                25L,
                context -> joinMethod.invoke(constructed, context.getParameters()));

        assertThat(methodContext.getTarget()).isSameAs(constructed);
        assertThat(methodContext.getMethod()).isEqualTo(joinMethod);
        assertThat(methodContext.getConstructor()).isNull();
        assertThat(methodContext.getParameters()).containsExactly("metadata", 1);
        assertThat(methodContext.getTimer()).isEqualTo(25L);

        methodContext.getContextData().put("attempt", 1);
        assertThat(methodContext.proceed()).isEqualTo("native:metadata");

        methodContext.setParameters(new Object[]{"forge", 3});
        assertThat(methodContext.getParameters()).containsExactly("forge", 3);
        assertThat(methodContext.proceed()).isEqualTo("native:forgeforgeforge");
        assertThat(methodContext.getContextData()).containsEntry("attempt", 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> methodContext.setParameters(new Object[]{"forge"}))
                .withMessageContaining("Expected 2 parameters");

        SimpleInvocationContext lifecycleOnlyContext = new SimpleInvocationContext(
                constructed,
                null,
                null,
                null,
                new LinkedHashMap<>(),
                null,
                context -> "done");

        assertThatIllegalStateException().isThrownBy(lifecycleOnlyContext::getParameters)
                .withMessageContaining("Parameters are not available");
        assertThatIllegalStateException().isThrownBy(() -> lifecycleOnlyContext.setParameters(new Object[0]))
                .withMessageContaining("Parameters are not available");
        assertThat(lifecycleOnlyContext.proceed()).isEqualTo("done");
    }

    @Test
    void aroundInvokeInterceptorsCanShareContextDataAndUpdatedParametersAcrossAChain() throws Exception {
        SampleService service = new SampleService("native");
        ContextCapturingInterceptor contextCapturingInterceptor = new ContextCapturingInterceptor();
        RepeatAdjustingInterceptor repeatAdjustingInterceptor = new RepeatAdjustingInterceptor();
        SimpleInvocationContext invocationContext = new SimpleInvocationContext(
                service,
                null,
                null,
                new Object[]{"image", 1},
                new LinkedHashMap<>(),
                null,
                new InterceptorChain(
                        context -> contextCapturingInterceptor.aroundInvoke(context),
                        context -> repeatAdjustingInterceptor.aroundInvoke(context),
                        context -> service.join((String) context.getParameters()[0], (Integer) context.getParameters()[1])));

        assertThat(invocationContext.proceed()).isEqualTo("native:imageimage");
        assertThat(invocationContext.getContextData())
                .containsEntry("targetType", SampleService.class.getSimpleName())
                .containsEntry("originalRepeatCount", 1)
                .containsEntry("adjustedRepeatCount", 2);
        assertThat(invocationContext.getParameters()).containsExactly("image", 2);
    }

    @Test
    void aroundTimeoutInterceptorsCanUseTimerMetadataAndUpdatedParametersAcrossAChain() throws Exception {
        TimeoutService service = new TimeoutService("native");
        TimeoutMetadata timeoutMetadata = new TimeoutMetadata("retry-window", 1500L);
        TimeoutMetadataCapturingInterceptor timeoutMetadataCapturingInterceptor =
                new TimeoutMetadataCapturingInterceptor();
        TimeoutAttemptAdjustingInterceptor timeoutAttemptAdjustingInterceptor =
                new TimeoutAttemptAdjustingInterceptor();
        SimpleInvocationContext invocationContext = new SimpleInvocationContext(
                service,
                null,
                null,
                new Object[]{"cleanup", 2},
                new LinkedHashMap<>(),
                timeoutMetadata,
                new InterceptorChain(
                        context -> timeoutMetadataCapturingInterceptor.aroundTimeout(context),
                        context -> timeoutAttemptAdjustingInterceptor.aroundTimeout(context),
                        context -> service.handleTimeout(
                                ((TimeoutMetadata) context.getTimer()).getScheduleName(),
                                (String) context.getParameters()[0],
                                (Integer) context.getParameters()[1])));

        assertThat(invocationContext.proceed()).isEqualTo("native:retry-window:cleanup#3");
        assertThat(invocationContext.getContextData())
                .containsEntry("timerName", "retry-window")
                .containsEntry("timerDelayMillis", 1500L)
                .containsEntry("originalAttempt", 2)
                .containsEntry("adjustedAttempt", 3);
        assertThat(invocationContext.getParameters()).containsExactly("cleanup", 3);
    }

    @Test
    void interceptorPriorityConstantsDefineAscendingPriorityBands() {
        assertThat(List.of(
                Interceptor.Priority.PLATFORM_BEFORE,
                Interceptor.Priority.LIBRARY_BEFORE,
                Interceptor.Priority.APPLICATION,
                Interceptor.Priority.LIBRARY_AFTER,
                Interceptor.Priority.PLATFORM_AFTER))
                .containsExactly(0, 1000, 2000, 3000, 4000);
    }

    private static void assertRetentionAndTarget(
            Class<? extends java.lang.annotation.Annotation> annotationType,
            RetentionPolicy retentionPolicy,
            ElementType... expectedTargets) {
        assertThat(AnnotationAccess.getAnnotation(annotationType, Retention.class).value()).isEqualTo(retentionPolicy);
        assertThat(AnnotationAccess.getAnnotation(annotationType, Target.class).value()).containsExactly(expectedTargets);
    }

    private static final class AnnotationAccess {

        private AnnotationAccess() {
        }

        private static <A extends java.lang.annotation.Annotation> A getAnnotation(
                java.lang.reflect.AnnotatedElement annotatedElement,
                Class<A> annotationType) {
            A[] annotations = annotatedElement.getAnnotationsByType(annotationType);
            return annotations.length == 0 ? null : annotations[0];
        }

        private static boolean isAnnotationPresent(
                java.lang.reflect.AnnotatedElement annotatedElement,
                Class<? extends java.lang.annotation.Annotation> annotationType) {
            return annotatedElement.getAnnotationsByType(annotationType).length > 0;
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD, CONSTRUCTOR})
    private @interface Audited {
    }

    @Interceptor
    @Audited
    private static final class AuditingInterceptor {

        @AroundConstruct
        Object aroundConstruct(InvocationContext context) throws Exception {
            return context.proceed();
        }

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            return context.proceed();
        }

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    private static final class MetricsInterceptor {
    }

    private static final class ContextCapturingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            context.getContextData().put("targetType", context.getTarget().getClass().getSimpleName());
            context.getContextData().put("originalRepeatCount", context.getParameters()[1]);
            return context.proceed();
        }
    }

    private static final class RepeatAdjustingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            int adjustedRepeatCount = ((Integer) parameters[1]) + 1;
            context.setParameters(new Object[]{parameters[0], adjustedRepeatCount});
            context.getContextData().put("adjustedRepeatCount", adjustedRepeatCount);
            return context.proceed();
        }
    }

    private static final class TimeoutMetadataCapturingInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            TimeoutMetadata timeoutMetadata = (TimeoutMetadata) context.getTimer();
            context.getContextData().put("timerName", timeoutMetadata.getScheduleName());
            context.getContextData().put("timerDelayMillis", timeoutMetadata.getDelayMillis());
            context.getContextData().put("originalAttempt", context.getParameters()[1]);
            return context.proceed();
        }
    }

    private static final class TimeoutAttemptAdjustingInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            int adjustedAttempt = ((Integer) parameters[1]) + 1;
            context.setParameters(new Object[]{parameters[0], adjustedAttempt});
            context.getContextData().put("adjustedAttempt", adjustedAttempt);
            return context.proceed();
        }
    }

    @ExcludeDefaultInterceptors
    @Interceptors({AuditingInterceptor.class, MetricsInterceptor.class})
    private static final class InterceptedComponent {
        private final String name;

        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        @Interceptors(AuditingInterceptor.class)
        private InterceptedComponent(String name) {
            this.name = name;
        }

        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        @Interceptors(MetricsInterceptor.class)
        private String interceptedOperation() {
            return name.toUpperCase();
        }
    }

    private static final class SampleService {
        private final String prefix;

        private SampleService(String prefix) {
            this.prefix = prefix;
        }

        private String join(String token, int repeatCount) {
            return prefix + ":" + token.repeat(repeatCount);
        }
    }

    private static final class TimeoutService {
        private final String prefix;

        private TimeoutService(String prefix) {
            this.prefix = prefix;
        }

        private String handleTimeout(String scheduleName, String operationName, int attempt) {
            return prefix + ":" + scheduleName + ":" + operationName + "#" + attempt;
        }
    }

    private static final class TimeoutMetadata {
        private final String scheduleName;
        private final long delayMillis;

        private TimeoutMetadata(String scheduleName, long delayMillis) {
            this.scheduleName = scheduleName;
            this.delayMillis = delayMillis;
        }

        private String getScheduleName() {
            return scheduleName;
        }

        private long getDelayMillis() {
            return delayMillis;
        }
    }

    private static final class SimpleInvocationContext implements InvocationContext {
        private final Object target;
        private final Method method;
        private final Constructor<?> constructor;
        private final Map<String, Object> contextData;
        private final Object timer;
        private final ProceedAction proceedAction;
        private Object[] parameters;

        private SimpleInvocationContext(
                Object target,
                Method method,
                Constructor<?> constructor,
                Object[] parameters,
                Map<String, Object> contextData,
                Object timer,
                ProceedAction proceedAction) {
            this.target = target;
            this.method = method;
            this.constructor = constructor;
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
        public Constructor<?> getConstructor() {
            return constructor;
        }

        @Override
        public Object[] getParameters() {
            if (parameters == null) {
                throw new IllegalStateException("Parameters are not available for this interception type");
            }
            return parameters.clone();
        }

        @Override
        public void setParameters(Object[] parameters) {
            if (this.parameters == null) {
                throw new IllegalStateException("Parameters are not available for this interception type");
            }
            if (parameters.length != this.parameters.length) {
                throw new IllegalArgumentException("Expected " + this.parameters.length + " parameters");
            }
            this.parameters = parameters.clone();
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

    private static final class InterceptorChain implements ProceedAction {
        private final ProceedAction[] steps;
        private int index;

        private InterceptorChain(ProceedAction... steps) {
            this.steps = steps;
        }

        @Override
        public Object proceed(SimpleInvocationContext context) throws Exception {
            return steps[index++].proceed(context);
        }
    }

    @FunctionalInterface
    private interface ProceedAction {
        Object proceed(SimpleInvocationContext context) throws Exception;
    }
}
