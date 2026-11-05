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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SingleFieldInjectorTest {
    @Test
    void injectsPrivateInheritedAndQualifiedFieldsIntoAnExistingObject() {
        StringBuilder shared = new StringBuilder("shared");
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(StringBuilder.class).toInstance(shared);
                bindConstant().annotatedWith(Names.named("region")).to("west");
            }
        });
        Client client = new Client();
        injector.injectMembers(client);
        assertSame(shared, ((BaseClient) client).dependency);
        assertEquals("west", client.region);
        assertNull(client.optional);
    }

    static class BaseClient {
        @Inject private StringBuilder dependency;
    }

    static final class Client extends BaseClient {
        @Inject @Named("region") private String region;
        @Inject(optional = true) private Runnable optional;
    }
}
