/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jpa;

import java.util.List;

import javax.persistence.TypedQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.QueryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class QueryUtilsTest {
    @Test
    void buildsExecutableSortedAndCountQueries() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            database.repository().saveAllAndFlush(List.of(
                    new CatalogItem("Zebra", "toys", 3),
                    new CatalogItem("Bear", "toys", 4),
                    new CatalogItem("Apron", "kitchen", 2)));
            database.entityManager().clear();
            String jpql = "select c from CatalogItem c where c.category = :category";
            String sorted = QueryUtils.applySorting(jpql, Sort.by(Sort.Order.asc("name").ignoreCase()));
            assertThat(sorted).endsWith("order by lower(c.name) asc");
            TypedQuery<CatalogItem> query = database.entityManager().createQuery(sorted, CatalogItem.class);
            assertThat(QueryUtils.hasNamedParameter(query)).isTrue();
            assertThat(query.setParameter("category", "toys").getResultList())
                    .extracting(CatalogItem::getName).containsExactly("Bear", "Zebra");

            String count = QueryUtils.createCountQueryFor(sorted);
            assertThat(count).doesNotContain("order by");
            assertThat(database.entityManager().createQuery(count, Long.class)
                    .setParameter("category", "toys").getSingleResult()).isEqualTo(2L);
        }
    }

    @Test
    void rejectsFunctionExpressionsInOrdinaryPropertySorts() {
        assertThatThrownBy(() -> QueryUtils.applySorting("select c from CatalogItem c", Sort.by("length(c.name)")))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }
}
