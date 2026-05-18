/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.ServiceFinder;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceFinderInnerLazyIteratorTest {
    @Test
    void discoversAndInstantiatesProviderWithExplicitClassLoader() {
        ClassLoader classLoader = ServiceFinderInnerLazyIteratorTest.class.getClassLoader();
        Iterator<SampleService> providers = ServiceFinder.find(SampleService.class, classLoader).iterator();

        assertThat(providers.hasNext()).isTrue();
        SampleService provider = providers.next();

        assertThat(provider.name()).isEqualTo("sample-provider");
        assertThat(providers.hasNext()).isFalse();
    }

    @Test
    void queriesSystemResourcesWhenNoClassLoaderIsProvided() {
        Iterator<MissingService> providers = ServiceFinder.find(MissingService.class, null).iterator();

        assertThat(providers.hasNext()).isFalse();
    }

    public interface SampleService {
        String name();
    }

    public static class SampleServiceProvider implements SampleService {
        @Override
        public String name() {
            return "sample-provider";
        }
    }

    public interface MissingService {
    }
}
