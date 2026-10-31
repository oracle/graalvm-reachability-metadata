/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jpa;

import java.util.List;

import javax.persistence.metamodel.EntityType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.repository.query.QueryUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class JpaSortTest {
    @Test
    void sortsUsingJpaMetamodelAttributes() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            database.repository().saveAllAndFlush(List.of(
                    new CatalogItem("Spoon", "kitchen", 5),
                    new CatalogItem("Fork", "kitchen", 5),
                    new CatalogItem("Knife", "kitchen", 2)));
            database.entityManager().clear();
            EntityType<CatalogItem> type = database.entityManager().getMetamodel().entity(CatalogItem.class);
            JpaSort sort = JpaSort.of(Sort.Direction.DESC, type.getSingularAttribute("quantity"))
                    .and(Sort.Direction.ASC, type.getSingularAttribute("name"));

            assertThat(database.repository().findAll(sort)).extracting(CatalogItem::getName)
                    .containsExactly("Fork", "Spoon", "Knife");
        }
    }

    @Test
    void explicitlyAllowsFunctionExpressionsInJpqlSorts() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            database.repository().saveAllAndFlush(List.of(
                    new CatalogItem("Long name", "test", 1),
                    new CatalogItem("A", "test", 1)));
            database.entityManager().clear();
            String query = QueryUtils.applySorting("select c from CatalogItem c", JpaSort.unsafe("length(c.name)"));

            assertThat(database.entityManager().createQuery(query, CatalogItem.class).getResultList())
                    .extracting(CatalogItem::getName).containsExactly("A", "Long name");
        }
    }
}
