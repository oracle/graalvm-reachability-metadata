/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableDeferredKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.MutexKt;
import org.junit.jupiter.api.Test;

/**
 * Suspend-function reflection: a remote-operation dispatcher finds the suspend members of a
 * coroutine service interface and invokes them through `callSuspend` and `callSuspendBy`.
 *
 * The dispatcher only knows the service's `KClass`. It lists the operations whose `KFunction`
 * is suspending, reports which parameters carry Kotlin defaults, binds named arguments to
 * `KParameter`s, and runs the call inside the caller's coroutine so a suspension parks it
 * instead of a thread.
 */
class SuspendOperationDispatcherTest {

    /** Dispatches named suspend operations on a service through its Kotlin interface. */
    static final class SuspendOperationDispatcher<S> {

        private final Map<String, KFunction<?>> operations = new HashMap<>();

        SuspendOperationDispatcher(Class<S> serviceInterface) {
            KClass<S> service = JvmClassMappingKt.getKotlinClass(serviceInterface);
            for (KFunction<?> function : KClasses.getMemberFunctions(service)) {
                if (function.isSuspend()) {
                    operations.put(function.getName(), function);
                }
            }
        }

        /** Names of the suspend operations the service exposes, sorted. */
        List<String> operationNames() {
            return new ArrayList<>(new TreeSet<>(operations.keySet()));
        }

        /** Describes an operation's value parameters as `name: Type` with `?` and `= default`. */
        List<String> signature(String name) {
            List<String> parameters = new ArrayList<>();
            for (KParameter parameter : KCallables.getValueParameters(operation(name))) {
                KClass<?> type = (KClass<?>) parameter.getType().getClassifier();
                parameters.add(parameter.getName() + ": " + type.getSimpleName()
                        + (parameter.getType().isMarkedNullable() ? "?" : "")
                        + (parameter.isOptional() ? " = default" : ""));
            }
            return parameters;
        }

        /** Invokes `name` on `target` with positional arguments in the caller's coroutine. */
        Object invoke(S target, String name, List<Object> arguments, Continuation<Object> continuation) {
            Object[] callArguments = new Object[arguments.size() + 1];
            callArguments[0] = target;
            for (int i = 0; i < arguments.size(); i++) {
                callArguments[i + 1] = arguments.get(i);
            }
            return KCallables.callSuspend(operation(name), callArguments, continuation);
        }

        /** Invokes `name` on `target` with arguments bound by parameter name. */
        Object invokeNamed(S target, String name, Map<String, Object> arguments, Continuation<Object> continuation) {
            KFunction<?> function = operation(name);
            Map<KParameter, Object> bound = new HashMap<>();
            bound.put(KCallables.getInstanceParameter(function), target);
            for (Map.Entry<String, Object> argument : arguments.entrySet()) {
                KParameter parameter = KCallables.findParameterByName(function, argument.getKey());
                if (parameter == null) {
                    throw new IllegalArgumentException(name + " has no parameter " + argument.getKey());
                }
                bound.put(parameter, argument.getValue());
            }
            return KCallables.callSuspendBy(function, bound, continuation);
        }

        private KFunction<?> operation(String name) {
            KFunction<?> function = operations.get(name);
            if (function == null) {
                throw new IllegalArgumentException(name + " is not a suspend operation");
            }
            return function;
        }
    }

    /** Runs `block` as the body of a blocking coroutine and returns its result. */
    @SuppressWarnings("unchecked")
    private static <T> T runSuspending(Function2<CoroutineScope, Continuation<Object>, Object> block)
            throws InterruptedException {
        Function2<? super CoroutineScope, ? super Continuation<? super T>, ?> body =
                (Function2<? super CoroutineScope, ? super Continuation<? super T>, ?>) (Object) block;
        return BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, body);
    }

    @Test
    void catalogsOnlySuspendingMembersWithTheirParameters() {
        SuspendOperationDispatcher<Mutex> locks = new SuspendOperationDispatcher<>(Mutex.class);
        SuspendOperationDispatcher<CompletableDeferred> replies =
                new SuspendOperationDispatcher<>(CompletableDeferred.class);

        assertThat(locks.operationNames()).containsExactly("lock");
        assertThat(locks.signature("lock")).containsExactly("owner: Any? = default");
        assertThat(replies.operationNames()).contains("await", "join").doesNotContain("complete", "cancel");
        assertThat(replies.signature("await")).isEmpty();
    }

    @Test
    void awaitsAReplyCompletedAfterTheCallSuspended() throws InterruptedException {
        SuspendOperationDispatcher<CompletableDeferred> replies =
                new SuspendOperationDispatcher<>(CompletableDeferred.class);
        CompletableDeferred<String> reply = CompletableDeferredKt.CompletableDeferred(null);
        List<String> events = new ArrayList<>();

        Object result = runSuspending((scope, continuation) -> {
            BuildersKt.launch(scope, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT, (child, step) -> {
                events.add("responder completes");
                reply.complete("order-4411 accepted");
                return Unit.INSTANCE;
            });
            events.add("caller awaits");
            return replies.invoke(reply, "await", List.of(), continuation);
        });

        assertThat(result).isEqualTo("order-4411 accepted");
        assertThat(events).containsExactly("caller awaits", "responder completes");
    }

    @Test
    void bindsPositionalAndNamedArguments() throws InterruptedException {
        SuspendOperationDispatcher<Mutex> locks = new SuspendOperationDispatcher<>(Mutex.class);
        Mutex anonymous = MutexKt.Mutex(false);
        Mutex owned = MutexKt.Mutex(false);

        runSuspending((scope, continuation) -> {
            List<Object> noOwner = new ArrayList<>();
            noOwner.add(null);
            return locks.invoke(anonymous, "lock", noOwner, continuation);
        });
        runSuspending((scope, continuation) ->
                locks.invokeNamed(owned, "lock", Map.of("owner", "billing-worker"), continuation));

        assertThat(anonymous.isLocked()).isTrue();
        assertThat(owned.isLocked()).isTrue();
        assertThat(owned.holdsLock("billing-worker")).isTrue();
        assertThat(owned.holdsLock("audit-worker")).isFalse();

        owned.unlock("billing-worker");
        anonymous.unlock(null);
        assertThat(owned.isLocked()).isFalse();
        assertThat(anonymous.isLocked()).isFalse();
    }

    @Test
    void rejectsUnknownOperationsAndParameters() {
        SuspendOperationDispatcher<Mutex> locks = new SuspendOperationDispatcher<>(Mutex.class);
        Mutex mutex = MutexKt.Mutex(false);

        assertThatThrownBy(() -> runSuspending((scope, continuation) ->
                locks.invoke(mutex, "unlock", List.of(), continuation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unlock is not a suspend operation");
        assertThatThrownBy(() -> runSuspending((scope, continuation) ->
                locks.invokeNamed(mutex, "lock", Map.of("holder", "x"), continuation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lock has no parameter holder");
        assertThat(mutex.isLocked()).isFalse();
    }
}
