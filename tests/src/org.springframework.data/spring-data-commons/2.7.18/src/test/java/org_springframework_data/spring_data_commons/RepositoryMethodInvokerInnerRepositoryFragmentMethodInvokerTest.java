/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.kotlin.CoroutineCrudRepository;
import reactor.core.publisher.Mono;

public class RepositoryMethodInvokerInnerRepositoryFragmentMethodInvokerTest {

    @Test
    void invokesRegularFragmentMethodThroughRepositoryComposition() throws Throwable {
        RepositoryComposition composition = RepositoryComposition.just(new GreetingFragment());
        Method repositoryMethod = GreetingRepository.class.getMethod("greeting", String.class);

        Object result = composition.invoke(repositoryMethod, "Ada");

        assertThat(result).isEqualTo("Hello Ada");
    }

    @Test
    void adaptsCoroutineRepositoryMethodToReactiveFragmentMethod() throws Throwable {
        SampleEntity entity = new SampleEntity("id-1");
        ReactiveCrudFragment fragment = new ReactiveCrudFragment(entity);
        RepositoryFragments fragments = RepositoryFragments.just(fragment);
        Method declaredMethod = CoroutineCrudRepository.class.getMethod("findById", Object.class, Continuation.class);
        Method baseMethod = ReactiveCrudFragment.class.getMethod("findById", String.class);
        CapturingContinuation<SampleEntity> continuation = new CapturingContinuation<>();

        Object result = fragments.invoke(declaredMethod, baseMethod, new Object[] { "id-1", continuation });

        SampleEntity resolvedEntity = result instanceof SampleEntity ? (SampleEntity) result : continuation.awaitValue();
        assertThat(resolvedEntity).isSameAs(entity);
        assertThat(fragment.requestedId).isEqualTo("id-1");
    }

    interface GreetingRepository {

        String greeting(String name);
    }

    public static final class GreetingFragment implements GreetingRepository {

        @Override
        public String greeting(String name) {
            return "Hello " + name;
        }
    }

    public static final class ReactiveCrudFragment {

        private final SampleEntity entity;
        private String requestedId;

        ReactiveCrudFragment(SampleEntity entity) {
            this.entity = entity;
        }

        public Mono<SampleEntity> findById(String id) {
            requestedId = id;
            return Mono.just(entity);
        }
    }

    public static final class SampleEntity {

        private final String id;

        SampleEntity(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }
    }

    static final class CapturingContinuation<T> implements Continuation<T> {

        private final CountDownLatch resumed = new CountDownLatch(1);
        private T value;
        private Throwable failure;

        @Override
        public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void resumeWith(Object result) {
            try {
                ResultKt.throwOnFailure(result);
                value = (T) result;
            } catch (Throwable throwable) {
                failure = throwable;
            } finally {
                resumed.countDown();
            }
        }

        T awaitValue() throws Throwable {
            assertThat(resumed.await(5, TimeUnit.SECONDS)).isTrue();

            if (failure != null) {
                throw failure;
            }

            return value;
        }
    }
}
