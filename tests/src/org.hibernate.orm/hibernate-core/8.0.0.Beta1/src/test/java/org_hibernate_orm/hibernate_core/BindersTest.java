/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.AttributeBinderType;
import org.hibernate.annotations.TypeBinderType;
import org.hibernate.binder.AttributeBinder;
import org.hibernate.binder.TypeBinder;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BindersTest {

    private static final List<String> BOUND = new CopyOnWriteArrayList<>();

    @Test
    void customBinderAnnotationsAdjustTheMapping() {
        BOUND.clear();
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Contract.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Contract(1L, "2024-01-01", "draft")));
            sessionFactory.inTransaction(session -> {
                Contract contract = session.find(Contract.class, 1L);
                contract.signedOn = "2030-12-31";
                contract.status = "signed";
            });
            Object[] stored = sessionFactory.fromTransaction(session -> session.createNativeQuery(
                    "select signedOn, status from Contract", Object[].class).getSingleResult());

            assertThat(stored).containsExactly("2024-01-01", "signed");
        }
        assertThat(BOUND).containsExactlyInAnyOrder("type:contracts", "attribute:signedOn");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @TypeBinderType(binder = TableCommentBinder.class)
    public @interface TableComment {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @AttributeBinderType(binder = InsertOnlyBinder.class)
    public @interface InsertOnly {
    }

    public static class TableCommentBinder implements TypeBinder<TableComment> {

        @Override
        public void bind(TableComment annotation, MetadataBuildingContext buildingContext, PersistentClass persistentClass) {
            persistentClass.getTable().setComment(annotation.value());
            BOUND.add("type:" + annotation.value());
        }

        @Override
        public void bind(TableComment annotation, MetadataBuildingContext buildingContext, Component embeddableClass) {
            BOUND.add("embeddable:" + annotation.value());
        }
    }

    public static class InsertOnlyBinder implements AttributeBinder<InsertOnly> {

        @Override
        public void bind(
                InsertOnly annotation,
                MetadataBuildingContext buildingContext,
                PersistentClass persistentClass,
                Property property) {
            property.setUpdatable(false);
            BOUND.add("attribute:" + property.getName());
        }
    }

    @Entity(name = "Contract")
    @TableComment("contracts")
    public static class Contract {

        @Id
        private Long id;

        @InsertOnly
        private String signedOn;

        private String status;

        public Contract() {
        }

        public Contract(Long id, String signedOn, String status) {
            this.id = id;
            this.signedOn = signedOn;
            this.status = status;
        }
    }
}
