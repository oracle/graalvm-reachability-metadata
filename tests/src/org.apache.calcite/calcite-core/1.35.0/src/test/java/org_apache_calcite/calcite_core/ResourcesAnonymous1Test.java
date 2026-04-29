/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.CalciteResource;
import org.apache.calcite.runtime.Resources;
import org.apache.calcite.util.SaffronProperties;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesAnonymous1Test {
    @Test
    public void createsResourceInstancesThroughCalciteResourceProxy() {
        CalciteResource resource = Resources.create(CalciteResource.class);

        Resources.ExInst<?> inst = resource.illegalIntervalLiteral("INTERVAL '1' DAY", "line 1, column 1");

        assertThat(inst.getProperties()).containsEntry("SQLSTATE", "42000");
    }

    @Test
    public void createsPropertyInstancesThroughSaffronPropertiesProxy() {
        Properties properties = new Properties();
        properties.setProperty("saffron.metadata.handler.cache.maximum.size", "321");
        SaffronProperties saffronProperties = Resources.create(properties, SaffronProperties.class);

        Resources.IntProp cacheMaximumSize = saffronProperties.metadataHandlerCacheMaximumSize();

        assertThat(cacheMaximumSize.isSet()).isTrue();
        assertThat(cacheMaximumSize.get()).isEqualTo(321);
    }
}
