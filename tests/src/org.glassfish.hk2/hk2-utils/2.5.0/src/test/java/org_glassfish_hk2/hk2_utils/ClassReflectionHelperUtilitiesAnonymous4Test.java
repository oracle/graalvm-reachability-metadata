/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import org.glassfish.hk2.utilities.reflection.ClassReflectionHelper;
import org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReflectionHelperUtilitiesAnonymous4Test {
    private final ClassReflectionHelper helper = new ClassReflectionHelperImpl();

    @Test
    public void findsDeclaredFieldsAcrossConcreteClassHierarchy() {
        final Set<String> fields = helper.getAllFields(FieldChildService.class)
                .stream()
                .map(ClassReflectionHelperUtilitiesAnonymous4Test::qualifiedFieldName)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "FieldParentService#parentId",
                "FieldChildService#childName"
        );
    }

    private static String qualifiedFieldName(final Field field) {
        return field.getDeclaringClass().getSimpleName() + "#" + field.getName();
    }

    private static class FieldParentService {
        private long parentId;
    }

    private static final class FieldChildService extends FieldParentService {
        private String childName;
    }
}
