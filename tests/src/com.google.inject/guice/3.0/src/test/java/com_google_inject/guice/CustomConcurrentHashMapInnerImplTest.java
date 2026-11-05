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
import com.google.inject.MembersInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class CustomConcurrentHashMapInnerImplTest {
    @Test
    void cachesMemberInjectionPlansAndUsesThemForMultipleObjects() {
        StringBuilder dependency = new StringBuilder("cache");
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(StringBuilder.class).toInstance(dependency);
            }
        });
        MembersInjector<Client> plan = injector.getMembersInjector(Client.class);
        assertSame(plan, injector.getMembersInjector(Client.class));
        Client first = new Client();
        Client second = new Client();
        plan.injectMembers(first);
        plan.injectMembers(second);
        assertSame(dependency, first.value);
        assertSame(dependency, second.value);
    }

    static final class Client {
        @Inject private StringBuilder value;
    }
}
