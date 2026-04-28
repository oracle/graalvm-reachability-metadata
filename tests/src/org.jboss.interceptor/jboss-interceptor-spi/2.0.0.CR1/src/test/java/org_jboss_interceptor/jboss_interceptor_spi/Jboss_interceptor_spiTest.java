/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_spi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.interceptor.InvocationContext;

import org.jboss.interceptor.spi.context.InterceptionChain;
import org.jboss.interceptor.spi.context.InvocationContextFactory;
import org.jboss.interceptor.spi.instance.InterceptorInstantiator;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorReference;
import org.jboss.interceptor.spi.metadata.MethodMetadata;
import org.jboss.interceptor.spi.model.InterceptionModel;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jboss_interceptor_spiTest {

    @Test
    public void interceptionTypesExposeStableNamesAnnotationClassesAndLifecycleClassification() {
        assertThat(InterceptionType.values()).containsExactly(
                InterceptionType.AROUND_INVOKE,
                InterceptionType.AROUND_TIMEOUT,
                InterceptionType.POST_CONSTRUCT,
                InterceptionType.PRE_DESTROY,
                InterceptionType.POST_ACTIVATE,
                InterceptionType.PRE_PASSIVATE
        );

        assertThat(InterceptionType.AROUND_INVOKE.annotationClassName()).isEqualTo("javax.interceptor.AroundInvoke");
        assertThat(InterceptionType.AROUND_TIMEOUT.annotationClassName()).isEqualTo("javax.interceptor.AroundTimeout");
        assertThat(InterceptionType.POST_CONSTRUCT.annotationClassName()).isEqualTo("javax.annotation.PostConstruct");
        assertThat(InterceptionType.PRE_DESTROY.annotationClassName()).isEqualTo("javax.annotation.PreDestroy");
        assertThat(InterceptionType.POST_ACTIVATE.annotationClassName()).isEqualTo("javax.ejb.PostActivate");
        assertThat(InterceptionType.PRE_PASSIVATE.annotationClassName()).isEqualTo("javax.ejb.PrePassivate");

        assertThat(InterceptionType.AROUND_INVOKE.isLifecycleCallback()).isFalse();
        assertThat(InterceptionType.AROUND_TIMEOUT.isLifecycleCallback()).isFalse();
        assertThat(InterceptionType.POST_CONSTRUCT.isLifecycleCallback()).isTrue();
        assertThat(InterceptionType.PRE_DESTROY.isLifecycleCallback()).isTrue();
        assertThat(InterceptionType.POST_ACTIVATE.isLifecycleCallback()).isTrue();
        assertThat(InterceptionType.PRE_PASSIVATE.isLifecycleCallback()).isTrue();
    }

    @Test
    public void interceptionTypeValueOfReturnsConstantsAndRejectsUnknownNames() {
        for (InterceptionType type : InterceptionType.values()) {
            assertThat(InterceptionType.valueOf(type.name())).isSameAs(type);
        }

        assertThatThrownBy(() -> InterceptionType.valueOf("AROUND_CONSTRUCT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void metadataObjectsExposeClassHierarchyDeclaredMethodsInterceptorReferencesAndEligibility() {
        SimpleMethodMetadata aroundInvoke = new SimpleMethodMetadata(
                String.class,
                InterceptionType.AROUND_INVOKE,
                InterceptionType.AROUND_TIMEOUT
        );
        SimpleMethodMetadata postConstruct = new SimpleMethodMetadata(void.class, InterceptionType.POST_CONSTRUCT);
        SimpleClassMetadata<ParentService> parentClass = new SimpleClassMetadata<>(
                ParentService.class,
                null,
                List.of(postConstruct)
        );
        SimpleClassMetadata<BusinessService> businessClass = new SimpleClassMetadata<>(
                BusinessService.class,
                parentClass,
                List.of(aroundInvoke)
        );
        SimpleInterceptorReference<String> reference = new SimpleInterceptorReference<>(
                "audit-interceptor",
                businessClass
        );
        SimpleInterceptorMetadata<String> metadata = new SimpleInterceptorMetadata<>(
                reference,
                false,
                aroundInvoke,
                postConstruct
        );
        InterceptorInstantiator<ManagedInterceptor<String>, String> instantiator = ManagedInterceptor::new;

        assertThat(businessClass.getJavaClass()).isEqualTo(BusinessService.class);
        assertThat(businessClass.getClassName()).isEqualTo(BusinessService.class.getName());
        assertThat(businessClass.getSuperclass()).isSameAs(parentClass);
        assertThat(businessClass.getDeclaredMethods()).containsExactly(aroundInvoke);
        assertThat(parentClass.getDeclaredMethods()).containsExactly(postConstruct);

        assertThat(aroundInvoke.getJavaMethod()).isNull();
        assertThat(aroundInvoke.getReturnType()).isEqualTo(String.class);
        assertThat(aroundInvoke.getSupportedInterceptionTypes())
                .containsExactly(InterceptionType.AROUND_INVOKE, InterceptionType.AROUND_TIMEOUT);
        assertThat(postConstruct.getReturnType()).isEqualTo(void.class);
        assertThat(postConstruct.getSupportedInterceptionTypes()).containsExactly(InterceptionType.POST_CONSTRUCT);

        assertThat(reference.getInterceptor()).isEqualTo("audit-interceptor");
        assertThat(reference.getClassMetadata()).isSameAs(businessClass);
        assertThat(metadata.getInterceptorReference()).isSameAs(reference);
        assertThat(metadata.getInterceptorClass()).isSameAs(businessClass);
        assertThat(metadata.getInterceptorMethods(InterceptionType.AROUND_INVOKE)).containsExactly(aroundInvoke);
        assertThat(metadata.getInterceptorMethods(InterceptionType.AROUND_TIMEOUT)).containsExactly(aroundInvoke);
        assertThat(metadata.getInterceptorMethods(InterceptionType.POST_CONSTRUCT)).containsExactly(postConstruct);
        assertThat(metadata.getInterceptorMethods(InterceptionType.PRE_DESTROY)).isEmpty();
        assertThat(metadata.isEligible(InterceptionType.AROUND_INVOKE)).isTrue();
        assertThat(metadata.isEligible(InterceptionType.PRE_DESTROY)).isFalse();
        assertThat(metadata.isTargetClass()).isFalse();

        ManagedInterceptor<String> managedInterceptor = instantiator.createFor(reference);

        assertThat(managedInterceptor.getReference()).isSameAs(reference);
        assertThat(managedInterceptor.getInterceptor()).isEqualTo("audit-interceptor");
    }

    @Test
    public void interceptionModelReturnsRegisteredLifecycleInterceptorsAndValidatesBusinessMethodNullability() {
        SimpleMethodMetadata postConstructMethod = new SimpleMethodMetadata(
                void.class,
                InterceptionType.POST_CONSTRUCT
        );
        SimpleInterceptorMetadata<String> lifecycleInterceptor = interceptorMetadata(
                "lifecycle-interceptor",
                true,
                postConstructMethod
        );
        SimpleInterceptionModel<String, String> model = new SimpleInterceptionModel<>("ShoppingCartService");

        model.register(InterceptionType.POST_CONSTRUCT, lifecycleInterceptor);

        assertThat(model.getInterceptedEntity()).isEqualTo("ShoppingCartService");
        assertThat(model.getInterceptors(InterceptionType.POST_CONSTRUCT, null)).containsExactly(lifecycleInterceptor);
        assertThat(model.getInterceptors(InterceptionType.PRE_DESTROY, null)).isEmpty();
        assertThat(model.getAllInterceptors()).containsExactly(lifecycleInterceptor);

        assertThatThrownBy(() -> model.getInterceptors(InterceptionType.AROUND_INVOKE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a method");
        assertThatThrownBy(() -> model.getInterceptors(InterceptionType.AROUND_TIMEOUT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a method");
    }

    @Test
    public void interceptionModelAggregatesUniqueInterceptorsAcrossLifecycleCallbacks() {
        SimpleMethodMetadata postConstructMethod = new SimpleMethodMetadata(
                void.class,
                InterceptionType.POST_CONSTRUCT
        );
        SimpleMethodMetadata preDestroyMethod = new SimpleMethodMetadata(
                void.class,
                InterceptionType.PRE_DESTROY
        );
        SimpleMethodMetadata prePassivateMethod = new SimpleMethodMetadata(
                void.class,
                InterceptionType.PRE_PASSIVATE
        );
        SimpleInterceptorMetadata<String> lifecycleInterceptor = interceptorMetadata(
                "shared-lifecycle-interceptor",
                true,
                postConstructMethod,
                preDestroyMethod
        );
        SimpleInterceptorMetadata<String> passivationInterceptor = interceptorMetadata(
                "passivation-interceptor",
                false,
                prePassivateMethod
        );
        SimpleInterceptionModel<String, String> model = new SimpleInterceptionModel<>("InventoryService");

        model.register(InterceptionType.POST_CONSTRUCT, lifecycleInterceptor);
        model.register(InterceptionType.PRE_DESTROY, lifecycleInterceptor);
        model.register(InterceptionType.PRE_PASSIVATE, passivationInterceptor);

        assertThat(model.getInterceptors(InterceptionType.POST_CONSTRUCT, null)).containsExactly(lifecycleInterceptor);
        assertThat(model.getInterceptors(InterceptionType.PRE_DESTROY, null)).containsExactly(lifecycleInterceptor);
        assertThat(model.getInterceptors(InterceptionType.PRE_PASSIVATE, null)).containsExactly(passivationInterceptor);
        assertThat(model.getAllInterceptors()).containsExactlyInAnyOrder(lifecycleInterceptor, passivationInterceptor);
    }

    @Test
    public void interceptionModelReturnsBusinessInterceptorsForSpecificMethods() throws Exception {
        Method checkout = BusinessOperations.class.getMethod("checkout", String.class);
        Method cancel = BusinessOperations.class.getMethod("cancel", String.class);
        SimpleMethodMetadata aroundInvokeMethod = new SimpleMethodMetadata(
                checkout,
                String.class,
                InterceptionType.AROUND_INVOKE
        );
        SimpleMethodMetadata aroundTimeoutMethod = new SimpleMethodMetadata(
                checkout,
                void.class,
                InterceptionType.AROUND_TIMEOUT
        );
        SimpleInterceptorMetadata<String> auditInterceptor = interceptorMetadata(
                "audit-business-interceptor",
                false,
                aroundInvokeMethod
        );
        SimpleInterceptorMetadata<String> timeoutInterceptor = interceptorMetadata(
                "timeout-business-interceptor",
                false,
                aroundTimeoutMethod
        );
        SimpleInterceptionModel<Class<BusinessOperations>, String> model = new SimpleInterceptionModel<>(
                BusinessOperations.class
        );

        model.register(InterceptionType.AROUND_INVOKE, checkout, auditInterceptor);
        model.register(InterceptionType.AROUND_TIMEOUT, checkout, timeoutInterceptor);

        assertThat(aroundInvokeMethod.getJavaMethod()).isSameAs(checkout);
        assertThat(model.getInterceptors(InterceptionType.AROUND_INVOKE, checkout)).containsExactly(auditInterceptor);
        assertThat(model.getInterceptors(InterceptionType.AROUND_INVOKE, cancel)).isEmpty();
        assertThat(model.getInterceptors(InterceptionType.AROUND_TIMEOUT, checkout)).containsExactly(timeoutInterceptor);
        assertThat(model.getAllInterceptors()).containsExactlyInAnyOrder(auditInterceptor, timeoutInterceptor);
        assertThatThrownBy(() -> model.getInterceptors(InterceptionType.POST_CONSTRUCT, checkout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not receive a method");
    }

    @Test
    public void invocationContextFactoryCreatesMethodInvocationContextsBackedByAnInterceptionChain() throws Exception {
        RecordingInterceptionChain chain = new RecordingInterceptionChain(List.of(
                context -> {
                    context.getContextData().put("first", context.getParameters()[0]);
                    context.setParameters(new Object[] {"normalized", 2});
                    return context.proceed() + ":first";
                },
                context -> {
                    context.getContextData().put("second", context.getParameters()[1]);
                    return "terminal:" + context.getTarget() + ":" + context.getParameters()[0];
                }
        ));
        InvocationContextFactory factory = new RecordingInvocationContextFactory();

        InvocationContext context = factory.newInvocationContext(chain, "service", null, new Object[] {" raw ", 1});
        Object result = context.proceed();

        assertThat(result).isEqualTo("terminal:service:normalized:first");
        assertThat(context.getTarget()).isEqualTo("service");
        assertThat(context.getMethod()).isNull();
        assertThat(context.getParameters()).containsExactly("normalized", 2);
        assertThat(context.getContextData())
                .containsEntry("first", " raw ")
                .containsEntry("second", 2);
        assertThat(context.getTimer()).isNull();
        assertThat(chain.hasNextInterceptor()).isFalse();
    }

    @Test
    public void invocationContextFactoryCreatesTimerContextsAndInterceptionChainPropagatesFailures() throws Exception {
        TimerDescriptor timer = new TimerDescriptor("daily-cleanup", 7);
        RecordingInterceptionChain timerChain = new RecordingInterceptionChain(List.of(
                context -> {
                    TimerDescriptor currentTimer = (TimerDescriptor) context.getTimer();
                    context.getContextData().put("timerName", currentTimer.getName());
                    context.getContextData().put("priority", currentTimer.getPriority());
                    return "timer:" + context.getContextData().get("timerName");
                }
        ));
        InvocationContextFactory factory = new RecordingInvocationContextFactory();

        InvocationContext timerContext = factory.newInvocationContext(timerChain, null, null, timer);
        Object timerResult = timerContext.proceed();

        assertThat(timerResult).isEqualTo("timer:daily-cleanup");
        assertThat(timerContext.getTimer()).isSameAs(timer);
        assertThat(timerContext.getParameters()).isEmpty();
        assertThat(timerContext.getContextData())
                .containsEntry("timerName", "daily-cleanup")
                .containsEntry("priority", 7);
        assertThat(timerChain.hasNextInterceptor()).isFalse();

        RecordingInterceptionChain failingChain = new RecordingInterceptionChain(List.of(context -> {
            throw new IllegalStateException("interceptor failed");
        }));
        InvocationContext failingContext = factory.newInvocationContext(failingChain, "service", null, new Object[0]);

        assertThatThrownBy(failingContext::proceed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("interceptor failed");
        assertThat(failingChain.hasNextInterceptor()).isFalse();
    }

    private static SimpleInterceptorMetadata<String> interceptorMetadata(
            String interceptor,
            boolean targetClass,
            MethodMetadata... methods
    ) {
        SimpleClassMetadata<BusinessService> classMetadata = new SimpleClassMetadata<>(
                BusinessService.class,
                null,
                List.of(methods)
        );
        SimpleInterceptorReference<String> reference = new SimpleInterceptorReference<>(interceptor, classMetadata);
        return new SimpleInterceptorMetadata<>(reference, targetClass, methods);
    }

    private static class ParentService {
    }

    private static final class BusinessService extends ParentService {
    }

    public static final class BusinessOperations {

        public String checkout(String item) {
            return item;
        }

        public void cancel(String orderId) {
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

    private interface ChainStep {

        Object invoke(InvocationContext context) throws Exception;
    }

    private static final class RecordingInterceptionChain implements InterceptionChain {

        private final List<ChainStep> steps;
        private int nextStepIndex;

        private RecordingInterceptionChain(List<ChainStep> steps) {
            this.steps = steps;
        }

        @Override
        public Object invokeNextInterceptor(InvocationContext invocationContext) throws Throwable {
            if (!hasNextInterceptor()) {
                throw new IllegalStateException("No interceptor remains in the chain");
            }
            ChainStep step = steps.get(nextStepIndex);
            nextStepIndex++;
            return step.invoke(invocationContext);
        }

        @Override
        public boolean hasNextInterceptor() {
            return nextStepIndex < steps.size();
        }
    }

    private static final class RecordingInvocationContextFactory implements InvocationContextFactory {

        private static final long serialVersionUID = 1L;

        @Override
        public InvocationContext newInvocationContext(
                InterceptionChain chain,
                Object target,
                Method method,
                Object[] args
        ) {
            return new RecordingInvocationContext(chain, target, method, args, null);
        }

        @Override
        public InvocationContext newInvocationContext(
                InterceptionChain chain,
                Object target,
                Method method,
                Object timer
        ) {
            return new RecordingInvocationContext(chain, target, method, new Object[0], timer);
        }
    }

    private static final class RecordingInvocationContext implements InvocationContext {

        private final InterceptionChain chain;
        private final Object target;
        private final Method method;
        private final Map<String, Object> contextData = new LinkedHashMap<>();
        private final Object timer;
        private Object[] parameters;

        private RecordingInvocationContext(
                InterceptionChain chain,
                Object target,
                Method method,
                Object[] parameters,
                Object timer
        ) {
            this.chain = chain;
            this.target = target;
            this.method = method;
            this.parameters = parameters;
            this.timer = timer;
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
            try {
                return chain.invokeNextInterceptor(this);
            } catch (Exception exception) {
                throw exception;
            } catch (Error error) {
                throw error;
            } catch (Throwable throwable) {
                throw new Exception(throwable);
            }
        }
    }

    private static final class SimpleClassMetadata<T> implements ClassMetadata<T> {

        private static final long serialVersionUID = 1L;

        private final Class<T> javaClass;
        private final ClassMetadata<?> superclass;
        private final List<MethodMetadata> declaredMethods;

        private SimpleClassMetadata(
                Class<T> javaClass,
                ClassMetadata<?> superclass,
                List<MethodMetadata> declaredMethods
        ) {
            this.javaClass = javaClass;
            this.superclass = superclass;
            this.declaredMethods = List.copyOf(declaredMethods);
        }

        @Override
        public Iterable<MethodMetadata> getDeclaredMethods() {
            return declaredMethods;
        }

        @Override
        public Class<T> getJavaClass() {
            return javaClass;
        }

        @Override
        public String getClassName() {
            return javaClass.getName();
        }

        @Override
        public ClassMetadata<?> getSuperclass() {
            return superclass;
        }
    }

    private static final class SimpleMethodMetadata implements MethodMetadata {

        private final Method javaMethod;
        private final Class<?> returnType;
        private final Set<InterceptionType> supportedInterceptionTypes;

        private SimpleMethodMetadata(Class<?> returnType, InterceptionType... supportedInterceptionTypes) {
            this(null, returnType, supportedInterceptionTypes);
        }

        private SimpleMethodMetadata(Method javaMethod, Class<?> returnType, InterceptionType... supportedInterceptionTypes) {
            this.javaMethod = javaMethod;
            this.returnType = returnType;
            this.supportedInterceptionTypes = new LinkedHashSet<>(List.of(supportedInterceptionTypes));
        }

        @Override
        public Method getJavaMethod() {
            return javaMethod;
        }

        @Override
        public Set<InterceptionType> getSupportedInterceptionTypes() {
            return Collections.unmodifiableSet(supportedInterceptionTypes);
        }

        @Override
        public Class<?> getReturnType() {
            return returnType;
        }
    }

    private static final class SimpleInterceptorReference<T> implements InterceptorReference<T> {

        private static final long serialVersionUID = 1L;

        private final T interceptor;
        private final ClassMetadata<?> classMetadata;

        private SimpleInterceptorReference(T interceptor, ClassMetadata<?> classMetadata) {
            this.interceptor = interceptor;
            this.classMetadata = classMetadata;
        }

        @Override
        public T getInterceptor() {
            return interceptor;
        }

        @Override
        public ClassMetadata<?> getClassMetadata() {
            return classMetadata;
        }
    }

    private static final class SimpleInterceptorMetadata<T> implements InterceptorMetadata<T> {

        private static final long serialVersionUID = 1L;

        private final InterceptorReference<T> reference;
        private final ClassMetadata<?> interceptorClass;
        private final boolean targetClass;
        private final Map<InterceptionType, List<MethodMetadata>> methodsByType = new EnumMap<>(InterceptionType.class);

        private SimpleInterceptorMetadata(
                InterceptorReference<T> reference,
                boolean targetClass,
                MethodMetadata... methods
        ) {
            this.reference = reference;
            this.interceptorClass = reference.getClassMetadata();
            this.targetClass = targetClass;
            for (MethodMetadata method : methods) {
                for (InterceptionType type : method.getSupportedInterceptionTypes()) {
                    methodsByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(method);
                }
            }
        }

        @Override
        public InterceptorReference<T> getInterceptorReference() {
            return reference;
        }

        @Override
        public ClassMetadata<?> getInterceptorClass() {
            return interceptorClass;
        }

        @Override
        public List<MethodMetadata> getInterceptorMethods(InterceptionType interceptionType) {
            return List.copyOf(methodsByType.getOrDefault(interceptionType, List.of()));
        }

        @Override
        public boolean isEligible(InterceptionType interceptionType) {
            return !getInterceptorMethods(interceptionType).isEmpty();
        }

        @Override
        public boolean isTargetClass() {
            return targetClass;
        }
    }

    private static final class ManagedInterceptor<T> {

        private final InterceptorReference<T> reference;

        private ManagedInterceptor(InterceptorReference<T> reference) {
            this.reference = reference;
        }

        private InterceptorReference<T> getReference() {
            return reference;
        }

        private T getInterceptor() {
            return reference.getInterceptor();
        }
    }

    private static final class SimpleInterceptionModel<T, I> implements InterceptionModel<T, I> {

        private static final long serialVersionUID = 1L;

        private final T interceptedEntity;
        private final Map<InterceptionType, List<InterceptorMetadata<I>>> interceptorsByType = new EnumMap<>(
                InterceptionType.class
        );
        private final Map<InterceptionType, Map<Method, List<InterceptorMetadata<I>>>> interceptorsByMethod = new EnumMap<>(
                InterceptionType.class
        );

        private SimpleInterceptionModel(T interceptedEntity) {
            this.interceptedEntity = interceptedEntity;
        }

        private void register(InterceptionType type, InterceptorMetadata<I> interceptorMetadata) {
            interceptorsByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(interceptorMetadata);
        }

        private void register(InterceptionType type, Method method, InterceptorMetadata<I> interceptorMetadata) {
            interceptorsByMethod
                    .computeIfAbsent(type, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(method, ignored -> new ArrayList<>())
                    .add(interceptorMetadata);
        }

        @Override
        public List<InterceptorMetadata<I>> getInterceptors(InterceptionType interceptionType, Method method) {
            if (!interceptionType.isLifecycleCallback() && method == null) {
                throw new IllegalArgumentException(interceptionType + " requires a method");
            }
            if (interceptionType.isLifecycleCallback() && method != null) {
                throw new IllegalArgumentException(interceptionType + " must not receive a method");
            }
            if (interceptionType.isLifecycleCallback()) {
                return List.copyOf(interceptorsByType.getOrDefault(interceptionType, List.of()));
            }
            return List.copyOf(
                    interceptorsByMethod
                            .getOrDefault(interceptionType, Map.of())
                            .getOrDefault(method, List.of())
            );
        }

        @Override
        public Set<InterceptorMetadata<I>> getAllInterceptors() {
            Set<InterceptorMetadata<I>> interceptors = new LinkedHashSet<>();
            for (List<InterceptorMetadata<I>> typeInterceptors : interceptorsByType.values()) {
                interceptors.addAll(typeInterceptors);
            }
            for (Map<Method, List<InterceptorMetadata<I>>> methodInterceptors : interceptorsByMethod.values()) {
                for (List<InterceptorMetadata<I>> interceptorsForMethod : methodInterceptors.values()) {
                    interceptors.addAll(interceptorsForMethod);
                }
            }
            return Set.copyOf(interceptors);
        }

        @Override
        public T getInterceptedEntity() {
            return interceptedEntity;
        }
    }
}
