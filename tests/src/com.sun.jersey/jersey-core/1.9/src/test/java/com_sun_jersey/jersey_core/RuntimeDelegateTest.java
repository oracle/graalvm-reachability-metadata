/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

public class RuntimeDelegateTest {
    @Test
    void reportsClassLoaderLocationsWhenConfiguredProviderHasWrongType() {
        final String originalProperty = System.getProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        try {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, PlainProvider.class.getName());

            assertThatThrownBy(RuntimeDelegate::getInstance)
                    .satisfies(RuntimeDelegateTest::assertWrongProviderTypeFailure);
        } finally {
            restoreRuntimeDelegateProperty(originalProperty);
            RuntimeDelegate.setInstance(null);
        }
    }

    private static void assertWrongProviderTypeFailure(final Throwable throwable) {
        if (throwable instanceof LinkageError) {
            assertThat(throwable).hasMessageContaining("ClassCastException");
        } else {
            assertThat(throwable)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(NullPointerException.class);
        }
    }

    private static void restoreRuntimeDelegateProperty(final String originalProperty) {
        if (originalProperty == null) {
            System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, originalProperty);
        }
    }

    public static final class PlainProvider {
        public PlainProvider() {
        }
    }
}
