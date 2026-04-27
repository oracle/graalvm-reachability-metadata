/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_api;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

public class Jboss_interceptor_apiTest {

    @Test
    public void aroundInvokeChainSharesInvocationContextStateWithTargetAndOtherInterceptors() throws Exception {
        TargetService target = new TargetService();
        Method method = TargetService.class.getDeclaredMethod("format", String.class, Integer.class);
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("traceId", "trace-42");
        contextData.put("events", new ArrayList<String>());

        ChainedInvocationContext invocationContext = new ChainedInvocationContext(
                target,
                method,
                new Object[] {" native-image ", 2},
                contextData,
                null,
                Arrays.asList(
                        currentContext -> new ParameterNormalizingInterceptor().aroundInvoke(currentContext),
                        currentContext -> new ResultDecoratingInterceptor().aroundInvoke(currentContext)
                ),
                currentContext -> {
                    @SuppressWarnings("unchecked")
                    List<String> events = (List<String>) currentContext.getContextData().get("events");
                    events.add("target:" + currentContext.getMethod().getName());

                    TargetService currentTarget = (TargetService) currentContext.getTarget();
                    Object[] currentParameters = currentContext.getParameters();
                    return currentTarget.format((String) currentParameters[0], (Integer) currentParameters[1]);
                }
        );

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("NATIVE-IMAGE:3|trace-42|format");
        assertThat(invocationContext.getTarget()).isSameAs(target);
        assertThat(invocationContext.getMethod()).isEqualTo(method);
        assertThat(invocationContext.getParameters()).containsExactly("NATIVE-IMAGE", 3);
        assertThat(invocationContext.getContextData()).containsEntry("methodName", "format");
        assertThat(eventsFrom(invocationContext.getContextData()))
                .containsExactly(
                        "normalize:before",
                        "decorate:before",
                        "target:format",
                        "decorate:after",
                        "normalize:after"
                );
    }

    @Test
    public void aroundInvokeInterceptorCanShortCircuitInvocationWithoutProceedingToTarget() throws Exception {
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("events", new ArrayList<String>());
        contextData.put("cacheHit", true);
        contextData.put("cachedValue", "cached:order-7");

        ChainedInvocationContext invocationContext = new ChainedInvocationContext(
                null,
                null,
                new Object[] {"order-7"},
                contextData,
                null,
                Arrays.asList(
                        currentContext -> new CacheLookupInterceptor().aroundInvoke(currentContext),
                        currentContext -> new UnexpectedAroundInvokeInterceptor().aroundInvoke(currentContext)
                ),
                currentContext -> {
                    throw new AssertionError("Target should not be invoked after a short-circuiting interceptor");
                }
        );

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("cached:order-7");
        assertThat(invocationContext.getContextData()).containsEntry("shortCircuited", true);
        assertThat(eventsFrom(invocationContext.getContextData())).containsExactly("cache:lookup", "cache:hit");
    }

    @Test
    public void aroundInvokeInterceptorCanRecoverFromProceedExceptionAndRecordFailure() throws Exception {
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("events", new ArrayList<String>());

        ChainedInvocationContext invocationContext = new ChainedInvocationContext(
                null,
                null,
                new Object[0],
                contextData,
                null,
                Arrays.asList(
                        currentContext -> new ExceptionTranslatingInterceptor().aroundInvoke(currentContext),
                        currentContext -> new CleanupInterceptor().aroundInvoke(currentContext)
                ),
                currentContext -> {
                    eventsFrom(currentContext.getContextData()).add("target:throw");
                    throw new OperationFailureException("database unavailable");
                }
        );

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("fallback:database unavailable");
        assertThat(invocationContext.getContextData())
                .containsEntry("failureType", OperationFailureException.class.getSimpleName())
                .containsEntry("cleanedUp", true);
        assertThat(eventsFrom(invocationContext.getContextData()))
                .containsExactly(
                        "translate:before",
                        "cleanup:before",
                        "target:throw",
                        "cleanup:finally",
                        "translate:recovered"
                );
    }

    @Test
    public void aroundTimeoutChainUsesTimerAndContextDataWithoutAConcreteInterceptorContainer() throws Exception {
        TimerDescriptor timer = new TimerDescriptor("nightly-cleanup", 5);
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("attempt", 1);

        ChainedInvocationContext invocationContext = new ChainedInvocationContext(
                null,
                null,
                new Object[0],
                contextData,
                timer,
                Arrays.asList(
                        currentContext -> new TimeoutAuditInterceptor().aroundTimeout(currentContext),
                        currentContext -> new TimeoutResultInterceptor().aroundTimeout(currentContext)
                ),
                currentContext -> "executed:"
                        + currentContext.getContextData().get("timerName")
                        + ":"
                        + currentContext.getContextData().get("attempt")
        );

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("timer[nightly-cleanup#5] executed:nightly-cleanup:2:wrapped");
        assertThat(invocationContext.getTimer()).isSameAs(timer);
        assertThat(invocationContext.getContextData())
                .containsEntry("attempt", 2)
                .containsEntry("timerName", "nightly-cleanup");
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    public void interceptorAnnotationsRetainBindingMembersAndInterceptorClassListsAtRuntime() throws Exception {
        Method operation = ConfiguredService.class.getDeclaredMethod("configuredOperation");

        assertThat(Audited.class.getAnnotation(InterceptorBinding.class)).isNotNull();
        assertThat(BindingInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(BindingInterceptor.class.getAnnotation(Audited.class).value()).isEqualTo("audit");

        Audited classBinding = ConfiguredService.class.getAnnotation(Audited.class);
        Interceptors classInterceptors = ConfiguredService.class.getAnnotation(Interceptors.class);
        Audited methodBinding = operation.getAnnotation(Audited.class);
        Interceptors methodInterceptors = operation.getAnnotation(Interceptors.class);

        assertThat(ConfiguredService.class.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(classBinding).isNotNull();
        assertThat(classBinding.value()).isEqualTo("class");
        assertThat(classBinding.enabled()).isTrue();
        assertThat(classInterceptors).isNotNull();
        assertThat(classInterceptors.value())
                .containsExactly(ParameterNormalizingInterceptor.class, ResultDecoratingInterceptor.class);

        assertThat(operation.getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(operation.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(methodBinding).isNotNull();
        assertThat(methodBinding.value()).isEqualTo("method");
        assertThat(methodBinding.enabled()).isFalse();
        assertThat(methodInterceptors).isNotNull();
        assertThat(methodInterceptors.value()).containsExactly(TimeoutAuditInterceptor.class);
    }

    @Test
    public void interceptorApiAnnotationsDeclareExpectedRuntimeContracts() {
        assertAnnotationContract(AroundInvoke.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(AroundTimeout.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(ExcludeClassInterceptors.class, RetentionPolicy.RUNTIME, false, ElementType.METHOD);
        assertAnnotationContract(
                ExcludeDefaultInterceptors.class,
                RetentionPolicy.RUNTIME,
                false,
                ElementType.TYPE,
                ElementType.METHOD
        );
        assertAnnotationContract(Interceptor.class, RetentionPolicy.RUNTIME, true, ElementType.TYPE);
        assertAnnotationContract(InterceptorBinding.class, RetentionPolicy.RUNTIME, true, ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(
                Interceptors.class,
                RetentionPolicy.RUNTIME,
                false,
                ElementType.TYPE,
                ElementType.METHOD
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> eventsFrom(Map<String, Object> contextData) {
        return (List<String>) contextData.get("events");
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static void assertAnnotationContract(
            Class<? extends Annotation> annotationType,
            RetentionPolicy retentionPolicy,
            boolean documented,
            ElementType... expectedTargets
    ) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(retentionPolicy);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(expectedTargets);
        assertThat(annotationType.isAnnotationPresent(Documented.class)).isEqualTo(documented);
    }

    @InterceptorBinding
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Audited {

        String value() default "standard";

        boolean enabled() default true;
    }

    @Interceptor
    @Audited("audit")
    private static final class BindingInterceptor {
    }

    @Audited("class")
    @ExcludeDefaultInterceptors
    @Interceptors({ParameterNormalizingInterceptor.class, ResultDecoratingInterceptor.class})
    private static final class ConfiguredService {

        @Audited(value = "method", enabled = false)
        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        @Interceptors({TimeoutAuditInterceptor.class})
        String configuredOperation() {
            return "configured";
        }
    }

    private static final class ParameterNormalizingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            @SuppressWarnings("unchecked")
            List<String> events = (List<String>) context.getContextData().get("events");
            events.add("normalize:before");

            Object[] parameters = context.getParameters();
            String normalizedName = ((String) parameters[0]).trim().toUpperCase(Locale.ROOT);
            Integer incrementedCount = ((Integer) parameters[1]) + 1;
            context.setParameters(new Object[] {normalizedName, incrementedCount});
            context.getContextData().put("methodName", context.getMethod().getName());

            Object result = context.proceed();
            events.add("normalize:after");
            return result;
        }
    }

    private static final class ResultDecoratingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            @SuppressWarnings("unchecked")
            List<String> events = (List<String>) context.getContextData().get("events");
            events.add("decorate:before");

            Object result = context.proceed();
            events.add("decorate:after");
            return result
                    + "|"
                    + context.getContextData().get("traceId")
                    + "|"
                    + context.getContextData().get("methodName");
        }
    }

    private static final class CacheLookupInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            eventsFrom(context.getContextData()).add("cache:lookup");
            if (Boolean.TRUE.equals(context.getContextData().get("cacheHit"))) {
                context.getContextData().put("shortCircuited", true);
                eventsFrom(context.getContextData()).add("cache:hit");
                return context.getContextData().get("cachedValue");
            }
            return context.proceed();
        }
    }

    private static final class UnexpectedAroundInvokeInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) {
            throw new AssertionError("Later interceptors should not be invoked after a short-circuiting interceptor");
        }
    }

    private static final class ExceptionTranslatingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            eventsFrom(context.getContextData()).add("translate:before");
            try {
                return context.proceed();
            } catch (OperationFailureException exception) {
                context.getContextData().put("failureType", exception.getClass().getSimpleName());
                eventsFrom(context.getContextData()).add("translate:recovered");
                return "fallback:" + exception.getMessage();
            }
        }
    }

    private static final class CleanupInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            eventsFrom(context.getContextData()).add("cleanup:before");
            try {
                return context.proceed();
            } finally {
                context.getContextData().put("cleanedUp", true);
                eventsFrom(context.getContextData()).add("cleanup:finally");
            }
        }
    }

    private static final class OperationFailureException extends Exception {

        private OperationFailureException(String message) {
            super(message);
        }
    }

    private static final class TimeoutAuditInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            TimerDescriptor timer = (TimerDescriptor) context.getTimer();
            Integer attempt = (Integer) context.getContextData().get("attempt");
            context.getContextData().put("timerName", timer.getName());
            context.getContextData().put("attempt", attempt + 1);
            return "timer[" + timer.getName() + "#" + timer.getPriority() + "] " + context.proceed();
        }
    }

    private static final class TimeoutResultInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            return context.proceed() + ":wrapped";
        }
    }

    private static final class TargetService {

        String format(String name, Integer count) {
            return name + ":" + count;
        }
    }

    private static final class TimerDescriptor {

        private final String name;
        private final int priority;

        private TimerDescriptor(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        private String getName() {
            return name;
        }

        private int getPriority() {
            return priority;
        }
    }

    private interface InvocationStep {

        Object invoke(ChainedInvocationContext context) throws Exception;
    }

    private static final class ChainedInvocationContext implements InvocationContext {

        private final Object target;
        private final Method method;
        private final Map<String, Object> contextData;
        private final Object timer;
        private final List<InvocationStep> steps;
        private final InvocationStep terminalStep;
        private Object[] parameters;
        private int nextStepIndex;

        private ChainedInvocationContext(
                Object target,
                Method method,
                Object[] parameters,
                Map<String, Object> contextData,
                Object timer,
                List<InvocationStep> steps,
                InvocationStep terminalStep
        ) {
            this.target = target;
            this.method = method;
            this.parameters = parameters;
            this.contextData = contextData;
            this.timer = timer;
            this.steps = steps;
            this.terminalStep = terminalStep;
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
            if (nextStepIndex < steps.size()) {
                InvocationStep step = steps.get(nextStepIndex);
                nextStepIndex++;
                return step.invoke(this);
            }
            return terminalStep.invoke(this);
        }
    }
}
