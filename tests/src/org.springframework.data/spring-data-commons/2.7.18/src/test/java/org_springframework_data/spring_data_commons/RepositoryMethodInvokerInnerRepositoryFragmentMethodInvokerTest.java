/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.kotlin.CoroutineCrudRepository;
import reactor.core.publisher.Mono;

public class RepositoryMethodInvokerInnerRepositoryFragmentMethodInvokerTest {

    @Test
    void invokesPlainRepositoryFragmentMethod() throws Throwable {
        GreetingFragment fragment = new GreetingFragment();
        Method method = GreetingFragment.class.getMethod("greeting", String.class);
        RepositoryFragments fragments = RepositoryFragments.just(fragment);

        Object result = fragments.invoke(method, method, new Object[] {"Ada" });

        assertThat(result).isEqualTo("Hello, Ada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adaptsSuspendingRepositoryMethodToReactiveFragmentMethod() throws Exception {
        ReactiveCounter fragment = new ReactiveCounter();
        Method baseMethod = ReactiveCounter.class.getMethod("count");
        RepositoryFragments fragments = RepositoryFragments.just(fragment);
        // Spring Data exposes repositories as interface proxies; invoking the suspending `count` through one
        // routes the call to the reactive fragment exactly as a real coroutine repository would.
        CoroutineCrudRepository<Object, Object> repository = (CoroutineCrudRepository<Object, Object>) Proxy
                .newProxyInstance(CoroutineCrudRepository.class.getClassLoader(),
                        new Class<?>[] {CoroutineCrudRepository.class },
                        (proxy, method, arguments) -> fragments.invoke(method, baseMethod, arguments));

        Long result = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
                new Function2<CoroutineScope, Continuation<? super Long>, Object>() {

                    @Override
                    public Object invoke(CoroutineScope scope, Continuation<? super Long> continuation) {
                        return repository.count(continuation);
                    }
                });

        assertThat(result).isEqualTo(7L);
        assertThat(fragment.invocations).isEqualTo(1);
    }

    public static class GreetingFragment {

        public String greeting(String name) {
            return "Hello, " + name;
        }
    }

    public static class ReactiveCounter {

        private int invocations;

        public Mono<Long> count() {
            invocations++;
            return Mono.just(7L);
        }
    }
}
