/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_interceptor.jakarta_interceptor_api;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.AroundTimeout;
import jakarta.interceptor.ExcludeClassInterceptors;
import jakarta.interceptor.ExcludeDefaultInterceptors;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.Interceptors;
import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Jakarta_interceptor_apiTest {

    @Test
    void interceptorAnnotationTypesExposeRuntimeContracts() {
        assertRetentionAndTarget(AroundConstruct.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(AroundInvoke.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(AroundTimeout.class, RetentionPolicy.RUNTIME, METHOD);
        assertRetentionAndTarget(ExcludeClassInterceptors.class, RetentionPolicy.RUNTIME, CONSTRUCTOR, METHOD);
        assertRetentionAndTarget(ExcludeDefaultInterceptors.class, RetentionPolicy.RUNTIME, TYPE, METHOD, CONSTRUCTOR);
        assertRetentionAndTarget(Interceptor.class, RetentionPolicy.RUNTIME, TYPE);
        assertRetentionAndTarget(InterceptorBinding.class, RetentionPolicy.RUNTIME, ANNOTATION_TYPE);
        assertRetentionAndTarget(Interceptors.class, RetentionPolicy.RUNTIME, TYPE, METHOD, CONSTRUCTOR);

        assertThat(Interceptor.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(InterceptorBinding.class.getAnnotationsByType(Documented.class)).hasSize(1);
        assertThat(Interceptor.Priority.PLATFORM_BEFORE).isZero();
        assertThat(Interceptor.Priority.LIBRARY_BEFORE).isEqualTo(1000);
        assertThat(Interceptor.Priority.APPLICATION).isEqualTo(2000);
        assertThat(Interceptor.Priority.LIBRARY_AFTER).isEqualTo(3000);
        assertThat(Interceptor.Priority.PLATFORM_AFTER).isEqualTo(4000);
        assertThat(List.of(
                Interceptor.Priority.PLATFORM_BEFORE,
                Interceptor.Priority.LIBRARY_BEFORE,
                Interceptor.Priority.APPLICATION,
                Interceptor.Priority.LIBRARY_AFTER,
                Interceptor.Priority.PLATFORM_AFTER))
                .containsExactly(0, 1000, 2000, 3000, 4000);
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    void interceptorAnnotationsRetainValuesOnTypesMethodsAndConstructors() throws Exception {
        Constructor<InterceptedComponent> constructor = declaredConstructor(InterceptedComponent.class, String.class);
        Method operation = declaredMethod(InterceptedComponent.class, "operation", String.class, int.class);
        Method constructMethod = declaredMethod(ConstructInterceptor.class, "aroundConstruct", InvocationContext.class);
        Method invokeMethod = declaredMethod(AuditInterceptor.class, "aroundInvoke", InvocationContext.class);
        Method timeoutMethod = declaredMethod(TimeoutInterceptor.class, "aroundTimeout", InvocationContext.class);

        assertThat(InterceptedComponent.class.getAnnotation(Audited.class)).isNotNull();
        assertThat(InterceptedComponent.class.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(InterceptedComponent.class.getAnnotation(Interceptors.class).value())
                .containsExactly(AuditInterceptor.class, TimeoutInterceptor.class);

        assertThat(constructor.getAnnotation(Audited.class)).isNotNull();
        assertThat(constructor.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(constructor.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(constructor.getAnnotation(Interceptors.class).value()).containsExactly(ConstructInterceptor.class);

        assertThat(operation.getAnnotation(Audited.class)).isNotNull();
        assertThat(operation.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(operation.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(operation.getAnnotation(Interceptors.class).value()).containsExactly(TimeoutInterceptor.class);

        assertThat(Audited.class.getAnnotation(InterceptorBinding.class)).isNotNull();
        assertThat(AuditInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(AuditInterceptor.class.getAnnotation(Audited.class)).isNotNull();
        assertThat(TimeoutInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(ConstructInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();

        assertThat(constructMethod.getAnnotation(AroundConstruct.class)).isNotNull();
        assertThat(invokeMethod.getAnnotation(AroundInvoke.class)).isNotNull();
        assertThat(timeoutMethod.getAnnotation(AroundTimeout.class)).isNotNull();
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    void interceptorBindingAnnotationsRetainMemberValuesForComponentsMethodsAndInterceptors() throws Exception {
        Method approve = declaredMethod(BindingAwareService.class, "approve", String.class);
        Method summarize = declaredMethod(BindingAwareService.class, "summarize", String.class);

        Secured serviceSecurity = BindingAwareService.class.getAnnotation(Secured.class);
        Traced serviceTracing = BindingAwareService.class.getAnnotation(Traced.class);
        Secured approvalSecurity = approve.getAnnotation(Secured.class);
        Traced approvalTracing = approve.getAnnotation(Traced.class);
        Secured defaultedSecurity = summarize.getAnnotation(Secured.class);
        Traced defaultedTracing = summarize.getAnnotation(Traced.class);
        Secured interceptorSecurity = BindingAwareInterceptor.class.getAnnotation(Secured.class);
        Traced interceptorTracing = BindingAwareInterceptor.class.getAnnotation(Traced.class);

        assertThat(Secured.class.getAnnotation(InterceptorBinding.class)).isNotNull();
        assertThat(Traced.class.getAnnotation(InterceptorBinding.class)).isNotNull();
        assertThat(BindingAwareInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();

        assertThat(serviceSecurity.role()).isEqualTo("reader");
        assertThat(serviceSecurity.permissions()).isEmpty();
        assertThat(serviceTracing.channel()).isEqualTo("orders");
        assertThat(serviceTracing.sampled()).isFalse();

        assertThat(approvalSecurity.role()).isEqualTo("operator");
        assertThat(approvalSecurity.permissions()).containsExactly("orders:approve");
        assertThat(approvalTracing.channel()).isEqualTo("orders.approval");
        assertThat(approvalTracing.sampled()).isTrue();

        assertThat(defaultedSecurity.role()).isEqualTo("reader");
        assertThat(defaultedSecurity.permissions()).isEmpty();
        assertThat(defaultedTracing.channel()).isEqualTo("orders.summary");
        assertThat(defaultedTracing.sampled()).isTrue();

        assertThat(interceptorSecurity.role()).isEqualTo("administrator");
        assertThat(interceptorSecurity.permissions()).containsExactly("accounts:read", "accounts:write");
        assertThat(interceptorTracing.channel()).isEqualTo("admin");
        assertThat(interceptorTracing.sampled()).isTrue();
    }

    @Test
    void methodInvocationContextSupportsParameterMutationContextDataTimerAndProceed() throws Exception {
        InterceptedComponent target = new InterceptedComponent("native");
        Method operation = declaredMethod(InterceptedComponent.class, "operation", String.class, int.class);
        Object timer = new TimerMetadata("retry", 750L);
        RecordingInvocationContext context = RecordingInvocationContext.forMethod(
                target,
                operation,
                timer,
                new Object[]{" image ", 1},
                parameters -> target.operation((String) parameters[0], (Integer) parameters[1]));

        Object result = new AuditInterceptor().aroundInvoke(context);

        assertThat(result).isEqualTo("native:IMAGEIMAGE");
        assertThat(context.getTarget()).isSameAs(target);
        assertThat(context.getMethod()).isEqualTo(operation);
        assertThat(context.getConstructor()).isNull();
        assertThat(context.getTimer()).isSameAs(timer);
        assertThat(context.getParameters()).containsExactly("IMAGE", 2);
        assertThat(context.getContextData())
                .containsEntry("phase", "invoke")
                .containsEntry("originalToken", " image ")
                .containsEntry("adjustedRepeatCount", 2);
    }

    @Test
    void aroundInvokeInterceptorsShareMutableContextDataAcrossAChain() throws Exception {
        ChainedService target = new ChainedService();
        Method combine = declaredMethod(ChainedService.class, "combine", String.class);
        ChainedInvocationContext context = ChainedInvocationContext.forMethod(
                target,
                combine,
                new Object[]{"  metadata  "},
                parameters -> target.combine((String) parameters[0]),
                new TrimmingInvokeInterceptor()::aroundInvoke,
                new UppercasingInvokeInterceptor()::aroundInvoke);

        Object result = context.proceed();

        assertThat(result).isEqualTo("chain[value=METADATA|combine]");
        assertThat(context.getTarget()).isSameAs(target);
        assertThat(context.getMethod()).isEqualTo(combine);
        assertThat(context.getConstructor()).isNull();
        assertThat(context.getTimer()).isNull();
        assertThat(context.getParameters()).containsExactly("METADATA");
        assertThat(context.getContextData())
                .containsEntry("normalizedToken", "metadata")
                .containsEntry("uppercasedToken", "METADATA")
                .containsEntry("observedMethod", "combine");
        assertThat(stepsOf(context))
                .containsExactly("trim-before", "uppercase-before", "uppercase-after", "trim-after");
    }

    @Test
    void aroundTimeoutInterceptorsCanUseTimerMetadataAndAdjustedParameters() throws Exception {
        TimeoutService target = new TimeoutService("native");
        TimerMetadata timer = new TimerMetadata("cleanup", 1500L);
        Method handleTimeout = declaredMethod(TimeoutService.class, "handleTimeout", String.class, int.class);
        RecordingInvocationContext context = RecordingInvocationContext.forMethod(
                target,
                handleTimeout,
                timer,
                new Object[]{"orphaned-files", 2},
                parameters -> target.handleTimeout((String) parameters[0], (Integer) parameters[1]));

        Object result = new TimeoutInterceptor().aroundTimeout(context);

        assertThat(result).isEqualTo("timeout[native:orphaned-files#3]");
        assertThat(context.getTimer()).isSameAs(timer);
        assertThat(context.getParameters()).containsExactly("orphaned-files", 3);
        assertThat(context.getContextData())
                .containsEntry("phase", "timeout")
                .containsEntry("timerName", "cleanup")
                .containsEntry("timerDelayMillis", 1500L)
                .containsEntry("adjustedAttempt", 3);
    }

    @Test
    void aroundInvokeInterceptorCanRecoverFromProceedException() throws Exception {
        FailingService target = new FailingService();
        Method fetch = declaredMethod(FailingService.class, "fetch", String.class);
        RecordingInvocationContext context = RecordingInvocationContext.forMethod(
                target,
                fetch,
                null,
                new Object[]{"missing-record"},
                parameters -> target.fetch((String) parameters[0]));

        Object result = new RecoveryInterceptor().aroundInvoke(context);

        assertThat(result).isEqualTo("fallback[missing-record]");
        assertThat(context.getTarget()).isSameAs(target);
        assertThat(context.getMethod()).isEqualTo(fetch);
        assertThat(context.getParameters()).containsExactly("missing-record");
        assertThat(context.getContextData())
                .containsEntry("phase", "recovery")
                .containsEntry("failedToken", "missing-record")
                .containsEntry("exceptionType", ServiceFailure.class.getName())
                .containsEntry("exceptionMessage", "Unable to fetch missing-record");
    }

    @Test
    void aroundConstructInterceptorCanMutateConstructorParametersAndExposeConstructedTarget() throws Exception {
        Constructor<TargetAwareComponent> constructor = declaredConstructor(TargetAwareComponent.class, String.class);
        RecordingInvocationContext context = RecordingInvocationContext.forConstructorUpdatingTarget(
                constructor,
                new Object[]{"service"},
                parameters -> new TargetAwareComponent((String) parameters[0]));

        TargetAwareComponent constructed = (TargetAwareComponent) new ConstructInterceptor().aroundConstruct(context);

        assertThat(constructed.describe()).isEqualTo("service-constructed");
        assertThat(context.getTarget()).isSameAs(constructed);
        assertThat(context.getMethod()).isNull();
        assertThat(context.getConstructor()).isEqualTo(constructor);
        assertThat(context.getTimer()).isNull();
        assertThat(context.getParameters()).containsExactly("service-constructed");
        assertThat(context.getContextData())
                .containsEntry("phase", "construct")
                .containsEntry("constructedType", TargetAwareComponent.class.getName());
    }

    @Test
    void invocationContextCanRejectInvalidParameterAccessForLifecycleCallbacks() throws Exception {
        RecordingInvocationContext lifecycleOnlyContext = RecordingInvocationContext.forLifecycle(
                new LifecycleComponent("ready"),
                context -> context.getTarget().toString());

        assertThat(lifecycleOnlyContext.getMethod()).isNull();
        assertThat(lifecycleOnlyContext.getConstructor()).isNull();
        assertThat(lifecycleOnlyContext.getTimer()).isNull();
        assertThat(lifecycleOnlyContext.proceed()).isEqualTo("ready");
        assertThatIllegalStateException().isThrownBy(lifecycleOnlyContext::getParameters)
                .withMessageContaining("Parameters are not available");
        assertThatIllegalStateException().isThrownBy(() -> lifecycleOnlyContext.setParameters(new Object[0]))
                .withMessageContaining("Parameters are not available");

        RecordingInvocationContext methodContext = RecordingInvocationContext.forMethod(
                new ChainedService(),
                declaredMethod(ChainedService.class, "combine", String.class),
                null,
                new Object[]{"native"},
                parameters -> "unused");

        assertThatIllegalArgumentException().isThrownBy(() -> methodContext.setParameters(new Object[]{"a", "b"}))
                .withMessageContaining("Expected 1 parameters");
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static void assertRetentionAndTarget(
            Class<? extends Annotation> annotationType,
            RetentionPolicy expectedRetention,
            ElementType... expectedTargets) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(target).isNotNull();
        assertThat(retention.value()).isEqualTo(expectedRetention);
        assertThat(target.value()).containsExactlyInAnyOrder(expectedTargets);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stepsOf(InvocationContext context) {
        return (List<String>) context.getContextData().computeIfAbsent("steps", key -> new ArrayList<String>());
    }

    private static Method declaredMethod(Class<?> declaringClass, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return declaringClass.getDeclaredMethod(name, parameterTypes);
    }

    private static <T> Constructor<T> declaredConstructor(Class<T> declaringClass, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return declaringClass.getDeclaredConstructor(parameterTypes);
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD, CONSTRUCTOR})
    private @interface Audited {
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD})
    private @interface Secured {
        String role() default "reader";

        String[] permissions() default {};
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD})
    private @interface Traced {
        String channel();

        boolean sampled() default true;
    }

    @Interceptor
    @Secured(role = "administrator", permissions = {"accounts:read", "accounts:write"})
    @Traced(channel = "admin")
    private static final class BindingAwareInterceptor {
    }

    @Secured
    @Traced(channel = "orders", sampled = false)
    private static final class BindingAwareService {
        @Secured(role = "operator", permissions = "orders:approve")
        @Traced(channel = "orders.approval")
        private String approve(String orderId) {
            return orderId;
        }

        @Secured
        @Traced(channel = "orders.summary")
        private String summarize(String orderId) {
            return orderId;
        }
    }

    @Audited
    @Interceptors({AuditInterceptor.class, TimeoutInterceptor.class})
    @ExcludeDefaultInterceptors
    private static final class InterceptedComponent {
        private final String prefix;

        @Audited
        @Interceptors(ConstructInterceptor.class)
        @ExcludeDefaultInterceptors
        @ExcludeClassInterceptors
        private InterceptedComponent(String prefix) {
            this.prefix = prefix;
        }

        @Audited
        @Interceptors(TimeoutInterceptor.class)
        @ExcludeDefaultInterceptors
        @ExcludeClassInterceptors
        private String operation(String token, int repeatCount) {
            return prefix + ":" + token.repeat(repeatCount);
        }
    }

    private static final class TargetAwareComponent {
        private final String value;

        private TargetAwareComponent(String value) {
            this.value = value;
        }

        private String describe() {
            return value;
        }
    }

    private static final class LifecycleComponent {
        private final String state;

        private LifecycleComponent(String state) {
            this.state = state;
        }

        @Override
        public String toString() {
            return state;
        }
    }

    private static final class ChainedService {
        private String combine(String token) {
            return "value=" + token;
        }
    }

    private static final class TimeoutService {
        private final String prefix;

        private TimeoutService(String prefix) {
            this.prefix = prefix;
        }

        private String handleTimeout(String operationName, int attempt) {
            return prefix + ":" + operationName + "#" + attempt;
        }
    }

    private static final class FailingService {
        private String fetch(String token) throws ServiceFailure {
            throw new ServiceFailure("Unable to fetch " + token);
        }
    }

    private static final class ServiceFailure extends Exception {
        private ServiceFailure(String message) {
            super(message);
        }
    }

    private static final class TimerMetadata {
        private final String name;
        private final long delayMillis;

        private TimerMetadata(String name, long delayMillis) {
            this.name = name;
            this.delayMillis = delayMillis;
        }

        private String name() {
            return name;
        }

        private long delayMillis() {
            return delayMillis;
        }
    }

    @Interceptor
    @Audited
    private static final class AuditInterceptor {
        @AroundInvoke
        private Object aroundInvoke(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            String originalToken = (String) parameters[0];
            String normalizedToken = originalToken.trim().toUpperCase();
            Integer adjustedRepeatCount = ((Integer) parameters[1]) + 1;

            context.setParameters(new Object[]{normalizedToken, adjustedRepeatCount});
            context.getContextData().put("phase", "invoke");
            context.getContextData().put("originalToken", originalToken);
            context.getContextData().put("adjustedRepeatCount", adjustedRepeatCount);
            return context.proceed();
        }
    }

    @Interceptor
    private static final class TimeoutInterceptor {
        @AroundTimeout
        private Object aroundTimeout(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            TimerMetadata timerMetadata = (TimerMetadata) context.getTimer();
            int adjustedAttempt = ((Integer) parameters[1]) + 1;

            context.setParameters(new Object[]{parameters[0], adjustedAttempt});
            context.getContextData().put("phase", "timeout");
            context.getContextData().put("timerName", timerMetadata.name());
            context.getContextData().put("timerDelayMillis", timerMetadata.delayMillis());
            context.getContextData().put("adjustedAttempt", adjustedAttempt);
            return "timeout[" + context.proceed() + "]";
        }
    }

    @Interceptor
    private static final class ConstructInterceptor {
        @AroundConstruct
        private Object aroundConstruct(InvocationContext context) throws Exception {
            String constructedValue = context.getParameters()[0] + "-constructed";

            context.setParameters(new Object[]{constructedValue});
            context.getContextData().put("phase", "construct");
            Object constructed = context.proceed();
            context.getContextData().put("constructedType", context.getTarget().getClass().getName());
            return constructed;
        }
    }

    @Interceptor
    private static final class RecoveryInterceptor {
        @AroundInvoke
        private Object aroundInvoke(InvocationContext context) throws Exception {
            try {
                return context.proceed();
            } catch (ServiceFailure failure) {
                String failedToken = (String) context.getParameters()[0];
                context.getContextData().put("phase", "recovery");
                context.getContextData().put("failedToken", failedToken);
                context.getContextData().put("exceptionType", failure.getClass().getName());
                context.getContextData().put("exceptionMessage", failure.getMessage());
                return "fallback[" + failedToken + "]";
            }
        }
    }

    @Interceptor
    private static final class TrimmingInvokeInterceptor {
        @AroundInvoke
        private Object aroundInvoke(InvocationContext context) throws Exception {
            List<String> steps = stepsOf(context);
            steps.add("trim-before");

            String normalizedToken = ((String) context.getParameters()[0]).trim();
            context.setParameters(new Object[]{normalizedToken});
            context.getContextData().put("normalizedToken", normalizedToken);

            Object result = context.proceed();
            steps.add("trim-after");
            return "chain[" + result + "]";
        }
    }

    @Interceptor
    private static final class UppercasingInvokeInterceptor {
        @AroundInvoke
        private Object aroundInvoke(InvocationContext context) throws Exception {
            List<String> steps = stepsOf(context);
            steps.add("uppercase-before");

            String uppercasedToken = ((String) context.getContextData().get("normalizedToken")).toUpperCase();
            context.setParameters(new Object[]{uppercasedToken});
            context.getContextData().put("uppercasedToken", uppercasedToken);
            context.getContextData().put("observedMethod", context.getMethod().getName());

            Object result = context.proceed();
            steps.add("uppercase-after");
            return result + "|" + context.getMethod().getName();
        }
    }

    @FunctionalInterface
    private interface ProceedHandler {
        Object proceed(Object[] parameters) throws Exception;
    }

    @FunctionalInterface
    private interface LifecycleProceedHandler {
        Object proceed(InvocationContext context) throws Exception;
    }

    @FunctionalInterface
    private interface AroundInvokeHandler {
        Object invoke(InvocationContext context) throws Exception;
    }

    private static final class ChainedInvocationContext implements InvocationContext {
        private final Object target;
        private final Method method;
        private final Map<String, Object> contextData;
        private final ProceedHandler terminalHandler;
        private final AroundInvokeHandler[] aroundInvokeHandlers;
        private Object[] parameters;
        private int nextHandlerIndex;

        private ChainedInvocationContext(
                Object target,
                Method method,
                Object[] parameters,
                ProceedHandler terminalHandler,
                AroundInvokeHandler... aroundInvokeHandlers) {
            this.target = target;
            this.method = method;
            this.parameters = parameters.clone();
            this.terminalHandler = terminalHandler;
            this.aroundInvokeHandlers = aroundInvokeHandlers.clone();
            this.contextData = new LinkedHashMap<>();
        }

        private static ChainedInvocationContext forMethod(
                Object target,
                Method method,
                Object[] parameters,
                ProceedHandler terminalHandler,
                AroundInvokeHandler... aroundInvokeHandlers) {
            return new ChainedInvocationContext(target, method, parameters, terminalHandler, aroundInvokeHandlers);
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
            return null;
        }

        @Override
        public Object[] getParameters() {
            return parameters.clone();
        }

        @Override
        public void setParameters(Object[] parameters) {
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
            return null;
        }

        @Override
        public Object proceed() throws Exception {
            if (nextHandlerIndex < aroundInvokeHandlers.length) {
                AroundInvokeHandler currentHandler = aroundInvokeHandlers[nextHandlerIndex++];
                return currentHandler.invoke(this);
            }
            return terminalHandler.proceed(parameters.clone());
        }
    }

    private static final class RecordingInvocationContext implements InvocationContext {
        private Object target;
        private final Object timer;
        private final Method method;
        private final Constructor<?> constructor;
        private final Map<String, Object> contextData;
        private final ProceedHandler proceedHandler;
        private Object[] parameters;

        private RecordingInvocationContext(
                Object target,
                Object timer,
                Method method,
                Constructor<?> constructor,
                Object[] parameters,
                ProceedHandler proceedHandler) {
            this.target = target;
            this.timer = timer;
            this.method = method;
            this.constructor = constructor;
            this.parameters = parameters == null ? null : parameters.clone();
            this.proceedHandler = proceedHandler;
            this.contextData = new LinkedHashMap<>();
        }

        private static RecordingInvocationContext forMethod(
                Object target,
                Method method,
                Object timer,
                Object[] parameters,
                ProceedHandler proceedHandler) {
            return new RecordingInvocationContext(target, timer, method, null, parameters, proceedHandler);
        }

        private static RecordingInvocationContext forConstructorUpdatingTarget(
                Constructor<?> constructor,
                Object[] parameters,
                ProceedHandler proceedHandler) {
            RecordingInvocationContext[] contextHolder = new RecordingInvocationContext[1];
            contextHolder[0] = new RecordingInvocationContext(
                    null,
                    null,
                    null,
                    constructor,
                    parameters,
                    currentParameters -> {
                        Object constructed = proceedHandler.proceed(currentParameters);
                        contextHolder[0].target = constructed;
                        return constructed;
                    });
            return contextHolder[0];
        }

        private static RecordingInvocationContext forLifecycle(
                Object target,
                LifecycleProceedHandler proceedHandler) {
            RecordingInvocationContext[] contextHolder = new RecordingInvocationContext[1];
            contextHolder[0] = new RecordingInvocationContext(
                    target,
                    null,
                    null,
                    null,
                    null,
                    parameters -> proceedHandler.proceed(contextHolder[0]));
            return contextHolder[0];
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
            return proceedHandler.proceed(parameters == null ? null : parameters.clone());
        }
    }
}
