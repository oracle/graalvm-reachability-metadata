/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.autobean.vm.impl.TypeUtils;

import java.lang.reflect.GenericArrayType;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TypeUtilsTest {
    @Test
    void ensureBaseTypeCreatesArrayClassForGenericArrayType() {
        GenericArrayType listArrayType = () -> List.class;

        Class<?> arrayType = TypeUtils.ensureBaseType(listArrayType);

        assertThat(arrayType).isEqualTo(List[].class);
        assertThat(arrayType.getComponentType()).isEqualTo(List.class);
    }
}
