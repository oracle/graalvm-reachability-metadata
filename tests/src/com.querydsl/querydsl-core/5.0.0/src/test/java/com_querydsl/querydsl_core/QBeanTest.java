/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.QBean;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

public class QBeanTest {

    @Test
    void fieldsProjectionCreatesBeanAndSetsPrivateFields() {
        QBean<FieldBackedPerson> projection = Projections.fields(
                FieldBackedPerson.class,
                Expressions.path(String.class, "name"),
                Expressions.path(Integer.class, "age"));

        FieldBackedPerson person = projection.newInstance("Ada", 36);

        assertThat(person.getName()).isEqualTo("Ada");
        assertThat(person.getAge()).isEqualTo(36);
    }

    @Test
    void beanProjectionCreatesBeanAndInvokesSetters() {
        QBean<SetterBackedPerson> projection = Projections.bean(
                SetterBackedPerson.class,
                Expressions.path(String.class, "name"),
                Expressions.path(Integer.class, "age"));

        SetterBackedPerson person = projection.newInstance("Grace", 44);

        assertThat(person.getName()).isEqualTo("Grace");
        assertThat(person.getAge()).isEqualTo(44);
    }

    public static final class FieldBackedPerson {
        private String name;
        private Integer age;

        public FieldBackedPerson() {
        }

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }
    }

    public static final class SetterBackedPerson {
        private String name;
        private Integer age;

        public SetterBackedPerson() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}
