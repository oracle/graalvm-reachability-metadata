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
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class QueryByExamplePredicateBuilderTest {
    @Test
    void matchesBeanPropertiesIgnoringCaseAndIgnoredFields() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            repository.saveAllAndFlush(List.of(
                    new CatalogItem("Green Tea", "drinks", 6),
                    new CatalogItem("Black Tea", "drinks", 8),
                    new CatalogItem("Teapot", "kitchen", 3)));
            database.entityManager().clear();
            CatalogItem probe = new CatalogItem("TEA", "drinks", 999);
            ExampleMatcher matcher = ExampleMatcher.matchingAll().withIgnoreCase()
                    .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING).withIgnorePaths("quantity");
            Example<CatalogItem> example = Example.of(probe, matcher);

            assertThat(repository.findAll(example, Sort.by("name")))
                    .extracting(CatalogItem::getName).containsExactly("Black Tea", "Green Tea");
            assertThat(repository.count(example)).isEqualTo(2);
            assertThat(repository.exists(example)).isTrue();
            List<CatalogItem> fluentResults = repository.findBy(example, query -> query.sortBy(Sort.by("name")).all());
            assertThat(fluentResults).extracting(CatalogItem::getName).containsExactly("Black Tea", "Green Tea");
        }
    }

    @Test
    void treatsSqlWildcardCharactersInExamplesAsLiteralText() {
        try (JpaTestDatabase database = new JpaTestDatabase()) {
            SimpleJpaRepository<CatalogItem, Long> repository = database.repository();
            repository.saveAllAndFlush(List.of(
                    new CatalogItem("Offer 20%_off", "sale", 1),
                    new CatalogItem("Offer 200Xoff", "sale", 2)));
            database.entityManager().clear();
            ExampleMatcher matcher = ExampleMatcher.matching()
                    .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);
            Example<CatalogItem> example = Example.of(new CatalogItem("20%_", null, null), matcher);

            assertThat(repository.findOne(example).orElseThrow().getName()).isEqualTo("Offer 20%_off");
            assertThat(repository.count(example)).isEqualTo(1);
            Example<CatalogItem> absent = Example.of(new CatalogItem("unavailable", null, null));
            assertThat(repository.findOne(absent)).isEmpty();
            assertThat(repository.exists(absent)).isFalse();
        }
    }
}
