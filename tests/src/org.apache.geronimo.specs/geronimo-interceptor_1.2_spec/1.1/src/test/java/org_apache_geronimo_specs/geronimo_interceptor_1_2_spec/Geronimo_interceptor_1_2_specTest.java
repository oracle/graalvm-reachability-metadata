/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_interceptor_1_2_spec;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import static org.assertj.core.api.Assertions.assertThat;

public class Geronimo_interceptor_1_2_specTest {
    @Test
    void aroundInvokeInterceptorsCanMutateParametersAndShareContextData() throws Exception {
        BusinessService service = new BusinessService("Hello");
        ChainInvocationContext context = new ChainInvocationContext(
                service,
                new Object[] {" ada ", 2},
                List.of(
                        interceptorContext -> new NormalizingInterceptor().normalize(interceptorContext),
                        interceptorContext -> new AuditInterceptor().auditInvocation(interceptorContext)),
                interceptorContext -> {
                    interceptorContext.getContextData()
                            .put("business-repeat-count", interceptorContext.getParameters()[1]);
                    record(interceptorContext, "business-invoked");
                    return service.greet(
                            (String) interceptorContext.getParameters()[0],
                            (Integer) interceptorContext.getParameters()[1]);
                });

        Object result = context.proceed();

        assertThat(result).isEqualTo("Hello Ada! Hello Ada!");
        assertThat(context.getTarget()).isSameAs(service);
        assertThat(context.getMethod()).isNull();
        assertThat(context.getConstructor()).isNull();
        assertThat(context.getParameters()).containsExactly("Ada", 2);
        assertThat(context.getContextData())
                .containsEntry("normalized-name", "Ada")
                .containsEntry("audit-target", BusinessService.class.getName())
                .containsEntry("business-repeat-count", 2);
        assertThat(events(context)).containsExactly(
                "normalize-before",
                "audit-before",
                "business-invoked",
                "audit-after",
                "normalize-after");
    }

    @Test
    void aroundInvokeInterceptorsCanRecoverFromProceedExceptions() throws Exception {
        FailingService service = new FailingService();
        ChainInvocationContext context = new ChainInvocationContext(
                service,
                new Object[] {"lookup-key"},
                List.of(interceptorContext -> new RecoveryInterceptor().recover(interceptorContext)),
                interceptorContext -> {
                    record(interceptorContext, "business-failed");
                    return service.find((String) interceptorContext.getParameters()[0]);
                });

        Object result = context.proceed();

        assertThat(result).isEqualTo("fallback:lookup-key");
        assertThat(context.getContextData())
                .containsEntry("failed-key", "lookup-key")
                .containsEntry("failure-type", IllegalStateException.class.getName())
                .containsEntry("failure-message", "missing lookup-key");
        assertThat(events(context)).containsExactly(
                "recovery-before",
                "business-failed",
                "recovery-fallback");
    }

    @Test
    void aroundInvokeInterceptorsCanShortCircuitWithoutProceeding() throws Exception {
        CachedReportService service = new CachedReportService();
        ChainInvocationContext context = new ChainInvocationContext(
                service,
                new Object[] {"quarterly-report"},
                List.of(
                        interceptorContext -> new CacheHitInterceptor().returnCached(interceptorContext),
                        interceptorContext -> new GuardInterceptor().shouldNotRun(interceptorContext)),
                interceptorContext -> service.render((String) interceptorContext.getParameters()[0]));

        Object result = context.proceed();

        assertThat(result).isEqualTo("cached:quarterly-report");
        assertThat(context.getContextData())
                .containsEntry("cache-key", "quarterly-report")
                .containsEntry("cache-hit", true);
        assertThat(events(context)).containsExactly("cache-hit");
    }

    @Test
    void aroundTimeoutCanReadTimerAndPreserveProceedResult() throws Exception {
        BusinessTimer timer = new BusinessTimer("daily-cleanup", 3);
        ChainInvocationContext context = new ChainInvocationContext(
                new TimeoutService(),
                new Object[] {"cache"},
                List.of(interceptorContext -> new AuditInterceptor().auditTimeout(interceptorContext)),
                interceptorContext -> "timeout:" + interceptorContext.getTimer() + ":"
                        + interceptorContext.getParameters()[0]);
        context.setTimer(timer);

        Object result = context.proceed();

        assertThat(result).isEqualTo("timeout:daily-cleanup#3:cache");
        assertThat(context.getTimer()).isSameAs(timer);
        assertThat(context.getContextData()).containsEntry("timeout-name", "daily-cleanup");
        assertThat(events(context)).containsExactly("timeout-before", "timeout-after");
    }

    @Test
    void aroundConstructCanChangeConstructorParametersBeforeProceeding() throws Exception {
        ChainInvocationContext context = new ChainInvocationContext(
                null,
                new Object[] {"  worker ", 7},
                List.of(interceptorContext -> new ConstructionInterceptor().construct(interceptorContext)),
                interceptorContext -> {
                    ConstructedComponent component = new ConstructedComponent(
                            (String) interceptorContext.getParameters()[0],
                            (Integer) interceptorContext.getParameters()[1]);
                    ((ChainInvocationContext) interceptorContext).setTarget(component);
                    return null;
                });

        Object result = context.proceed();

        assertThat(result).isNull();
        assertThat(context.getTarget()).isInstanceOf(ConstructedComponent.class);
        ConstructedComponent component = (ConstructedComponent) context.getTarget();
        assertThat(component.name()).isEqualTo("worker");
        assertThat(component.priority()).isEqualTo(Interceptor.Priority.APPLICATION + 7);
        assertThat(context.getParameters()).containsExactly("worker", Interceptor.Priority.APPLICATION + 7);
        assertThat(events(context)).containsExactly("construct-before", "construct-after");
    }

    @Test
    void interceptorPriorityConstantsKeepSpecifiedOrderingAndGaps() {
        assertThat(Interceptor.Priority.PLATFORM_BEFORE).isEqualTo(0);
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
                .isSorted()
                .containsExactly(0, 1000, 2000, 3000, 4000);
    }

    @Test
    void interceptorAnnotationsCanDecorateApplicationTypesAndMethods() throws Exception {
        AnnotatedResource resource = new AnnotatedResource();
        ChainInvocationContext context = new ChainInvocationContext(
                resource,
                new Object[] {"payload"},
                List.of(interceptorContext -> new MethodLevelInterceptor().invoke(interceptorContext)),
                interceptorContext -> resource.process((String) interceptorContext.getParameters()[0]));

        Object result = context.proceed();

        assertThat(result).isEqualTo("processed:payload");
        assertThat(resource.defaultInterceptorsExcluded()).isTrue();
        assertThat(resource.classInterceptorsExcluded()).isTrue();
        assertThat(events(context)).containsExactly("method-level-before", "method-level-after");
    }

    @Test
    void manualAnnotationImplementationsExposeTheirPublicMembers() {
        Interceptors interceptors = new Interceptors() {
            @Override
            public Class<?>[] value() {
                return new Class<?>[] {AuditInterceptor.class, NormalizingInterceptor.class};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Interceptors.class;
            }
        };
        RankedBinding rankedBinding = new RankedBinding() {
            @Override
            public int value() {
                return Interceptor.Priority.LIBRARY_BEFORE;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RankedBinding.class;
            }
        };

        assertThat(interceptors.annotationType()).isSameAs(Interceptors.class);
        assertThat(interceptors.value()).containsExactly(AuditInterceptor.class, NormalizingInterceptor.class);
        assertThat(rankedBinding.annotationType()).isSameAs(RankedBinding.class);
        assertThat(rankedBinding.value()).isEqualTo(Interceptor.Priority.LIBRARY_BEFORE);
    }

    @Test
    void markerAnnotationsCanBeImplementedWithoutContainerServices() {
        List<Annotation> annotations = List.of(
                new AroundConstruct() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return AroundConstruct.class;
                    }
                },
                new AroundInvoke() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return AroundInvoke.class;
                    }
                },
                new AroundTimeout() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return AroundTimeout.class;
                    }
                },
                new ExcludeClassInterceptors() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return ExcludeClassInterceptors.class;
                    }
                },
                new ExcludeDefaultInterceptors() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return ExcludeDefaultInterceptors.class;
                    }
                },
                new Interceptor() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return Interceptor.class;
                    }
                },
                new InterceptorBinding() {
                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return InterceptorBinding.class;
                    }
                });

        assertThat(annotations)
                .extracting(Annotation::annotationType)
                .containsExactly(
                        AroundConstruct.class,
                        AroundInvoke.class,
                        AroundTimeout.class,
                        ExcludeClassInterceptors.class,
                        ExcludeDefaultInterceptors.class,
                        Interceptor.class,
                        InterceptorBinding.class);
    }

    @SuppressWarnings("unchecked")
    private static List<String> events(InvocationContext context) {
        return (List<String>) context.getContextData().get("events");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    @interface Audited {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    @interface RankedBinding {
        int value() default Interceptor.Priority.APPLICATION;
    }

    @Audited
    @RankedBinding(Interceptor.Priority.LIBRARY_BEFORE)
    @Interceptor
    static final class AuditInterceptor {
        @AroundInvoke
        Object auditInvocation(InvocationContext context) throws Exception {
            record(context, "audit-before");
            context.getContextData().put("audit-target", context.getTarget().getClass().getName());
            Object result = context.proceed();
            record(context, "audit-after");
            return result;
        }

        @AroundTimeout
        Object auditTimeout(InvocationContext context) throws Exception {
            record(context, "timeout-before");
            context.getContextData().put("timeout-name", ((BusinessTimer) context.getTimer()).name());
            Object result = context.proceed();
            record(context, "timeout-after");
            return result;
        }
    }

    @Audited
    @Interceptor
    static final class NormalizingInterceptor {
        @AroundInvoke
        Object normalize(InvocationContext context) throws Exception {
            record(context, "normalize-before");
            Object[] parameters = context.getParameters();
            parameters[0] = capitalize(((String) parameters[0]).trim());
            context.setParameters(parameters);
            context.getContextData().put("normalized-name", parameters[0]);
            Object result = context.proceed();
            record(context, "normalize-after");
            return result;
        }

        private static String capitalize(String value) {
            return value.substring(0, 1).toUpperCase() + value.substring(1);
        }
    }

    @Interceptor
    static final class ConstructionInterceptor {
        @AroundConstruct
        Object construct(InvocationContext context) throws Exception {
            record(context, "construct-before");
            assertThat(context.getTarget()).isNull();
            Object[] parameters = context.getParameters();
            parameters[0] = ((String) parameters[0]).trim();
            parameters[1] = Interceptor.Priority.APPLICATION + (Integer) parameters[1];
            context.setParameters(parameters);
            Object result = context.proceed();
            assertThat(context.getTarget()).isInstanceOf(ConstructedComponent.class);
            record(context, "construct-after");
            return result;
        }
    }

    @Interceptor
    static final class RecoveryInterceptor {
        @AroundInvoke
        Object recover(InvocationContext context) throws Exception {
            record(context, "recovery-before");
            try {
                return context.proceed();
            } catch (IllegalStateException failure) {
                context.getContextData().put("failed-key", context.getParameters()[0]);
                context.getContextData().put("failure-type", failure.getClass().getName());
                context.getContextData().put("failure-message", failure.getMessage());
                record(context, "recovery-fallback");
                return "fallback:" + context.getParameters()[0];
            }
        }
    }

    @Interceptor
    static final class CacheHitInterceptor {
        @AroundInvoke
        Object returnCached(InvocationContext context) {
            String key = (String) context.getParameters()[0];
            record(context, "cache-hit");
            context.getContextData().put("cache-key", key);
            context.getContextData().put("cache-hit", true);
            return "cached:" + key;
        }
    }

    @Interceptor
    static final class GuardInterceptor {
        @AroundInvoke
        Object shouldNotRun(InvocationContext context) {
            record(context, "guard-ran");
            throw new AssertionError("short-circuited interceptor chain should not continue");
        }
    }

    @Interceptor
    static final class MethodLevelInterceptor {
        @AroundInvoke
        Object invoke(InvocationContext context) throws Exception {
            record(context, "method-level-before");
            Object result = context.proceed();
            record(context, "method-level-after");
            return result;
        }
    }

    static final class FailingService {
        String find(String key) {
            throw new IllegalStateException("missing " + key);
        }
    }

    static final class CachedReportService {
        String render(String name) {
            throw new AssertionError("cached reports should not be rendered again: " + name);
        }
    }

    @Interceptors({AuditInterceptor.class, NormalizingInterceptor.class})
    static final class BusinessService {
        private final String greeting;

        BusinessService(String greeting) {
            this.greeting = greeting;
        }

        String greet(String name, int times) {
            String phrase = greeting + " " + name + "!";
            return String.join(" ", Collections.nCopies(times, phrase));
        }
    }

    @ExcludeDefaultInterceptors
    @Interceptors(MethodLevelInterceptor.class)
    static final class AnnotatedResource {
        @ExcludeClassInterceptors
        String process(String value) {
            return "processed:" + value;
        }

        boolean defaultInterceptorsExcluded() {
            return true;
        }

        boolean classInterceptorsExcluded() {
            return true;
        }
    }

    static final class TimeoutService {
    }

    static final class BusinessTimer {
        private final String name;
        private final int attempt;

        BusinessTimer(String name, int attempt) {
            this.name = name;
            this.attempt = attempt;
        }

        String name() {
            return name;
        }

        @Override
        public String toString() {
            return name + "#" + attempt;
        }
    }

    static final class ConstructedComponent {
        private final String name;
        private final int priority;

        ConstructedComponent(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        String name() {
            return name;
        }

        int priority() {
            return priority;
        }
    }

    static final class ChainInvocationContext implements InvocationContext {
        private final Map<String, Object> contextData = new LinkedHashMap<>();
        private final List<InterceptorStep> steps;
        private final TerminalInvocation terminalInvocation;
        private Object target;
        private Object[] parameters;
        private Object timer;
        private int index;

        ChainInvocationContext(
                Object target,
                Object[] parameters,
                List<InterceptorStep> steps,
                TerminalInvocation terminalInvocation) {
            this.target = target;
            this.parameters = Arrays.copyOf(parameters, parameters.length);
            this.steps = List.copyOf(steps);
            this.terminalInvocation = terminalInvocation;
            this.contextData.put("events", new ArrayList<String>());
        }

        @Override
        public Object getTarget() {
            return target;
        }

        void setTarget(Object target) {
            this.target = target;
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
            return Arrays.copyOf(parameters, parameters.length);
        }

        @Override
        public void setParameters(Object[] parameters) {
            this.parameters = Arrays.copyOf(parameters, parameters.length);
        }

        @Override
        public Map<String, Object> getContextData() {
            return contextData;
        }

        @Override
        public Object proceed() throws Exception {
            if (index < steps.size()) {
                InterceptorStep step = steps.get(index);
                index++;
                return step.apply(this);
            }
            return terminalInvocation.invoke(this);
        }

        @Override
        public Object getTimer() {
            return timer;
        }

        void setTimer(Object timer) {
            this.timer = timer;
        }
    }

    @FunctionalInterface
    interface InterceptorStep {
        Object apply(InvocationContext context) throws Exception;
    }

    @FunctionalInterface
    interface TerminalInvocation {
        Object invoke(InvocationContext context) throws Exception;
    }

    private static void record(InvocationContext context, String event) {
        events(context).add(event);
    }
}
