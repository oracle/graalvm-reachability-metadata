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
import static org.junit.jupiter.api.Assertions.assertSame;

public class SingleMethodInjectorAnonymous2Test {
    @Test
    void invokesPrivateAndProtectedInjectionMethodsAfterFieldInjection() {
        StringBuilder shared = new StringBuilder("payload");
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(StringBuilder.class).toInstance(shared);
                bindConstant().annotatedWith(Names.named("suffix")).to("done");
            }
        });
        Client client = new Client();
        injector.injectMembers(client);
        assertEquals("payload:done", client.result);
        assertSame(shared, ((BaseClient) client).received);
        assertEquals(1, client.calls);
    }

    static class BaseClient {
        private StringBuilder received;

        @Inject
        protected void initializeBase(StringBuilder dependency) {
            received = dependency;
        }
    }

    static final class Client extends BaseClient {
        @Inject private StringBuilder field;
        private String result;
        private int calls;

        @Inject
        private void initialize(@Named("suffix") String suffix) {
            result = field.toString() + ":" + suffix;
            calls++;
        }
    }
}
