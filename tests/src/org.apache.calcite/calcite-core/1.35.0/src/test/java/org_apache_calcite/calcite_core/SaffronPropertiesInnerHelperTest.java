/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.SaffronProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SaffronPropertiesInnerHelperTest {
    @Test
    void loadsDefaultSaffronProperties() {
        assertThat(SaffronProperties.INSTANCE.defaultCollationStrength().get())
                .isEqualTo("primary");
        assertThat(SaffronProperties.INSTANCE.metadataHandlerCacheMaximumSize().get())
                .isPositive();
    }
}
