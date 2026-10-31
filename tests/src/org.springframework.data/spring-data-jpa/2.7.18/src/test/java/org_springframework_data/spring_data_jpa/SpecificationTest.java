/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jpa;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class SpecificationTest {
    @Test
    void composesCriteriaAndExecutesFilteredQueries() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            repository.saveAllAndFlush(List.of(
                    new CatalogItem("Apple", "food", 12),
                    new CatalogItem("Pear", "food", 2),
                    new CatalogItem("Plate", "kitchen", 8)));
            database.entityManager().clear();
            Specification<CatalogItem> food = (root, query, builder) -> builder.equal(root.get("category"), "food");
            Specification<CatalogItem> stocked = (root, query, builder) -> builder.greaterThan(root.get("quantity"), 5);
            Specification<CatalogItem> selected = Specification.where(food).and(stocked);

            assertThat(repository.findOne(selected).orElseThrow().getName()).isEqualTo("Apple");
            assertThat(repository.count(selected)).isEqualTo(1);
            assertThat(repository.exists(selected)).isTrue();
            assertThat(repository.findAll(food.or(stocked), Sort.by("name")))
                    .extracting(CatalogItem::getName).containsExactly("Apple", "Pear", "Plate");
            assertThat(repository.findAll(Specification.not(food)))
                    .extracting(CatalogItem::getName).containsExactly("Plate");
            assertThat(repository.findAll(Specification.where(null))).hasSize(3);
        }
    }
}
