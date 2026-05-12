/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.data.util.ReflectionUtils.DescribedFieldFilter;

public class ReflectionUtilsTest {

    @Test
    void findsMatchingDeclaredFieldUsingDescribedFilter() {
        Field field = ReflectionUtils.findField(AbstractPageRequest.class,
                new ReflectionUtilsNamedFieldFilter("page"), true);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("page");
        assertThat(field.getType()).isEqualTo(int.class);
    }

    @Test
    void findsMatchingDeclaredConstructorFromRuntimeArguments() {
        Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor(AbstractPageRequest.class, 0, 20);

        assertThat(constructor).isPresent();
        assertThat(constructor.get().getParameterTypes()).containsExactly(int.class, int.class);
    }

    @Test
    void findsRequiredInterfaceMethodThroughPublicMethodLookup() {
        Method method = ReflectionUtils.findRequiredMethod(Pageable.class, "getPageSize");

        assertThat(method.getDeclaringClass()).isEqualTo(Pageable.class);
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}

final class ReflectionUtilsNamedFieldFilter implements DescribedFieldFilter {

    private final String fieldName;

    ReflectionUtilsNamedFieldFilter(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public boolean matches(Field field) {
        return fieldName.equals(field.getName());
    }

    @Override
    public String getDescription() {
        return "Field named " + fieldName;
    }
}
