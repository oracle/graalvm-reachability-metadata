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
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DefaultConstructionProxyFactoryAnonymous2Test {
    @Test
    void constructsAnUnscopedClientWithPrivateInjectedConstructor() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("greeting")).to("hello");
            }
        });
        Client first = injector.getInstance(Client.class);
        Client second = injector.getInstance(Client.class);
        assertEquals("hello", first.greeting);
        assertEquals("hello", second.greeting);
        assertNotSame(first, second);
    }

    static final class Client {
        private final String greeting;

        @Inject
        private Client(@Named("greeting") String greeting) {
            this.greeting = greeting;
        }
    }
}
