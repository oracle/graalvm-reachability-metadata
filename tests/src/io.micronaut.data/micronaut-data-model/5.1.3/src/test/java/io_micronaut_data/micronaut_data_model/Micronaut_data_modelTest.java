/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.vector.SparseFloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.model.vector.search.Similarity;
import org.junit.jupiter.api.Test;

public class Micronaut_data_modelTest {

    @Test
    void createsAndTransformsOffsetPagesThroughPublicFactories() {
        Sort sort = Sort.of(Sort.Order.asc("title"), Sort.Order.desc("createdAt", true));
        Pageable pageable = Pageable.from(1, 2, sort);
        Page<String> page = Page.of(List.of("first", "second"), pageable, 5L);

        assertThat(sort.isSorted()).isTrue();
        assertThat(sort.getOrderBy()).hasSize(2);
        assertThat(sort.getOrderBy().get(0).getProperty()).isEqualTo("title");
        assertThat(sort.getOrderBy().get(1).isIgnoreCase()).isTrue();
        assertThat(page.getContent()).containsExactly("first", "second");
        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getOffset()).isEqualTo(2);
        assertThat(page.getNumberOfElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextPageable().getNumber()).isEqualTo(2);
        assertThat(page.previousPageable().getNumber()).isZero();
        assertThat(page.map(String::toUpperCase).getContent())
                .containsExactly("FIRST", "SECOND");
        assertThat(page).containsExactly("first", "second");
    }

    @Test
    void createsCursorPagesAndNavigatesWithReturnedCursors() {
        Sort sort = Sort.of(Sort.Order.asc("id"));
        Pageable.Cursor initialCursor = Pageable.Cursor.of("item-0", 0);
        CursoredPageable after = Pageable.afterCursor(initialCursor, 1, 3, sort);
        List<Pageable.Cursor> resultCursors = List.of(
                Pageable.Cursor.of("item-1", 1),
                Pageable.Cursor.of("item-2", 2),
                Pageable.Cursor.of("item-3", 3));
        CursoredPage<String> page = CursoredPage.of(
                List.of("one", "two", "three"), after, resultCursors, 9L);

        assertThat(after.getMode()).isEqualTo(Pageable.Mode.CURSOR_NEXT);
        assertThat(after.cursor()).contains(initialCursor);
        assertThat(after.isBackward()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.getCursor(0)).contains(resultCursors.get(0));
        assertThat(page.nextPageable().cursor()).contains(resultCursors.get(2));
        assertThat(page.previousPageable().cursor()).contains(resultCursors.get(0));
        assertThat(page.map(String::toUpperCase).getContent())
                .containsExactly("ONE", "TWO", "THREE");

        CursoredPageable before = Pageable.beforeCursor(initialCursor, 2, 3, sort);
        assertThat(before.getMode()).isEqualTo(Pageable.Mode.CURSOR_PREVIOUS);
        assertThat(before.isBackward()).isTrue();
        assertThat(before.cursor()).contains(initialCursor);
    }

    @Test
    void buildsPersistentPropertiesFromLibraryBeanIntrospection() {
        BeanIntrospection<Page> pageIntrospection = BeanIntrospection.getIntrospection(Page.class);
        assertThat(pageIntrospection.getPropertyNames())
                .contains("content", "pageable", "totalPages");

        BeanIntrospection<Sort.Order> orderIntrospection =
                BeanIntrospection.getIntrospection(Sort.Order.class);
        PersistentEntity orderEntity = PersistentEntity.of(orderIntrospection);
        assertThat(orderEntity.getPersistentPropertyNames())
                .contains("property", "direction", "ignoreCase");
        assertThat(orderEntity.getPropertyByName("direction")
                .isAssignable(Sort.Order.Direction.class)).isTrue();
    }

    @Test
    void appliesModelLimitsIntrospectedNamingStrategiesAndDataTypes() {
        Pageable pageable = Pageable.from(2, 25, Sort.of(Sort.Order.asc("name")));
        Limit limit = pageable.getLimit();
        BeanIntrospection<NamingStrategies.KebabCase> introspection =
                BeanIntrospection.getIntrospection(NamingStrategies.KebabCase.class);
        NamingStrategy kebabCase = introspection.instantiate();

        assertThat(limit.isLimited()).isTrue();
        assertThat(limit.maxResults()).isEqualTo(25);
        assertThat(limit.offset()).isEqualTo(50);
        assertThat(new NamingStrategies.Raw().mappedName("BookTitle")).isEqualTo("BookTitle");
        assertThat(kebabCase.mappedName("bookTitle")).isEqualTo("book-title");
        assertThat(DataType.forType(String.class)).isEqualTo(DataType.STRING);
        assertThat(DataType.forType(int[].class)).isEqualTo(DataType.INTEGER_ARRAY);
        assertThat(DataType.INTEGER.isNumeric()).isTrue();
        assertThat(DataType.STRING.isNumeric()).isFalse();
    }

    @Test
    void createsDenseVectorsAndRoundTripsThemThroughSparseRepresentation() {
        Vector vector = Vector.of(0.0f, 1.5f, 0.0f, -2.25f);
        SparseFloatVector sparseVector = vector.toSparseFloatVector();

        assertThat(vector.getType()).isEqualTo(float.class);
        assertThat(vector.toFloatArray()).containsExactly(0.0f, 1.5f, 0.0f, -2.25f);
        assertThat(sparseVector.length()).isEqualTo(4);
        assertThat(sparseVector.indices()).containsExactly(1, 3);
        assertThat(sparseVector.values()).containsExactly(1.5f, -2.25f);
        assertThat(sparseVector.toDenseVector().toFloatArray())
                .containsExactly(0.0f, 1.5f, 0.0f, -2.25f);
        assertThat(sparseVector.toDoubleArray()).containsExactly(0.0, 1.5, 0.0, -2.25);
    }

    @Test
    void returnsVectorSearchResultsWithScoresAndSimilarities() {
        SearchResult<String> nearest = new SearchResult<>(
                "nearest", new Score(0.92), new Similarity(0.97));
        SearchResult<String> next = new SearchResult<>("next", new Score(0.51));
        SearchResults<String> results = SearchResults.of(List.of(nearest, next));

        assertThat(results.results()).containsExactly(nearest, next);
        assertThat(results).containsExactly(nearest, next);
        assertThat(nearest.entity()).isEqualTo("nearest");
        assertThat(nearest.score().value()).isEqualTo(0.92);
        assertThat(nearest.similarity().value()).isEqualTo(0.97);
        assertThat(next.similarity()).isNull();
    }

    @Test
    void representsSlicesAndGeometriesWithValueSemantics() {
        Pageable unpaged = Pageable.unpaged();
        Slice<Integer> slice = Slice.of(List.of(2, 4, 6), unpaged);
        Point point = Point.fromCoords(List.of(12.5, -4.25));

        assertThat(slice.isEmpty()).isFalse();
        assertThat(slice.getSize()).isEqualTo(-1);
        assertThat(slice.getNumberOfElements()).isEqualTo(3);
        assertThat(slice.map(number -> number * 2).getContent())
                .containsExactly(4, 8, 12);
        assertThat(point.asCoords()).containsExactly(12.5, -4.25);
        assertThat(new Point(12.5, -4.25)).isEqualTo(point);
        assertThat(Page.empty().isEmpty()).isTrue();
        assertThat(CursoredPage.empty().isEmpty()).isTrue();
    }
}
