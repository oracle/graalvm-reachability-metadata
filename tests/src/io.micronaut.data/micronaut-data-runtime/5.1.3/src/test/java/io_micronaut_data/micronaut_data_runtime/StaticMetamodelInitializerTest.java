/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import org.junit.jupiter.api.Test;

public class StaticMetamodelInitializerTest {

    @Test
    void initializesStaticMetamodelAttributesWhenCreatingCriteriaRoot() {
        RuntimeCriteriaBuilder criteriaBuilder = new RuntimeCriteriaBuilder();
        PersistentEntityCriteriaQuery<CriteriaBook> query = criteriaBuilder.createQuery(CriteriaBook.class);

        PersistentEntityRoot<CriteriaBook> root = query.from(CriteriaBook.class);
        PersistentPropertyPath<String> titlePath = root.get(CriteriaBook_.title);

        assertThat(titlePath.getProperty().getName()).isEqualTo("title");
        assertThat(titlePath.getJavaType()).isEqualTo(String.class);
    }

    @MappedEntity
    @Introspected
    public static class CriteriaBook {
        @Id
        private Long id;
        private String title;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @StaticMetamodel(CriteriaBook.class)
    public static class CriteriaBook_ {
        public static SingularAttribute<CriteriaBook, String> title;
    }
}
