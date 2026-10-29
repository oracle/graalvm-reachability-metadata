/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ResultLoaderTest {
    @Test
    void convertsNestedQueryResultsToObjectAndPrimitiveArrays() throws Exception {
        try (SqlMapFixture fixture = new SqlMapFixture(false)) {
            SqlMapFixture.Catalog catalog = (SqlMapFixture.Catalog)
                    fixture.client().queryForObject("catalog.arrayCatalog", 7);
            assertThat(catalog.getId()).isEqualTo(7);
            assertThat(catalog.getNameArray()).containsExactly("alpha", "beta");
            assertThat(catalog.getOrderArray()).containsExactly(1, 2);
            fixture.addItem(3, "gamma");
            assertThat(catalog.getNameArray()).containsExactly("alpha", "beta");
            assertThat(catalog.getOrderArray()).containsExactly(1, 2);
        }
    }

    @Test
    void convertsEmptyNestedQueriesToEmptyArrays() throws Exception {
        try (SqlMapFixture fixture = new SqlMapFixture(false)) {
            SqlMapFixture.Catalog catalog = (SqlMapFixture.Catalog)
                    fixture.client().queryForObject("catalog.arrayCatalog", 99);
            assertThat(catalog.getNameArray()).isEmpty();
            assertThat(catalog.getOrderArray()).isEmpty();
        }
    }
}
