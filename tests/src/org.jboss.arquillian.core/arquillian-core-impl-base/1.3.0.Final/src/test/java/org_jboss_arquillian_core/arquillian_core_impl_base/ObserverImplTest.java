/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.impl.ObserverImpl;
import org.jboss.arquillian.core.spi.Manager;
import org.jboss.arquillian.core.spi.NonManagedObserver;
import org.jboss.arquillian.core.spi.ObserverMethod;
import org.junit.jupiter.api.Test;

public class ObserverImplTest {
    @Test
    void invokeCallsObserverMethodWithResolvedArguments() throws Exception {
        ObserverTarget target = new ObserverTarget();
        Collaborator collaborator = new Collaborator("resolved dependency");
        Method observerMethod = ObserverTarget.class.getDeclaredMethod("observe", String.class, Collaborator.class);
        ObserverMethod observer = ObserverImpl.of(target, observerMethod);

        boolean invoked = observer.invoke(new ResolvingManager(collaborator), "payload");

        assertThat(invoked).isTrue();
        assertThat(target.event()).isEqualTo("payload");
        assertThat(target.collaborator()).isSameAs(collaborator);
        assertThat(target.message()).isEqualTo("resolved dependency");
    }

    private static final class ObserverTarget {
        private String event;
        private Collaborator collaborator;

        private void observe(@Observes String observedEvent, Collaborator resolvedCollaborator) {
            this.event = observedEvent;
            this.collaborator = resolvedCollaborator;
        }

        private String event() {
            return event;
        }

        private Collaborator collaborator() {
            return collaborator;
        }

        private String message() {
            return collaborator.message();
        }
    }

    private static final class Collaborator {
        private final String message;

        private Collaborator(String message) {
            this.message = message;
        }

        private String message() {
            return message;
        }
    }

    private static final class ResolvingManager implements Manager {
        private final Collaborator collaborator;

        private ResolvingManager(Collaborator collaborator) {
            this.collaborator = collaborator;
        }

        @Override
        public void fire(Object event) {
        }

        @Override
        public <T> void fire(T event, NonManagedObserver<T> nonManagedObserver) {
        }

        @Override
        public <T> T resolve(Class<T> type) {
            if (type == Collaborator.class) {
                return type.cast(collaborator);
            }
            return null;
        }

        @Override
        public <T> void bind(Class<? extends Annotation> scope, Class<T> type, T instance) {
        }

        @Override
        public void inject(Object target) {
        }

        @Override
        public <T> T getContext(Class<T> type) {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }
    }
}
