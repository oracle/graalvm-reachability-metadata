/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.Course;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationBinderTest extends AbstractHibernateTest {

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:mem:annotation-binder";
    }

    @Override
    protected String getHibernateDialect() {
        return "org.hibernate.dialect.H2Dialect";
    }

    @Test
    public void testDialectSpecificWhereClauseIsApplied() {
        List<Course> courses = getEntityManager()
                .createQuery("from Course order by title", Course.class)
                .getResultList();

        assertThat(courses)
                .extracting(Course::getTitle)
                .containsExactly("Math", "Statistics");
    }
}
