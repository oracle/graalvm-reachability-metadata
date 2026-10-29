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
public class LazyResultLoaderTest {
    @Test
    void loadsNestedListsAndSetsOnFirstUseAndRetainsLoadedResults() throws Exception {
        try (SqlMapFixture fixture = new SqlMapFixture(true)) {
            SqlMapFixture.Catalog catalog = (SqlMapFixture.Catalog)
                    fixture.client().queryForObject("catalog.collectionCatalog", 7);
            assertThat(catalog.getId()).isEqualTo(7);
            fixture.addItem(3, "gamma");
            assertThat(catalog.getNames()).containsExactly("alpha", "beta", "gamma");
            assertThat(catalog.getUniqueNames()).containsExactlyInAnyOrder("alpha", "beta", "gamma");
            fixture.addItem(4, "delta");
            assertThat(catalog.getNames()).hasSize(3).doesNotContain("delta");
            assertThat(catalog.getUniqueNames()).hasSize(3).doesNotContain("delta");
        }
    }

    @Test
    void representsEmptyNestedCollections() throws Exception {
        try (SqlMapFixture fixture = new SqlMapFixture(true)) {
            SqlMapFixture.Catalog catalog = (SqlMapFixture.Catalog)
                    fixture.client().queryForObject("catalog.collectionCatalog", 99);
            assertThat(catalog.getNames()).isEmpty();
            assertThat(catalog.getUniqueNames()).isEmpty();
        }
    }
}
