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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.ReflectionUtils;

public class ReflectionUtilsTest {

    @Test
    void findsDeclaredFieldOnSpringDataType() {
        Field field = ReflectionUtils.findField(PageRequest.class, candidate -> "sort".equals(candidate.getName()));

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("sort");
        assertThat(field.getType()).isEqualTo(Sort.class);
    }

    @Test
    void findsConstructorMatchingRuntimeArguments() {
        Sort sort = Sort.by("lastname").ascending();

        Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor(PageRequest.class, 0, 20, sort);

        assertThat(constructor).hasValueSatisfying(candidate -> {
            assertThat(candidate.getParameterCount()).isEqualTo(3);
            assertThat(candidate.getParameterTypes()).containsExactly(int.class, int.class, Sort.class);
        });
    }

    @Test
    void findsRequiredMethodDeclaredOnInterface() {
        Method method = ReflectionUtils.findRequiredMethod(Pageable.class, "getPageNumber");

        assertThat(method.getName()).isEqualTo("getPageNumber");
        assertThat(method.getParameterCount()).isZero();
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
