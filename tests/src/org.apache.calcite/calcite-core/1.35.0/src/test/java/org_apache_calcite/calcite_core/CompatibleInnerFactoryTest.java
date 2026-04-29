/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.Compatible;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static org.assertj.core.api.Assertions.assertThat;

public class CompatibleInnerFactoryTest {
    @Test
    public void compatibleInstanceCreatesPrivateLookupForRequestedClass() {
        MethodHandles.Lookup lookup = Compatible.INSTANCE.lookupPrivate(PrivateLookupTarget.class);

        assertThat(lookup.lookupClass()).isEqualTo(PrivateLookupTarget.class);
        assertThat(lookup.lookupModes() & MethodHandles.Lookup.PRIVATE)
            .isEqualTo(MethodHandles.Lookup.PRIVATE);
    }

    public static class PrivateLookupTarget {
        private PrivateLookupTarget() {
        }
    }
}
