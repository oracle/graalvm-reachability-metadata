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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class SimpleJpaRepositoryTest {
    @Test
    void persistsReloadsMergesAndDeletesAnEntity() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            CatalogItem item = new CatalogItem("Notebook", "stationery", 12);
            assertThat(repository.saveAndFlush(item)).isSameAs(item);
            assertThat(item.getId()).isNotNull();
            assertThat(item.getVersion()).isNotNull();
            Long id = item.getId();
            database.entityManager().clear();

            CatalogItem reloaded = repository.findById(id).orElseThrow();
            assertThat(reloaded).isNotSameAs(item);
            assertThat(reloaded.getName()).isEqualTo("Notebook");
            assertThat(repository.existsById(id)).isTrue();
            database.entityManager().detach(reloaded);
            reloaded.setQuantity(7);
            CatalogItem merged = repository.saveAndFlush(reloaded);
            assertThat(merged.getVersion()).isGreaterThan(reloaded.getVersion());
            database.entityManager().clear();
            assertThat(repository.findById(id).orElseThrow().getQuantity()).isEqualTo(7);

            repository.deleteById(id);
            repository.flush();
            database.entityManager().clear();
            assertThat(repository.findById(id)).isEmpty();
            assertThat(repository.existsById(id)).isFalse();
            assertThat(repository.count()).isZero();
        }
    }

    @Test
    void pagesAndSortsPersistedEntities() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            repository.saveAllAndFlush(List.of(
                    new CatalogItem("Ruler", "stationery", 3),
                    new CatalogItem("Eraser", "stationery", 8),
                    new CatalogItem("Pencil", "stationery", 15)));
            database.entityManager().clear();

            Page<CatalogItem> page = repository.findAll(PageRequest.of(0, 2, Sort.by("name")));
            assertThat(page.getContent()).extracting(CatalogItem::getName).containsExactly("Eraser", "Pencil");
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.hasNext()).isTrue();
            assertThat(repository.findAll(page.nextPageable()).getContent())
                    .extracting(CatalogItem::getName).containsExactly("Ruler");
        }
    }

    @Test
    void findsSelectedIdsAndDeletesThemInBatch() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            CatalogItem first = new CatalogItem("Tea", "food", 5);
            CatalogItem second = new CatalogItem("Coffee", "food", 9);
            CatalogItem retained = new CatalogItem("Mug", "kitchen", 4);
            repository.saveAllAndFlush(List.of(first, second, retained));
            database.entityManager().clear();

            List<Long> ids = List.of(first.getId(), second.getId());
            assertThat(repository.findAllById(ids)).extracting(CatalogItem::getName)
                    .containsExactlyInAnyOrder("Tea", "Coffee");
            repository.deleteAllByIdInBatch(ids);
            database.entityManager().clear();
            assertThat(repository.findAll()).extracting(CatalogItem::getName).containsExactly("Mug");
            repository.deleteAllInBatch();
            assertThat(repository.count()).isZero();
        }
    }
}
