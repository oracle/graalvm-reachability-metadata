/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.ReflectionUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void getFieldOrNullFindsDeclaredFieldOnSuperclass() {
        Field field = ReflectionUtils.getFieldOrNull(PremiumAccount.class, "id");

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("id");
        assertThat(field.getDeclaringClass()).isEqualTo(Account.class);
    }

    @Test
    void getGetterOrNullFindsBooleanGetterOnSuperclass() {
        Method method = ReflectionUtils.getGetterOrNull(PremiumAccount.class, "active", boolean.class);

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("isActive");
        assertThat(method.getDeclaringClass()).isEqualTo(Account.class);
    }

    @Test
    void getFieldsCollectsDeclaredFieldsFromClassHierarchy() {
        Set<Field> fields = ReflectionUtils.getFields(PremiumAccount.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("tier", "id", "active");
    }

    @Test
    void getTypeParameterAsClassConvertsGenericArrayTypeToArrayClass() {
        Class<?> arrayClass = ReflectionUtils.getTypeParameterAsClass(
                ListArrayHolder.class.getGenericSuperclass(), 0);

        assertThat(arrayClass).isEqualTo(List[].class);
    }

    private abstract static class GenericHolder<T> {
    }

    public static final class ListArrayHolder extends GenericHolder<List<String>[]> {
    }

    public static class Account {
        private String id = "account-1";
        private boolean active = true;

        public boolean isActive() {
            return active;
        }
    }

    public static final class PremiumAccount extends Account {
        private String tier = "gold";
    }
}
