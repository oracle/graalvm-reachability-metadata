/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_interceptor.jakarta_interceptor_api;

import java.lang.annotation.Annotation;
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

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class Jakarta_interceptor_apiTest {

    @Test
    void annotationInterfacesAndPriorityConstantsAreUsableThroughThePublicApi() {
        AroundConstruct aroundConstruct = new AroundConstruct() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AroundConstruct.class;
            }
        };
        AroundInvoke aroundInvoke = new AroundInvoke() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AroundInvoke.class;
            }
        };
        AroundTimeout aroundTimeout = new AroundTimeout() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AroundTimeout.class;
            }
        };
        ExcludeClassInterceptors excludeClassInterceptors = new ExcludeClassInterceptors() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ExcludeClassInterceptors.class;
            }
        };
        ExcludeDefaultInterceptors excludeDefaultInterceptors = new ExcludeDefaultInterceptors() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ExcludeDefaultInterceptors.class;
            }
        };
        Interceptor interceptor = new Interceptor() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Interceptor.class;
            }
        };
        InterceptorBinding interceptorBinding = new InterceptorBinding() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return InterceptorBinding.class;
            }
        };
        Interceptors interceptors = new Interceptors() {
            @Override
            public Class<?>[] value() {
                return new Class<?>[]{AuditTrailInterceptor.class, TimeoutMetricsInterceptor.class};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Interceptors.class;
            }
        };

        assertThat(aroundConstruct.annotationType()).isSameAs(AroundConstruct.class);
        assertThat(aroundInvoke.annotationType()).isSameAs(AroundInvoke.class);
        assertThat(aroundTimeout.annotationType()).isSameAs(AroundTimeout.class);
        assertThat(excludeClassInterceptors.annotationType()).isSameAs(ExcludeClassInterceptors.class);
        assertThat(excludeDefaultInterceptors.annotationType()).isSameAs(ExcludeDefaultInterceptors.class);
        assertThat(interceptor.annotationType()).isSameAs(Interceptor.class);
        assertThat(interceptorBinding.annotationType()).isSameAs(InterceptorBinding.class);
        assertThat(interceptors.annotationType()).isSameAs(Interceptors.class);
        assertThat(interceptors.value()).containsExactly(AuditTrailInterceptor.class, TimeoutMetricsInterceptor.class);

        assertThat(List.of(
                Interceptor.Priority.PLATFORM_BEFORE,
                Interceptor.Priority.LIBRARY_BEFORE,
                Interceptor.Priority.APPLICATION,
                Interceptor.Priority.LIBRARY_AFTER,
                Interceptor.Priority.PLATFORM_AFTER))
                .containsExactly(0, 1000, 2000, 3000, 4000);
    }

    @Test
    void aroundInvokeInterceptorsCanTransformParametersAndShareContextData() throws Exception {
        GreetingService greetingService = new GreetingService("native");
        AuditTrailInterceptor auditTrailInterceptor = new AuditTrailInterceptor();
        PayloadNormalizingInterceptor payloadNormalizingInterceptor = new PayloadNormalizingInterceptor();
        MutableInvocationContext invocationContext = MutableInvocationContext.forMethod(
                greetingService,
                new Object[]{"  metadata  ", 1},
                new InterceptorChain(
                        context -> auditTrailInterceptor.aroundInvoke(context),
                        context -> payloadNormalizingInterceptor.aroundInvoke(context),
                        context -> greetingService.repeat(
                                (String) context.getParameters()[0],
                                (Integer) context.getParameters()[1])));

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("native:METADATAMETADATA");
        assertThat(invocationContext.getMethod()).isNull();
        assertThat(invocationContext.getConstructor()).isNull();
        assertThat(invocationContext.getTarget()).isSameAs(greetingService);
        assertThat(invocationContext.getParameters()).containsExactly("METADATA", 2);
        assertThat(invocationContext.getContextData())
                .containsEntry("interceptionType", "around-invoke")
                .containsEntry("originalPayload", "  metadata  ")
                .containsEntry("normalizedPayload", "METADATA")
                .containsEntry("repeatCount", 2);
    }

    @Test
    void aroundTimeoutInterceptorsCanUseTimerDataWithoutBypassingTheInvocationContext() throws Exception {
        TimeoutService timeoutService = new TimeoutService("native");
        TimeoutMetadata timeoutMetadata = new TimeoutMetadata("cleanup-window", 2);
        TimeoutMetricsInterceptor timeoutMetricsInterceptor = new TimeoutMetricsInterceptor();
        RetryAdjustingInterceptor retryAdjustingInterceptor = new RetryAdjustingInterceptor();
        MutableInvocationContext invocationContext = MutableInvocationContext.forMethod(
                timeoutService,
                new Object[]{"cleanup", 2},
                timeoutMetadata,
                new InterceptorChain(
                        context -> timeoutMetricsInterceptor.aroundTimeout(context),
                        context -> retryAdjustingInterceptor.aroundTimeout(context),
                        context -> timeoutService.execute(
                                timeoutMetadata.name(),
                                (String) context.getParameters()[0],
                                (Integer) context.getParameters()[1])));

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("native:cleanup-window:cleanup#3");
        assertThat(invocationContext.getTimer()).isSameAs(timeoutMetadata);
        assertThat(invocationContext.getParameters()).containsExactly("cleanup", 3);
        assertThat(invocationContext.getContextData())
                .containsEntry("timeoutName", "cleanup-window")
                .containsEntry("initialAttempt", 2)
                .containsEntry("adjustedAttempt", 3)
                .containsEntry("delaySeconds", 2L);
    }

    @Test
    void aroundConstructInterceptorsCanCreateTargetsAndExposeTheConstructedInstanceAfterProceed() throws Exception {
        ConstructionAuditInterceptor constructionAuditInterceptor = new ConstructionAuditInterceptor();
        MutableInvocationContext invocationContext = MutableInvocationContext.forConstructor(
                new Object[]{"forge"},
                new InterceptorChain(
                        constructionAuditInterceptor::aroundConstruct,
                        context -> new ConstructedGreetingService((String) context.getParameters()[0])));

        Object result = invocationContext.proceed();

        assertThat(result).isInstanceOf(ConstructedGreetingService.class);
        assertThat(invocationContext.getConstructor()).isNull();
        assertThat(invocationContext.getMethod()).isNull();
        assertThat(invocationContext.getTarget()).isSameAs(result);
        assertThat(((ConstructedGreetingService) result).message("metadata")).isEqualTo("forge:metadata");
        assertThat(invocationContext.getContextData())
                .containsEntry("interceptionType", "around-construct")
                .containsEntry("constructedType", ConstructedGreetingService.class.getSimpleName());
        assertThat(constructionAuditInterceptor.observedTarget()).isSameAs(result);
    }

    @Test
    void lifecycleStyleContextsCanOmitParametersWhileStillSupportingProceedAndSharedState() throws Exception {
        MutableInvocationContext invocationContext = MutableInvocationContext.forLifecycle(
                new LifecycleAwareComponent(),
                context -> {
                    context.getContextData().put("completed", true);
                    return "done";
                });

        assertThatIllegalStateException().isThrownBy(invocationContext::getParameters)
                .withMessageContaining("Parameters are not available");
        assertThatIllegalStateException().isThrownBy(() -> invocationContext.setParameters(new Object[0]))
                .withMessageContaining("Parameters are not available");
        assertThat(invocationContext.proceed()).isEqualTo("done");
        assertThat(invocationContext.getContextData()).containsEntry("completed", true);
        assertThat(invocationContext.getTimer()).isNull();
        assertThat(invocationContext.getMethod()).isNull();
        assertThat(invocationContext.getConstructor()).isNull();
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD, CONSTRUCTOR})
    private @interface Audited {
    }

    @Audited
    @Interceptor
    private static final class AuditTrailInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            context.getContextData().put("interceptionType", "around-invoke");
            context.getContextData().put("originalPayload", context.getParameters()[0]);
            return context.proceed();
        }
    }

    @Interceptor
    private static final class PayloadNormalizingInterceptor {

        @AroundInvoke
        Object aroundInvoke(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            String normalizedPayload = ((String) parameters[0]).trim().toUpperCase();
            int repeatCount = ((Integer) parameters[1]) + 1;
            context.setParameters(new Object[]{normalizedPayload, repeatCount});
            context.getContextData().put("normalizedPayload", normalizedPayload);
            context.getContextData().put("repeatCount", repeatCount);
            return context.proceed();
        }
    }

    @Interceptor
    private static final class TimeoutMetricsInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            TimeoutMetadata timeoutMetadata = (TimeoutMetadata) context.getTimer();
            context.getContextData().put("timeoutName", timeoutMetadata.name());
            context.getContextData().put("delaySeconds", timeoutMetadata.delaySeconds());
            context.getContextData().put("initialAttempt", context.getParameters()[1]);
            return context.proceed();
        }
    }

    @Interceptor
    private static final class RetryAdjustingInterceptor {

        @AroundTimeout
        Object aroundTimeout(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            int adjustedAttempt = ((Integer) parameters[1]) + 1;
            context.setParameters(new Object[]{parameters[0], adjustedAttempt});
            context.getContextData().put("adjustedAttempt", adjustedAttempt);
            return context.proceed();
        }
    }

    @Interceptor
    private static final class ConstructionAuditInterceptor {
        private Object observedTarget;

        @AroundConstruct
        Object aroundConstruct(InvocationContext context) throws Exception {
            context.getContextData().put("interceptionType", "around-construct");
            Object result = context.proceed();
            observedTarget = context.getTarget();
            context.getContextData().put("constructedType", result.getClass().getSimpleName());
            return result;
        }

        private Object observedTarget() {
            return observedTarget;
        }
    }

    @ExcludeDefaultInterceptors
    @Interceptors({AuditTrailInterceptor.class, PayloadNormalizingInterceptor.class})
    private static final class GreetingService {
        private final String prefix;

        private GreetingService(String prefix) {
            this.prefix = prefix;
        }

        @ExcludeClassInterceptors
        @Interceptors({TimeoutMetricsInterceptor.class, RetryAdjustingInterceptor.class})
        private String repeat(String token, int repeatCount) {
            return prefix + ":" + token.repeat(repeatCount);
        }
    }

    @ExcludeDefaultInterceptors
    @Interceptors(ConstructionAuditInterceptor.class)
    private static final class ConstructedGreetingService {
        private final String prefix;

        private ConstructedGreetingService(String prefix) {
            this.prefix = prefix;
        }

        private String message(String value) {
            return prefix + ":" + value;
        }
    }

    private static final class TimeoutService {
        private final String prefix;

        private TimeoutService(String prefix) {
            this.prefix = prefix;
        }

        private String execute(String timeoutName, String operationName, int attempt) {
            return prefix + ":" + timeoutName + ":" + operationName + "#" + attempt;
        }
    }

    private static final class LifecycleAwareComponent {
    }

    private record TimeoutMetadata(String name, long delaySeconds) {
    }

    private static final class MutableInvocationContext implements InvocationContext {
        private final Object[] initialParameters;
        private final Map<String, Object> contextData;
        private final Object timer;
        private final ProceedAction proceedAction;
        private Object target;
        private Object[] parameters;

        private MutableInvocationContext(
                Object target,
                Object[] parameters,
                Object timer,
                ProceedAction proceedAction) {
            this.target = target;
            this.parameters = parameters == null ? null : parameters.clone();
            this.initialParameters = parameters == null ? null : parameters.clone();
            this.contextData = new LinkedHashMap<>();
            this.timer = timer;
            this.proceedAction = proceedAction;
        }

        private static MutableInvocationContext forMethod(
                Object target,
                Object[] parameters,
                ProceedAction proceedAction) {
            return forMethod(target, parameters, null, proceedAction);
        }

        private static MutableInvocationContext forMethod(
                Object target,
                Object[] parameters,
                Object timer,
                ProceedAction proceedAction) {
            return new MutableInvocationContext(target, parameters, timer, proceedAction);
        }

        private static MutableInvocationContext forConstructor(Object[] parameters, ProceedAction proceedAction) {
            return new MutableInvocationContext(null, parameters, null, context -> {
                Object result = proceedAction.proceed(context);
                context.target = result;
                return result;
            });
        }

        private static MutableInvocationContext forLifecycle(Object target, ProceedAction proceedAction) {
            return new MutableInvocationContext(target, null, null, proceedAction);
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Constructor<?> getConstructor() {
            return null;
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
            if (this.parameters == null || initialParameters == null) {
                throw new IllegalStateException("Parameters are not available for this interception type");
            }
            if (parameters.length != initialParameters.length) {
                throw new IllegalStateException("Expected " + initialParameters.length + " parameters");
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
        public Object proceed(MutableInvocationContext context) throws Exception {
            return steps[index++].proceed(context);
        }
    }

    @FunctionalInterface
    private interface ProceedAction {
        Object proceed(MutableInvocationContext context) throws Exception;
    }
}
