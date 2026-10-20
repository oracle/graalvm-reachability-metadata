/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.EnumMap;
import java.util.Map;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.Meta.DatabaseProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetaInnerDatabasePropertyTest {
    @Test
    void readsATypedDatabasePropertyFromAPropertyMap() {
        Map<DatabaseProperty, Object> properties = new EnumMap<>(DatabaseProperty.class);
        properties.put(DatabaseProperty.GET_DATABASE_PRODUCT_NAME, "Kylin");

        assertThat(DatabaseProperty.GET_DATABASE_PRODUCT_NAME.getProp(properties, String.class)).isEqualTo("Kylin");
    }
}
