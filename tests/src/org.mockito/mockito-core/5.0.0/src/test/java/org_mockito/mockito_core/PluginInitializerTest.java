/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MockResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginInitializerTest {
    private static final AtomicInteger RESOLUTIONS = new AtomicInteger();

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void configuredMockResolverIsLoadedAndUsedByMockingDetails() {
        final Runnable mock = Mockito.mock(Runnable.class);
        final int resolutionsBeforeInspection = RESOLUTIONS.get();

        assertThat(Mockito.mockingDetails(mock).isMock()).isTrue();
        assertThat(RESOLUTIONS.get()).isGreaterThan(resolutionsBeforeInspection);
    }

    public static class CountingMockResolver implements MockResolver {
        @Override
        public Object resolve(Object instance) {
            RESOLUTIONS.incrementAndGet();
            return instance;
        }
    }
}
