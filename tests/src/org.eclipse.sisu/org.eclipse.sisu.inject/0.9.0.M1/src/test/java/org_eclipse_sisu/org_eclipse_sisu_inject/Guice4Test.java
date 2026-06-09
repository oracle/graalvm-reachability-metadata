/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.inject.Guice4;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;

public class Guice4Test {
    @Test
    void invokeStaticBindingInvokesProviderMethodBinding() {
        Injector injector = Guice.createInjector(new ProvidesModule());
        Binding<ProvidedComponent> binding = injector.getBinding(ProvidedComponent.class);

        Object value = Guice4.invokeStaticBinding(binding);

        assertThat(value).isInstanceOf(ProvidedComponent.class);
        assertThat(((ProvidedComponent) value).name()).isEqualTo("provided-by-module");
    }

    private static final class ProvidesModule extends AbstractModule {
        @Provides
        ProvidedComponent component() {
            return new ProvidedComponent("provided-by-module");
        }
    }

    private static final class ProvidedComponent {
        private final String name;

        private ProvidedComponent(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }
}
