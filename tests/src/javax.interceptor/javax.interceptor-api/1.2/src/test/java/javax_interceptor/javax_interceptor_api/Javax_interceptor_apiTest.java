/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_interceptor.javax_interceptor_api;

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

class Javax_interceptor_apiTest {
    @Test
    void interceptorApiAnnotationTypesExposeSupportedMetaAnnotations() {
        assertThat(retentionOf(AroundConstruct.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(AroundConstruct.class)).containsExactly(METHOD);

        assertThat(retentionOf(AroundInvoke.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(AroundInvoke.class)).containsExactly(METHOD);

        assertThat(retentionOf(AroundTimeout.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(AroundTimeout.class)).containsExactly(METHOD);

        assertThat(retentionOf(ExcludeClassInterceptors.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(ExcludeClassInterceptors.class)).containsExactlyInAnyOrder(CONSTRUCTOR, METHOD);

        assertThat(retentionOf(ExcludeDefaultInterceptors.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(ExcludeDefaultInterceptors.class)).containsExactlyInAnyOrder(TYPE, METHOD, CONSTRUCTOR);

        assertThat(retentionOf(InterceptorBinding.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(InterceptorBinding.class)).containsExactly(ANNOTATION_TYPE);
        assertThat(InterceptorBinding.class.getAnnotationsByType(Documented.class)).hasSize(1);

        assertThat(retentionOf(Interceptor.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(Interceptor.class)).containsExactly(TYPE);
        assertThat(Interceptor.class.getAnnotationsByType(Documented.class)).hasSize(1);

        assertThat(retentionOf(Interceptors.class)).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(targetOf(Interceptors.class)).containsExactlyInAnyOrder(TYPE, METHOD, CONSTRUCTOR);

        assertThat(Interceptor.Priority.PLATFORM_BEFORE).isZero();
        assertThat(Interceptor.Priority.LIBRARY_BEFORE).isEqualTo(1000);
        assertThat(Interceptor.Priority.APPLICATION).isEqualTo(2000);
        assertThat(Interceptor.Priority.LIBRARY_AFTER).isEqualTo(3000);
        assertThat(Interceptor.Priority.PLATFORM_AFTER).isEqualTo(4000);
        assertThat(Interceptor.Priority.PLATFORM_BEFORE)
                .isLessThan(Interceptor.Priority.LIBRARY_BEFORE)
                .isLessThan(Interceptor.Priority.APPLICATION)
                .isLessThan(Interceptor.Priority.LIBRARY_AFTER)
                .isLessThan(Interceptor.Priority.PLATFORM_AFTER);
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    void runtimeVisibleAnnotationsDescribeInterceptorBindingsAcrossTypesMethodsAndConstructors() throws Exception {
        Interceptors typeInterceptors = InterceptedService.class.getAnnotation(Interceptors.class);
        Interceptors constructorInterceptors = declaredConstructor(InterceptedService.class, String.class)
                .getAnnotation(Interceptors.class);
        Interceptors methodInterceptors = declaredMethod(InterceptedService.class, "process", String.class, int.class)
                .getAnnotation(Interceptors.class);

        assertThat(InterceptedService.class.getAnnotation(AuditedBinding.class)).isNotNull();
        assertThat(InterceptedService.class.getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(typeInterceptors.value()).containsExactly(AuditInterceptor.class, TimeoutInterceptor.class);

        assertThat(declaredConstructor(InterceptedService.class, String.class)
                .getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(declaredConstructor(InterceptedService.class, String.class)
                .getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(constructorInterceptors.value()).containsExactly(ConstructInterceptor.class);

        assertThat(declaredMethod(InterceptedService.class, "process", String.class, int.class)
                .getAnnotation(AuditedBinding.class)).isNotNull();
        assertThat(declaredMethod(InterceptedService.class, "process", String.class, int.class)
                .getAnnotation(ExcludeDefaultInterceptors.class)).isNotNull();
        assertThat(declaredMethod(InterceptedService.class, "process", String.class, int.class)
                .getAnnotation(ExcludeClassInterceptors.class)).isNotNull();
        assertThat(methodInterceptors.value()).containsExactly(TimeoutInterceptor.class);

        assertThat(AuditedBinding.class.getAnnotation(InterceptorBinding.class)).isNotNull();
        assertThat(AuditInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(TimeoutInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();
        assertThat(ConstructInterceptor.class.getAnnotation(Interceptor.class)).isNotNull();

        assertThat(declaredMethod(AuditInterceptor.class, "aroundInvoke", InvocationContext.class)
                .getAnnotation(AroundInvoke.class)).isNotNull();
        assertThat(declaredMethod(TimeoutInterceptor.class, "aroundTimeout", InvocationContext.class)
                .getAnnotation(AroundTimeout.class)).isNotNull();
        assertThat(declaredMethod(ConstructInterceptor.class, "aroundConstruct", InvocationContext.class)
                .getAnnotation(AroundConstruct.class)).isNotNull();
    }

    @Test
    void aroundInvokeAndAroundTimeoutInterceptorsCanCoordinateMethodContexts() throws Exception {
        InterceptedService target = new InterceptedService("audit");
        Method processMethod = declaredMethod(InterceptedService.class, "process", String.class, int.class);

        RecordingInvocationContext invokeContext = RecordingInvocationContext.forMethod(
                target,
                processMethod,
                null,
                new Object[]{" native-image ", 1},
                parameters -> target.process((String) parameters[0], (Integer) parameters[1]));

        AuditInterceptor auditInterceptor = new AuditInterceptor();
        Object invokeResult = auditInterceptor.aroundInvoke(invokeContext);

        assertThat(invokeContext.getTarget()).isSameAs(target);
        assertThat(invokeContext.getMethod()).isEqualTo(processMethod);
        assertThat(invokeContext.getConstructor()).isNull();
        assertThat(invokeContext.getTimer()).isNull();
        assertThat(invokeContext.getParameters()).containsExactly("native-image", 2);
        assertThat(invokeContext.getContextData())
                .containsEntry("phase", "invoke")
                .containsEntry("methodName", "process");
        assertThat(invokeResult).isEqualTo("audit:native-imagenative-image");

        Object timer = "500ms";
        RecordingInvocationContext timeoutContext = RecordingInvocationContext.forMethod(
                target,
                processMethod,
                timer,
                new Object[]{"scheduled", 1},
                parameters -> target.process((String) parameters[0], (Integer) parameters[1]));

        TimeoutInterceptor timeoutInterceptor = new TimeoutInterceptor();
        Object timeoutResult = timeoutInterceptor.aroundTimeout(timeoutContext);

        assertThat(timeoutContext.getTimer()).isEqualTo(timer);
        assertThat(timeoutContext.getContextData())
                .containsEntry("phase", "timeout")
                .containsEntry("timer", timer);
        assertThat(timeoutResult).isEqualTo("timeout->audit:scheduled");
    }

    @Test
    void aroundInvokeInterceptorsCanShareMutableContextDataAcrossChain() throws Exception {
        ChainedService target = new ChainedService();
        Method combineMethod = declaredMethod(ChainedService.class, "combine", String.class);
        ChainedInvocationContext invocationContext = ChainedInvocationContext.forMethod(
                target,
                combineMethod,
                new Object[]{"  native-image  "},
                parameters -> target.combine((String) parameters[0]),
                new TrimmingInvokeInterceptor()::aroundInvoke,
                new UppercasingInvokeInterceptor()::aroundInvoke);

        Object result = invocationContext.proceed();

        assertThat(result).isEqualTo("chain[value=NATIVE-IMAGE|combine]");
        assertThat(invocationContext.getTarget()).isSameAs(target);
        assertThat(invocationContext.getMethod()).isEqualTo(combineMethod);
        assertThat(invocationContext.getConstructor()).isNull();
        assertThat(invocationContext.getTimer()).isNull();
        assertThat(invocationContext.getParameters()).containsExactly("NATIVE-IMAGE");
        assertThat(invocationContext.getContextData())
                .containsEntry("normalizedToken", "native-image")
                .containsEntry("uppercasedToken", "NATIVE-IMAGE")
                .containsEntry("observedMethod", "combine");
        assertThat(stepsOf(invocationContext))
                .containsExactly("trim-before", "uppercase-before", "uppercase-after", "trim-after");
    }

    @Test
    void aroundConstructInterceptorCanBuildObjectsViaInvocationContext() throws Exception {
        Constructor<InterceptedService> constructor = declaredConstructor(InterceptedService.class, String.class);
        RecordingInvocationContext constructContext = RecordingInvocationContext.forConstructor(
                constructor,
                new Object[]{"seed"},
                parameters -> new InterceptedService((String) parameters[0]));

        ConstructInterceptor constructInterceptor = new ConstructInterceptor();
        InterceptedService constructed = (InterceptedService) constructInterceptor.aroundConstruct(constructContext);

        assertThat(constructContext.getTarget()).isNull();
        assertThat(constructContext.getMethod()).isNull();
        assertThat(constructContext.getConstructor()).isEqualTo(constructor);
        assertThat(constructContext.getTimer()).isNull();
        assertThat(constructContext.getParameters()).containsExactly("seed-constructed");
        assertThat(constructContext.getContextData()).containsEntry("phase", "construct");
        assertThat(constructed.process("metadata", 2)).isEqualTo("seed-constructed:metadatametadata");
    }

    @Test
    void aroundConstructInterceptorsCanObserveConstructedTargetAfterProceed() throws Exception {
        Constructor<TargetAwareService> constructor = declaredConstructor(TargetAwareService.class, String.class);
        RecordingInvocationContext constructContext = RecordingInvocationContext.forConstructorUpdatingTarget(
                constructor,
                new Object[]{"visible-target"},
                parameters -> new TargetAwareService((String) parameters[0]));

        TargetCapturingConstructInterceptor constructInterceptor = new TargetCapturingConstructInterceptor();
        TargetAwareService constructed = (TargetAwareService) constructInterceptor.aroundConstruct(constructContext);

        assertThat(constructContext.getConstructor()).isEqualTo(constructor);
        assertThat(constructContext.getTarget()).isSameAs(constructed);
        assertThat(constructInterceptor.observedTarget()).isSameAs(constructed);
        assertThat(constructContext.getContextData())
                .containsEntry("phase", "construct-target")
                .containsEntry("observedTargetType", TargetAwareService.class.getName());
        assertThat(constructed.describe()).isEqualTo("visible-target");
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static RetentionPolicy retentionOf(Class<? extends Annotation> annotationType) {
        Retention retention = annotationType.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        return retention.value();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stepsOf(InvocationContext context) {
        return (List<String>) context.getContextData().computeIfAbsent("steps", key -> new ArrayList<String>());
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static ElementType[] targetOf(Class<? extends Annotation> annotationType) {
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        return target.value();
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
    @Target({TYPE, METHOD})
    private @interface AuditedBinding {
    }

    @AuditedBinding
    @Interceptors({AuditInterceptor.class, TimeoutInterceptor.class})
    @ExcludeDefaultInterceptors
    private static final class InterceptedService {
        private final String prefix;

        @Interceptors({ConstructInterceptor.class})
        @ExcludeDefaultInterceptors
        @ExcludeClassInterceptors
        private InterceptedService(String prefix) {
            this.prefix = prefix;
        }

        @AuditedBinding
        @Interceptors(TimeoutInterceptor.class)
        @ExcludeDefaultInterceptors
        @ExcludeClassInterceptors
        private String process(String token, int repetitions) {
            return prefix + ":" + token.repeat(repetitions);
        }
    }

    private static final class TargetAwareService {
        private final String value;

        private TargetAwareService(String value) {
            this.value = value;
        }

        private String describe() {
            return value;
        }
    }

    private static final class ChainedService {
        private String combine(String token) {
            return "value=" + token;
        }
    }

    @Interceptor
    @AuditedBinding
    private static final class AuditInterceptor {
        @AroundInvoke
        private Object aroundInvoke(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            String trimmedToken = ((String) parameters[0]).trim();
            Integer incrementedRepetitions = ((Integer) parameters[1]) + 1;

            context.setParameters(new Object[]{trimmedToken, incrementedRepetitions});
            context.getContextData().put("phase", "invoke");
            context.getContextData().put("methodName", context.getMethod().getName());
            return context.proceed();
        }
    }

    @Interceptor
    private static final class TimeoutInterceptor {
        @AroundTimeout
        private Object aroundTimeout(InvocationContext context) throws Exception {
            context.getContextData().put("phase", "timeout");
            context.getContextData().put("timer", context.getTimer());
            return "timeout->" + context.proceed();
        }
    }

    @Interceptor
    private static final class ConstructInterceptor {
        @AroundConstruct
        private Object aroundConstruct(InvocationContext context) throws Exception {
            Object[] parameters = context.getParameters();
            String prefix = ((String) parameters[0]) + "-constructed";

            context.setParameters(new Object[]{prefix});
            context.getContextData().put("phase", "construct");
            return context.proceed();
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

    @Interceptor
    private static final class TargetCapturingConstructInterceptor {
        private Object observedTarget;

        @AroundConstruct
        private Object aroundConstruct(InvocationContext context) throws Exception {
            context.getContextData().put("phase", "construct-target");
            Object constructed = context.proceed();
            observedTarget = context.getTarget();
            context.getContextData().put("observedTargetType", observedTarget.getClass().getName());
            return constructed;
        }

        private Object observedTarget() {
            return observedTarget;
        }
    }

    @FunctionalInterface
    private interface ProceedHandler {
        Object proceed(Object[] parameters) throws Exception;
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
            this.parameters = parameters;
            this.terminalHandler = terminalHandler;
            this.aroundInvokeHandlers = aroundInvokeHandlers;
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
        public Object getTimer() {
            return null;
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
            if (nextHandlerIndex < aroundInvokeHandlers.length) {
                AroundInvokeHandler currentHandler = aroundInvokeHandlers[nextHandlerIndex++];
                return currentHandler.invoke(this);
            }
            return terminalHandler.proceed(parameters);
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
            this.parameters = parameters;
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

        private static RecordingInvocationContext forConstructor(
                Constructor<?> constructor,
                Object[] parameters,
                ProceedHandler proceedHandler) {
            return new RecordingInvocationContext(null, null, null, constructor, parameters, proceedHandler);
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
            return proceedHandler.proceed(parameters);
        }
    }
}
