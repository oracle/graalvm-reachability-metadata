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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import org.junit.jupiter.api.Test;

public class FactoryProvider2Test {
    @Test
    void delegatesObjectToStringToFactoryProviderInvocationHandler() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(DescriptionSuffix.class).toInstance(new DescriptionSuffix("-assisted"));
                install(new FactoryModuleBuilder().build(DescriptiveWidgetFactory.class));
            }
        });

        DescriptiveWidgetFactory factory = injector.getInstance(DescriptiveWidgetFactory.class);
        DescriptiveWidget widget = factory.create("widget");

        assertThat(widget.description()).isEqualTo("widget-assisted");
        assertThat(factory.toString()).isEqualTo(DescriptiveWidgetFactory.class.getName());
    }

    private interface DescriptiveWidgetFactory {
        DescriptiveWidget create(@Assisted String name);
    }

    static final class DescriptiveWidget {
        private final DescriptionSuffix suffix;
        private final String name;

        @Inject
        DescriptiveWidget(DescriptionSuffix suffix, @Assisted String name) {
            this.suffix = suffix;
            this.name = name;
        }

        String description() {
            return name + suffix.value();
        }
    }

    static final class DescriptionSuffix {
        private final String value;

        DescriptionSuffix(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
