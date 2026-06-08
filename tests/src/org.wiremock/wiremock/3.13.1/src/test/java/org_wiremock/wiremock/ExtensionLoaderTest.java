/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionLoader;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExtensionLoaderTest {
    @Test
    void loadExtensionInstantiatesExtensionsDeclaredByClassName() {
        Map<String, Extension> extensions =
                ExtensionLoader.loadExtension(LoadableExtension.class.getName());

        assertThat(extensions).containsOnlyKeys("loadable-extension");
        assertThat(extensions.get("loadable-extension")).isInstanceOf(LoadableExtension.class);
    }

    public static class LoadableExtension implements Extension {
        @Override
        public String getName() {
            return "loadable-extension";
        }
    }
}
