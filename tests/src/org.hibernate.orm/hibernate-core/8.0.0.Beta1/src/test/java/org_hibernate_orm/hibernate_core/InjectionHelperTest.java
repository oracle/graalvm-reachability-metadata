/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.SessionFactory;
import org_hibernate_orm.hibernate_core.entity.Gem;
import org_hibernate_orm.hibernate_core.entity.Gem_;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InjectionHelperTest {

    @Test
    void staticMetamodelFieldsArePopulatedAndUsableInCriteria() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Gem.class)
                .buildSessionFactory()) {
            assertThat(Gem_.id.isId()).isTrue();
            assertThat(Gem_.name.getName()).isEqualTo(Gem_.NAME);
            assertThat(Gem_.carats.getJavaType()).isEqualTo(Integer.class);

            sessionFactory.inTransaction(session -> {
                session.persist(new Gem(1L, "Ruby", 3));
                session.persist(new Gem(2L, "Opal", 1));
            });
            List<String> names = sessionFactory.fromTransaction(session -> {
                CriteriaBuilder builder = session.getCriteriaBuilder();
                CriteriaQuery<String> query = builder.createQuery(String.class);
                Root<Gem> gem = query.from(Gem.class);
                query.select(gem.get(Gem_.name)).where(builder.greaterThan(gem.get(Gem_.carats), 2));
                return session.createSelectionQuery(query).getResultList();
            });

            assertThat(names).containsExactly("Ruby");
        }
    }
}
