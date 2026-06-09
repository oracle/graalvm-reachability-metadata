/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Provider;

import org.eclipse.sisu.inject.Guice4;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProvidesMethodBinding;

public class Guice4Test {
    @Test
    void invokeStaticBindingInvokesGuiceProvidesMethodBinding() {
        ProvidesModule module = new ProvidesModule();
        Injector injector = Guice.createInjector(module);
        Binding<ProvidedService> binding = injector.getBinding(ProvidedService.class);
        Provider<?> provider = Guice4.getProviderInstance((ProviderInstanceBinding<?>) binding);

        assertThat(provider).isInstanceOf(ProvidesMethodBinding.class);
        Object value = Guice4.invokeStaticBinding(binding);

        assertThat(value).isSameAs(module.service());
        assertThat(module.invocations()).isEqualTo(1);
    }

    private static final class ProvidesModule extends AbstractModule {
        private final ProvidedService service = new ProvidedService();
        private int invocations;

        @Provides
        ProvidedService provideService() {
            invocations++;
            return service;
        }

        private ProvidedService service() {
            return service;
        }

        private int invocations() {
            return invocations;
        }
    }

    private static final class ProvidedService {
    }
}
