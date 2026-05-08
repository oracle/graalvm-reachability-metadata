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
import java.lang.invoke.MethodHandles;
import org.junit.jupiter.api.Test;

public class FactoryProvider2InnerPrivateLookupTest {
    @Test
    void fallsBackToPrivateLookupForGeneratedBridgeDefaultMethod() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BridgeSuffixService.class).toInstance(new BridgeSuffixService("-bridge"));
                install(new FactoryModuleBuilder()
                        .withLookups(MethodHandles.publicLookup())
                        .build(BridgeWidgetFactory.class));
            }
        });

        BridgeWidgetFactory factory = injector.getInstance(BridgeWidgetFactory.class);
        BridgeWidget directWidget = factory.create("direct");
        GenericBridgeFactory<BridgeWidget> genericFactory = factory;
        BridgeWidget genericWidget = genericFactory.create("generic");

        assertThat(directWidget.description()).isEqualTo("direct-bridge");
        assertThat(genericWidget.description()).isEqualTo("generic-bridge");
    }

    private interface GenericBridgeFactory<T> {
        T create(@Assisted String name);
    }

    private interface BridgeWidgetFactory extends GenericBridgeFactory<BridgeWidget> {
        @Override
        BridgeWidget create(@Assisted String name);
    }

    static final class BridgeWidget {
        private final BridgeSuffixService suffixService;
        private final String name;

        @Inject
        BridgeWidget(BridgeSuffixService suffixService, @Assisted String name) {
            this.suffixService = suffixService;
            this.name = name;
        }

        String description() {
            return name + suffixService.value();
        }
    }

    static final class BridgeSuffixService {
        private final String value;

        BridgeSuffixService(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }
}
