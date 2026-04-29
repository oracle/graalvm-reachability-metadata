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
    public void createsSingletonFromClasspathPropertiesAndDefaults() {
        SaffronProperties properties = SaffronProperties.INSTANCE;

        assertThat(properties.allowInfiniteCostConverters().get()).isTrue();
        assertThat(properties.defaultCharset().get()).isEqualTo("ISO-8859-1");
        assertThat(properties.defaultNationalCharset().get()).isEqualTo("ISO-8859-1");
        assertThat(properties.defaultCollation().get()).isEqualTo("ISO-8859-1$en_US");
        assertThat(properties.defaultCollationStrength().get()).isEqualTo("primary");
        assertThat(properties.metadataHandlerCacheMaximumSize().get()).isEqualTo(1000);
    }
}
