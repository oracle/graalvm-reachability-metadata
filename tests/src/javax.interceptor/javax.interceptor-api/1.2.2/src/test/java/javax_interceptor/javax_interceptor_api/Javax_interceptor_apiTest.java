/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_interceptor.javax_interceptor_api;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
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

class Javax_interceptor_apiTest {
    @Test
    void interceptorAnnotationsExposeExpectedRuntimeContracts() {
        assertAnnotationContract(AroundConstruct.class, ElementType.METHOD);
        assertAnnotationContract(AroundInvoke.class, ElementType.METHOD);
        assertAnnotationContract(AroundTimeout.class, ElementType.METHOD);
        assertAnnotationContract(ExcludeClassInterceptors.class, ElementType.METHOD, ElementType.CONSTRUCTOR);
        assertAnnotationContract(
                ExcludeDefaultInterceptors.class,
                ElementType.TYPE,
                ElementType.METHOD,
                ElementType.CONSTRUCTOR);
        assertAnnotationContract(Interceptor.class, ElementType.TYPE);
        assertAnnotationContract(InterceptorBinding.class, ElementType.ANNOTATION_TYPE);
        assertAnnotationContract(Interceptors.class, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
    }

    @Test
    void interceptorAnnotationsAreVisibleOnAnnotatedTypesMethodsAndConstructors() throws NoSuchMethodException {
        InterceptorBinding bindingMarker = getAnnotation(Logged.class, InterceptorBinding.class);
        Interceptor interceptorMarker = getAnnotation(AuditInterceptor.class, Interceptor.class);
        Interceptors classInterceptors = getAnnotation(PaymentService.class, Interceptors.class);
        ExcludeDefaultInterceptors classDefaultExclusion =
                getAnnotation(PaymentService.class, ExcludeDefaultInterceptors.class);

        Constructor<PaymentService> constructor = PaymentService.class.getDeclaredConstructor(String.class);
        Interceptors constructorInterceptors = getAnnotation(constructor, Interceptors.class);
        ExcludeDefaultInterceptors constructorDefaultExclusion =
                getAnnotation(constructor, ExcludeDefaultInterceptors.class);

        Method pay = PaymentService.class.getDeclaredMethod("pay", String.class);
        Interceptors methodInterceptors = getAnnotation(pay, Interceptors.class);
        ExcludeClassInterceptors classExclusion = getAnnotation(pay, ExcludeClassInterceptors.class);
        ExcludeDefaultInterceptors methodDefaultExclusion = getAnnotation(pay, ExcludeDefaultInterceptors.class);

        Method aroundConstruct = AuditInterceptor.class.getDeclaredMethod("aroundConstruct", InvocationContext.class);
        Method aroundInvoke = AuditInterceptor.class.getDeclaredMethod("aroundInvoke", InvocationContext.class);
        Method aroundTimeout = AuditInterceptor.class.getDeclaredMethod("aroundTimeout", InvocationContext.class);

        assertThat(bindingMarker).isNotNull();
        assertThat(bindingMarker.annotationType()).isSameAs(InterceptorBinding.class);
        assertThat(interceptorMarker).isNotNull();
        assertThat(interceptorMarker.annotationType()).isSameAs(Interceptor.class);

        assertThat(classInterceptors).isNotNull();
        assertThat(classInterceptors.annotationType()).isSameAs(Interceptors.class);
        assertThat(classInterceptors.value()).containsExactly(AuditInterceptor.class, MetricsInterceptor.class);
        assertThat(classDefaultExclusion).isNotNull();
        assertThat(classDefaultExclusion.annotationType()).isSameAs(ExcludeDefaultInterceptors.class);

        assertThat(constructorInterceptors).isNotNull();
        assertThat(constructorInterceptors.value()).containsExactly(AuditInterceptor.class);
        assertThat(constructorDefaultExclusion).isNotNull();
        assertThat(constructorDefaultExclusion.annotationType()).isSameAs(ExcludeDefaultInterceptors.class);

        assertThat(methodInterceptors).isNotNull();
        assertThat(methodInterceptors.value()).containsExactly(AuditInterceptor.class, MetricsInterceptor.class);
        assertThat(classExclusion).isNotNull();
        assertThat(classExclusion.annotationType()).isSameAs(ExcludeClassInterceptors.class);
        assertThat(methodDefaultExclusion).isNotNull();
        assertThat(methodDefaultExclusion.annotationType()).isSameAs(ExcludeDefaultInterceptors.class);

        assertThat(getAnnotation(aroundConstruct, AroundConstruct.class)).isNotNull();
        assertThat(getAnnotation(aroundConstruct, AroundConstruct.class).annotationType()).isSameAs(AroundConstruct.class);
        assertThat(getAnnotation(aroundInvoke, AroundInvoke.class)).isNotNull();
        assertThat(getAnnotation(aroundInvoke, AroundInvoke.class).annotationType()).isSameAs(AroundInvoke.class);
        assertThat(getAnnotation(aroundTimeout, AroundTimeout.class)).isNotNull();
        assertThat(getAnnotation(aroundTimeout, AroundTimeout.class).annotationType()).isSameAs(AroundTimeout.class);
    }

    @Test
    void invocationContextSupportsMethodInvocationState() throws Exception {
        PaymentService target = new PaymentService("paid:");
        Method method = PaymentService.class.getDeclaredMethod("pay", String.class);
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("phase", "before-invoke");

        RecordingInvocationContext[] holder = new RecordingInvocationContext[1];
        holder[0] = RecordingInvocationContext.forMethod(
                target,
                method,
                new Object[] {"invoice-17"},
                "timeout-42",
                contextData,
                () -> target.pay((String) holder[0].getParameters()[0]));
        RecordingInvocationContext invocationContext = holder[0];

        assertThat(invocationContext.getTarget()).isSameAs(target);
        assertThat(invocationContext.getTimer()).isEqualTo("timeout-42");
        assertThat(invocationContext.getMethod()).isSameAs(method);
        assertThat(invocationContext.getConstructor()).isNull();
        assertThat(invocationContext.getParameters()).containsExactly("invoice-17");
        assertThat(invocationContext.getContextData()).containsEntry("phase", "before-invoke");

        invocationContext.setParameters(new Object[] {"invoice-18"});

        assertThat(invocationContext.getParameters()).containsExactly("invoice-18");
        assertThat(invocationContext.proceed()).isEqualTo("paid:invoice-18");
    }

    @Test
    void invocationContextSupportsConstructorInvocationState() throws Exception {
        Constructor<PaymentService> constructor = PaymentService.class.getDeclaredConstructor(String.class);
        Map<String, Object> contextData = new LinkedHashMap<>();
        contextData.put("phase", "before-construct");

        RecordingInvocationContext[] holder = new RecordingInvocationContext[1];
        holder[0] = RecordingInvocationContext.forConstructor(
                constructor,
                new Object[] {"approved:"},
                contextData,
                () -> new PaymentService((String) holder[0].getParameters()[0]));
        RecordingInvocationContext invocationContext = holder[0];

        invocationContext.setParameters(new Object[] {"captured:"});

        assertThat(invocationContext.getTarget()).isNull();
        assertThat(invocationContext.getTimer()).isNull();
        assertThat(invocationContext.getMethod()).isNull();
        assertThat(invocationContext.getConstructor()).isSameAs(constructor);
        assertThat(invocationContext.getContextData()).containsEntry("phase", "before-construct");
        assertThat(invocationContext.getParameters()).containsExactly("captured:");

        PaymentService service = (PaymentService) invocationContext.proceed();

        assertThat(service.pay("invoice-19")).isEqualTo("captured:invoice-19");
    }

    @Test
    void interceptorPriorityConstantsDefineOrderedBands() {
        assertThat(Interceptor.Priority.class).isNotNull();
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

    private static void assertAnnotationContract(Class<? extends Annotation> annotationType, ElementType... targets) {
        Retention retention = getAnnotation(annotationType, Retention.class);
        Target target = getAnnotation(annotationType, Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(targets);
    }

    private static <A extends Annotation> A getAnnotation(AnnotatedElement element, Class<A> annotationType) {
        return element.getAnnotation(annotationType);
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    private @interface Logged {
    }

    @Logged
    @Interceptor
    private static final class AuditInterceptor {
        @AroundConstruct
        Object aroundConstruct(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }

        @AroundInvoke
        Object aroundInvoke(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }

        @AroundTimeout
        Object aroundTimeout(InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }
    }

    private static final class MetricsInterceptor {
    }

    @Logged
    @Interceptors({AuditInterceptor.class, MetricsInterceptor.class})
    @ExcludeDefaultInterceptors
    private static final class PaymentService {
        private final String prefix;

        @Interceptors(AuditInterceptor.class)
        @ExcludeDefaultInterceptors
        PaymentService(String prefix) {
            this.prefix = prefix;
        }

        @Interceptors({AuditInterceptor.class, MetricsInterceptor.class})
        @ExcludeClassInterceptors
        @ExcludeDefaultInterceptors
        String pay(String invoiceId) {
            return prefix + invoiceId;
        }
    }

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

        private static RecordingInvocationContext forMethod(
                Object target,
                Method method,
                Object[] parameters,
                Object timer,
                Map<String, Object> contextData,
                ProceedAction proceedAction) {
            return new RecordingInvocationContext(target, timer, method, null, parameters, contextData, proceedAction);
        }

        private static RecordingInvocationContext forConstructor(
                Constructor<?> constructor,
                Object[] parameters,
                Map<String, Object> contextData,
                ProceedAction proceedAction) {
            return new RecordingInvocationContext(null, null, null, constructor, parameters, contextData, proceedAction);
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
        public void setParameters(Object[] parameters) {
            this.parameters = parameters;
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
