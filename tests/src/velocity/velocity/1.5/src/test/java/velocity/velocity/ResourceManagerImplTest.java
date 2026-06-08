/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Vector;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.resource.ResourceManagerImpl;
import org.junit.jupiter.api.Test;

public class ResourceManagerImplTest {
    @Test
    public void fallsBackToDefaultResourceCacheWhenConfiguredCacheClassHasWrongType() throws Exception {
        final ResourceManagerImpl resourceManager = new ResourceManagerImpl();

        resourceManager.initialize(new CacheFallbackRuntime());

        assertThat(resourceManager.getLoaderNameForResource("missing.vm")).isNull();
    }

    private static final class CacheFallbackRuntime extends RuntimeInstance {
        private final ExtendedProperties configuration = new ExtendedProperties();

        private CacheFallbackRuntime() {
            configuration.setProperty(RuntimeConstants.RESOURCE_LOADER, new Vector<String>());
        }

        @Override
        public ExtendedProperties getConfiguration() {
            return configuration;
        }

        @Override
        public String getString(final String key) {
            if (RuntimeConstants.RESOURCE_MANAGER_CACHE_CLASS.equals(key)) {
                return ResourceManagerImpl.class.getName();
            }
            return null;
        }

        @Override
        public boolean getBoolean(final String key, final boolean def) {
            return def;
        }

        @Override
        public int getInt(final String key, final int defaultValue) {
            return defaultValue;
        }
    }
}
