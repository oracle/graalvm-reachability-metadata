/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import org.apache.bval.jsr.ApacheValidationProvider;
import org.apache.bval.jsr.DefaultValidationProviderResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultValidationProviderResolverTest {

    @Test
    void discoversProviderFromServiceDescriptor() {
        assertThat(new DefaultValidationProviderResolver().getValidationProviders())
                .anyMatch(ApacheValidationProvider.class::isInstance);
    }
}
