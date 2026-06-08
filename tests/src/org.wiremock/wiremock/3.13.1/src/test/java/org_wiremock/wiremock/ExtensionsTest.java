/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionDeclarations;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.Extensions;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ExtensionsTest {
    @Test
    void loadInstantiatesExtensionsDeclaredByClassNameAndFactoryClass() {
        WireMockConfiguration options = WireMockConfiguration.options()
                .extensionScanningEnabled(false)
                .templatingEnabled(false);
        options.extensions(ClassNameExtension.class.getName());
        options.extensionFactories(DeclaredExtensionFactory.class);

        ExtensionDeclarations declarations = options.getDeclaredExtensions();
        Extensions extensions = new Extensions(declarations, null, options, null, null);

        extensions.load();

        assertThat(extensions.getAllExtensionNames())
                .contains("class-name-extension", "factory-class-extension");
        assertThat(extensions.ofType(ClassNameExtension.class))
                .containsKey("class-name-extension");
        assertThat(extensions.ofType(FactoryClassExtension.class))
                .containsKey("factory-class-extension");
    }

    public static class ClassNameExtension implements Extension {
        @Override
        public String getName() {
            return "class-name-extension";
        }
    }

    public static class FactoryClassExtension implements Extension {
        @Override
        public String getName() {
            return "factory-class-extension";
        }
    }

    public static class DeclaredExtensionFactory implements ExtensionFactory {
        @Override
        public List<Extension> create(WireMockServices services) {
            assertThat(services.getOptions()).isNotNull();
            return List.of(new FactoryClassExtension());
        }
    }
}
