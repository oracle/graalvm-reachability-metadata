/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Scopes;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.junit.jupiter.api.Assertions.assertSame;

public class FinalizableReferenceQueueTest {
    @Test
    void resolvesACustomScopeUsingTheWeakAnnotationCaches() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindScope(ApplicationScoped.class, Scopes.SINGLETON);
            }
        });
        Client first = injector.getInstance(Client.class);
        assertSame(first, injector.getInstance(Client.class));
        assertSame(first, injector.getProvider(Client.class).get());
    }

    @ScopeAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ApplicationScoped {
    }

    @ApplicationScoped
    static final class Client {
        @Inject
        private Client() {
        }
    }
}
