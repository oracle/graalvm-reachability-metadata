/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.QBean;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QBeanTest {
    @Test
    void fieldsProjectionCreatesBeanAndWritesDeclaredFields() {
        QBean<FieldProjection> projection = Projections.fields(
                FieldProjection.class,
                Expressions.stringPath("givenName"),
                Expressions.numberPath(Integer.class, "age"));

        FieldProjection result = projection.newInstance("Ada", 36);

        assertThat(result.givenName).isEqualTo("Ada");
        assertThat(result.age).isEqualTo(36);
    }

    @Test
    void beanProjectionCreatesBeanAndInvokesSetters() {
        QBean<SetterProjection> projection = Projections.bean(
                SetterProjection.class,
                Expressions.stringPath("givenName"),
                Expressions.numberPath(Integer.class, "age"));

        SetterProjection result = projection.newInstance("Grace", 85);

        assertThat(result.getGivenName()).isEqualTo("Grace");
        assertThat(result.getAge()).isEqualTo(85);
    }

    public static final class FieldProjection {
        public String givenName;
        public Integer age;

        public FieldProjection() {
        }
    }

    public static final class SetterProjection {
        private String givenName;
        private Integer age;

        public SetterProjection() {
        }

        public String getGivenName() {
            return givenName;
        }

        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}
