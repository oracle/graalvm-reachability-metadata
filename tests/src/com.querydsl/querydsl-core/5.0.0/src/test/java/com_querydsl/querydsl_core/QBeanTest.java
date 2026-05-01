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
    void fieldProjectionCreatesBeanAndWritesDeclaredFields() {
        QBean<FieldAccessCustomer> projection = Projections.fields(
                FieldAccessCustomer.class,
                Expressions.stringPath("name"),
                Expressions.numberPath(Integer.class, "age"));

        FieldAccessCustomer customer = projection.newInstance("Ada", 42);

        assertThat(customer.getName()).isEqualTo("Ada");
        assertThat(customer.getAge()).isEqualTo(42);
    }

    @Test
    void beanProjectionCreatesBeanAndInvokesSetters() {
        QBean<SetterAccessCustomer> projection = Projections.bean(
                SetterAccessCustomer.class,
                Expressions.stringPath("name"),
                Expressions.numberPath(Integer.class, "age"));

        SetterAccessCustomer customer = projection.newInstance("Grace", 37);

        assertThat(customer.getName()).isEqualTo("Grace");
        assertThat(customer.getAge()).isEqualTo(37);
    }

    public static class FieldAccessCustomer {

        private String name = "unset";

        private Integer age = -1;

        public FieldAccessCustomer() {
        }

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }
    }

    public static class SetterAccessCustomer {

        private String name = "unset";

        private Integer age = -1;

        public SetterAccessCustomer() {
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
