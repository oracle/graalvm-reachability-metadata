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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.FactoryProvider;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class FactoryProviderTest {
    @Test
    void createsProxyForLegacyAssistedInjectConstructors() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(SuffixService.class).toInstance(new SuffixService("-legacy"));
                bind(LegacyWidgetFactory.class).toProvider(
                        FactoryProvider.newFactory(LegacyWidgetFactory.class, LegacyWidget.class));
            }
        });

        LegacyWidgetFactory factory = injector.getInstance(LegacyWidgetFactory.class);
        LegacyWidget widget = factory.create("widget");

        assertThat(widget.description()).isEqualTo("widget-legacy");
        assertThat(factory).isEqualTo(factory);
        assertThat(factory.hashCode()).isEqualTo(System.identityHashCode(factory));
    }

    @Test
    void inspectsFactoryMethodsWhenFallingBackToModernProvider() {
        Provider<ModernWidgetFactory> provider =
                FactoryProvider.newFactory(ModernWidgetFactory.class, ModernWidget.class);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(SuffixService.class).toInstance(new SuffixService("-modern"));
                bind(ModernWidgetFactory.class).toProvider(provider);
            }
        });

        ModernWidget widget = injector.getInstance(ModernWidgetFactory.class).create("widget");

        assertThat(widget.description()).isEqualTo("widget-modern");
    }
}

interface LegacyWidgetFactory {
    LegacyWidget create(String name);
}

final class LegacyWidget {
    private final SuffixService suffixService;
    private final String name;

    @AssistedInject
    LegacyWidget(SuffixService suffixService, @Assisted String name) {
        this.suffixService = suffixService;
        this.name = name;
    }

    String description() {
        return name + suffixService.value();
    }
}

interface ModernWidgetFactory {
    ModernWidget create(String name);
}

final class ModernWidget {
    private final SuffixService suffixService;
    private final String name;

    @Inject
    ModernWidget(SuffixService suffixService, @Assisted String name) {
        this.suffixService = suffixService;
        this.name = name;
    }

    String description() {
        return name + suffixService.value();
    }
}

final class SuffixService {
    private final String value;

    SuffixService(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}

