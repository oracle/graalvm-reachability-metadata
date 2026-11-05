/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ProviderMethodTest {
    @Test
    void invokesAPrivateScopedProviderWithResolvedArguments() {
        ServiceModule module = new ServiceModule();
        Injector injector = Guice.createInjector(module);
        StringBuilder result = injector.getInstance(StringBuilder.class);
        assertEquals("service", result.toString());
        assertSame(result, injector.getInstance(StringBuilder.class));
        assertEquals(1, module.calls.get());
    }

    static final class ServiceModule extends AbstractModule {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        protected void configure() {
            bindConstant().annotatedWith(Names.named("name")).to("service");
        }

        @Provides
        @Singleton
        private StringBuilder provideService(@Named("name") String name) {
            calls.incrementAndGet();
            return new StringBuilder(name);
        }
    }
}
