/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MutableClassToInstanceMap;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.MutableTypeToInstanceMap;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.Reflection;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeResolver;
import com.google.common.reflect.TypeToInstanceMap;
import com.google.common.reflect.TypeToken;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Generic type introspection with `com.google.common.reflect`: `TypeToken` capture,
 * resolution, `where` substitution and subtype checks, `TypeResolver`, `Invokable` methods
 * and constructors with annotated `Parameter`s, and type-keyed instance maps.
 *
 * An order service routes commands to handlers by reading the command type out of each
 * handler's generic signature, invokes handlers reflectively, builds handlers through a tiny
 * container that injects configuration named by parameter annotations, binds repositories to
 * their entity type, and keeps typed settings keyed by full generic types.
 */
public class CommandDispatcherReflectionTest {

    @Test
    void routesCommandsToHandlersByTheirGenericSignature() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.register(new CreateOrderHandler("ORD-"));
        dispatcher.register(new CancelOrderHandler());

        assertEquals(ImmutableSet.of(CreateOrder.class, CancelOrder.class), dispatcher.commandTypes());
        assertEquals(TypeToken.of(String.class), dispatcher.resultType(CreateOrder.class));
        assertEquals(TypeToken.of(Boolean.class), dispatcher.resultType(CancelOrder.class));

        String orderId = dispatcher.dispatch(new CreateOrder("acme", 3));
        assertEquals("ORD-acme-3", orderId);
        Boolean cancelled = dispatcher.dispatch(new CancelOrder(orderId));
        assertTrue(cancelled);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> dispatcher.dispatch(new CancelOrder("")));
        assertEquals("order id must not be blank", failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(new ShipOrder("ORD-1")));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(new CreateOrderHandler("X-")));
    }

    @Test
    void resolvesHandlerSupertypesThroughAbstractBaseClasses() {
        TypeToken<CancelOrderHandler> handler = TypeToken.of(CancelOrderHandler.class);

        TypeToken<? super CancelOrderHandler> contract = handler.getSupertype(CommandHandler.class);
        assertEquals(new TypeToken<CommandHandler<CancelOrder, Boolean>>() { }, contract);
        assertTrue(handler.getTypes().interfaces().rawTypes().contains(CommandHandler.class));
        assertTrue(handler.getTypes().classes().rawTypes().contains(AuditedHandler.class));
        assertTrue(handler.isSubtypeOf(new TypeToken<CommandHandler<CancelOrder, Boolean>>() { }));
        assertFalse(handler.isSubtypeOf(new TypeToken<CommandHandler<CreateOrder, String>>() { }));

        TypeToken<?> auditedResult = handler.resolveType(AuditedHandler.class.getTypeParameters()[1]);
        assertEquals(TypeToken.of(Boolean.class), auditedResult);

        TypeVariable<?> commandVariable = CommandHandler.class.getTypeParameters()[0];
        Type resolved = new TypeResolver()
                .where(commandVariable, CreateOrder.class)
                .resolveType(commandVariable);
        assertEquals(CreateOrder.class, resolved);
        assertEquals("com_google_guava.guava", Reflection.getPackageName(CommandHandler.class));
    }

    @Test
    void invokesHandlerMethodsWithResolvedSignatures() throws Exception {
        Method handle = CommandHandler.class.getMethod("handle", Command.class);
        Invokable<CreateOrderHandler, Object> invokable =
                TypeToken.of(CreateOrderHandler.class).method(handle);

        assertTrue(invokable.isPublic());
        assertTrue(invokable.isAbstract());
        assertEquals("handle", invokable.getName());
        assertEquals(TypeToken.of(CreateOrderHandler.class), invokable.getOwnerType());
        assertEquals(TypeToken.of(String.class), invokable.getReturnType());
        assertEquals(TypeToken.of(CreateOrder.class), invokable.getParameters().get(0).getType());

        Object orderId = invokable.invoke(new CreateOrderHandler("WEB-"), new CreateOrder("globex", 1));
        assertEquals("WEB-globex-1", orderId);

        Invokable<CancelOrderHandler, Object> cancel = TypeToken.of(CancelOrderHandler.class).method(handle);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> cancel.invoke(new CancelOrderHandler(), new CancelOrder(" ")));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void buildsHandlersByInjectingAnnotatedSettings() throws Exception {
        Container container = new Container(ImmutableMap.<String, Object>of("order.prefix", "B2B-", "order.limit", 5));

        CreateOrderHandler handler = container.create(CreateOrderHandler.class);
        assertEquals("B2B-initech-2", handler.handle(new CreateOrder("initech", 2)));

        LimitedCreateOrderHandler limited = container.create(LimitedCreateOrderHandler.class);
        assertEquals("B2B-hooli-5", limited.handle(new CreateOrder("hooli", 5)));
        IllegalStateException overLimit = assertThrows(IllegalStateException.class,
                () -> limited.handle(new CreateOrder("hooli", 6)));
        assertEquals("quantity 6 exceeds limit 5", overLimit.getMessage());

        Constructor<LimitedCreateOrderHandler> constructor =
                LimitedCreateOrderHandler.class.getConstructor(String.class, int.class);
        Invokable<LimitedCreateOrderHandler, LimitedCreateOrderHandler> factory =
                Invokable.from(constructor);
        ImmutableList<Parameter> parameters = factory.getParameters();
        assertEquals(2, parameters.size());
        assertEquals("order.limit", parameters.get(1).getAnnotation(Setting.class).value());
        assertTrue(parameters.get(1).getType().isPrimitive());
        assertEquals(TypeToken.of(Integer.class), parameters.get(1).getType().wrap());
        assertEquals(TypeToken.of(LimitedCreateOrderHandler.class), factory.getReturnType());

        Container missing = new Container(ImmutableMap.<String, Object>of("order.prefix", "B2B-"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> missing.create(LimitedCreateOrderHandler.class));
        assertEquals("missing setting order.limit for int", failure.getMessage());
    }

    @Test
    void bindsRepositoriesToTheirEntityTypes() {
        OrderRepository orders = new OrderRepository();
        CustomerRepository customers = new CustomerRepository();

        assertEquals(TypeToken.of(CreateOrder.class), orders.entityType());
        assertEquals(TypeToken.of(String.class), customers.entityType());
        assertEquals(new TypeToken<Map<String, List<CreateOrder>>>() { }, orders.indexType());

        orders.save("acme", new CreateOrder("acme", 1));
        orders.save("acme", new CreateOrder("acme", 4));
        assertEquals(2, orders.findAll("acme").size());
        assertThrows(ClassCastException.class, () -> orders.saveUnchecked("acme", "not an order"));

        TypeToken<Map<String, List<Integer>>> indexOfInts =
                Repository.indexType(TypeToken.of(Integer.class));
        assertEquals(new TypeToken<Map<String, List<Integer>>>() { }, indexOfInts);
        assertTrue(new TypeToken<ArrayList<String>>() { }.isSubtypeOf(new TypeToken<List<String>>() { }));
        assertFalse(new TypeToken<ArrayList<String>>() { }.isSubtypeOf(new TypeToken<List<Integer>>() { }));
        assertTrue(new TypeToken<List<? extends Number>>() { }
                .isSupertypeOf(new TypeToken<List<Integer>>() { }));
    }

    @Test
    void keepsServicesAndSettingsKeyedByType() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneOffset.UTC);
        ClassToInstanceMap<Object> services = MutableClassToInstanceMap.create();
        services.putInstance(Clock.class, clock);
        services.putInstance(Dispatcher.class, new Dispatcher());
        assertSame(clock, services.getInstance(Clock.class));
        assertNull(services.getInstance(Container.class));

        ImmutableClassToInstanceMap<CommandHandler<?, ?>> handlers =
                ImmutableClassToInstanceMap.<CommandHandler<?, ?>>builder()
                        .put(CreateOrderHandler.class, new CreateOrderHandler("SRV-"))
                        .put(CancelOrderHandler.class, new CancelOrderHandler())
                        .build();
        assertEquals("SRV-acme-1", handlers.getInstance(CreateOrderHandler.class)
                .handle(new CreateOrder("acme", 1)));

        TypeToken<List<String>> allowedRegions = new TypeToken<List<String>>() { };
        TypeToken<Map<String, Integer>> quotas = new TypeToken<Map<String, Integer>>() { };
        TypeToInstanceMap<Object> settings = new MutableTypeToInstanceMap<>();
        settings.putInstance(allowedRegions, ImmutableList.of("eu-west", "us-east"));
        settings.putInstance(quotas, ImmutableMap.of("acme", 10));
        assertEquals(ImmutableList.of("eu-west", "us-east"), settings.getInstance(allowedRegions));
        assertEquals(10, settings.getInstance(quotas).get("acme"));
        assertNull(settings.getInstance(new TypeToken<List<Integer>>() { }));

        ImmutableTypeToInstanceMap<Object> frozen = ImmutableTypeToInstanceMap.builder()
                .put(allowedRegions, ImmutableList.of("ap-south"))
                .build();
        assertEquals(ImmutableList.of("ap-south"), frozen.getInstance(allowedRegions));
        assertThrows(UnsupportedOperationException.class,
                () -> frozen.putInstance(allowedRegions, ImmutableList.of()));
    }

    /** A command whose handler returns `R`. */
    public interface Command<R> {
    }

    public record CreateOrder(String customer, int quantity) implements Command<String> {
    }

    public record CancelOrder(String orderId) implements Command<Boolean> {
    }

    public record ShipOrder(String orderId) implements Command<Instant> {
    }

    /** Handles commands of type `C`, producing results of type `R`. */
    public interface CommandHandler<C extends Command<R>, R> {
        R handle(C command);
    }

    /** A base handler that records every command it accepted before delegating. */
    public abstract static class AuditedHandler<C extends Command<R>, R> implements CommandHandler<C, R> {
        private final List<C> audit = new ArrayList<>();

        @Override
        public final R handle(C command) {
            R result = process(command);
            audit.add(command);
            return result;
        }

        protected abstract R process(C command);

        public List<C> audit() {
            return audit;
        }
    }

    /** Names the configuration key an injected constructor parameter is read from. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Setting {
        String value();
    }

    public static final class CreateOrderHandler implements CommandHandler<CreateOrder, String> {
        private final String prefix;

        public CreateOrderHandler(@Setting("order.prefix") String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String handle(CreateOrder command) {
            return prefix + command.customer() + "-" + command.quantity();
        }
    }

    public static final class LimitedCreateOrderHandler implements CommandHandler<CreateOrder, String> {
        private final CreateOrderHandler delegate;
        private final int limit;

        public LimitedCreateOrderHandler(@Setting("order.prefix") String prefix,
                @Setting("order.limit") int limit) {
            this.delegate = new CreateOrderHandler(prefix);
            this.limit = limit;
        }

        @Override
        public String handle(CreateOrder command) {
            if (command.quantity() > limit) {
                throw new IllegalStateException("quantity " + command.quantity() + " exceeds limit " + limit);
            }
            return delegate.handle(command);
        }
    }

    public static final class CancelOrderHandler extends AuditedHandler<CancelOrder, Boolean> {
        @Override
        protected Boolean process(CancelOrder command) {
            if (command.orderId().isBlank()) {
                throw new IllegalStateException("order id must not be blank");
            }
            return Boolean.TRUE;
        }
    }

    /** Routes each command to the one handler whose generic signature accepts it. */
    public static final class Dispatcher {
        private static final TypeVariable<?> COMMAND = CommandHandler.class.getTypeParameters()[0];
        private static final TypeVariable<?> RESULT = CommandHandler.class.getTypeParameters()[1];

        private final Map<Class<?>, CommandHandler<?, ?>> handlers = new LinkedHashMap<>();
        private final Map<Class<?>, Invokable<?, Object>> entryPoints = new HashMap<>();
        private final Map<Class<?>, TypeToken<?>> resultTypes = new HashMap<>();

        public void register(CommandHandler<?, ?> handler) {
            TypeToken<?> handlerType = TypeToken.of(handler.getClass());
            Class<?> commandType = handlerType.resolveType(COMMAND).getRawType();
            if (handlers.containsKey(commandType)) {
                throw new IllegalArgumentException("duplicate handler for " + commandType.getSimpleName());
            }
            handlers.put(commandType, handler);
            entryPoints.put(commandType, handlerType.method(handleMethod()));
            resultTypes.put(commandType, handlerType.resolveType(RESULT));
        }

        public ImmutableSet<Class<?>> commandTypes() {
            return ImmutableSet.copyOf(handlers.keySet());
        }

        public TypeToken<?> resultType(Class<?> commandType) {
            return resultTypes.get(commandType);
        }

        @SuppressWarnings("unchecked")
        public <R> R dispatch(Command<R> command) {
            Invokable<?, Object> entryPoint = entryPoints.get(command.getClass());
            if (entryPoint == null) {
                throw new IllegalArgumentException("no handler for " + command.getClass().getSimpleName());
            }
            try {
                return (R) invoke(entryPoint, handlers.get(command.getClass()), command);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> Object invoke(Invokable<T, Object> entryPoint, Object receiver, Command<?> command)
                throws InvocationTargetException, IllegalAccessException {
            return entryPoint.invoke((T) receiver, command);
        }

        private static Method handleMethod() {
            try {
                return CommandHandler.class.getMethod("handle", Command.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Builds handlers from their single public constructor, injecting `@Setting` values. */
    public static final class Container {
        private final Map<String, Object> settings;

        public Container(Map<String, Object> settings) {
            this.settings = settings;
        }

        public <T> T create(Class<T> type) throws ReflectiveOperationException {
            Constructor<?>[] constructors = type.getConstructors();
            if (constructors.length != 1) {
                throw new IllegalArgumentException(type.getSimpleName() + " needs exactly one public constructor");
            }
            Invokable<T, T> factory = TypeToken.of(type).constructor(constructors[0]);
            List<Object> arguments = new ArrayList<>();
            for (Parameter parameter : factory.getParameters()) {
                Parameter parameterAnnotationAccess = parameter;
                String key = parameterAnnotationAccess.getAnnotation(Setting.class).value();
                Object value = settings.get(key);
                if (value == null) {
                    throw new IllegalStateException("missing setting " + key + " for " + parameter.getType());
                }
                if (!parameter.getType().wrap().getRawType().isInstance(value)) {
                    throw new IllegalStateException("setting " + key + " is not a " + parameter.getType());
                }
                arguments.add(value);
            }
            return factory.invoke(null, arguments.toArray());
        }
    }

    /** Stores entities of type `T`, captured from the concrete repository subclass. */
    public abstract static class Repository<T> {
        private final TypeToken<T> entityType = new TypeToken<T>(getClass()) { };
        private final Map<String, List<T>> index = new HashMap<>();

        public TypeToken<T> entityType() {
            return entityType;
        }

        public TypeToken<Map<String, List<T>>> indexType() {
            return indexType(entityType);
        }

        public static <E> TypeToken<Map<String, List<E>>> indexType(TypeToken<E> entity) {
            return new TypeToken<Map<String, List<E>>>() { }.where(new TypeParameter<E>() { }, entity);
        }

        public void save(String key, T entity) {
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
        }

        public void saveUnchecked(String key, Object entity) {
            save(key, castEntity(entityType.getRawType().cast(entity)));
        }

        @SuppressWarnings("unchecked")
        private T castEntity(Object entity) {
            return (T) entity;
        }

        public List<T> findAll(String key) {
            return index.getOrDefault(key, List.of());
        }
    }

    public static final class OrderRepository extends Repository<CreateOrder> {
    }

    public static final class CustomerRepository extends Repository<String> {
    }
}
