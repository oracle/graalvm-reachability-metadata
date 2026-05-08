/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject_extensions.guice_assistedinject;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.FactoryProvider;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class FactoryProviderAnonymous1Test {
    @Test
    void delegatesObjectToStringToInvocationHandler() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ToStringWidgetFactory.class).toProvider(
                        FactoryProvider.newFactory(ToStringWidgetFactory.class, ToStringWidget.class));
            }
        });

        ToStringWidgetFactory factory = injector.getInstance(ToStringWidgetFactory.class);

        assertThat(factory.toString()).isNotBlank();
    }

    interface ToStringWidgetFactory {
        ToStringWidget create(String name);
    }

    static final class ToStringWidget {
        @AssistedInject
        ToStringWidget(@Assisted String name) {
            assertThat(name).isNotNull();
        }
    }
}
