/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.glassfish.hk2.utilities.reflection.ClassReflectionHelper;
import org.glassfish.hk2.utilities.reflection.MethodWrapper;
import org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassReflectionHelperUtilitiesTest {
    private final ClassReflectionHelper helper = new ClassReflectionHelperImpl();

    @Test
    public void findsMethodsDeclaredOnInterfaceHierarchy() {
        final Set<String> methodNames = helper.getAllMethods(ClassReflectionHelperUtilitiesChildContract.class)
                .stream()
                .map(MethodWrapper::getMethod)
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames).containsExactlyInAnyOrder("parentOperation", "childOperation");
    }
}

interface ClassReflectionHelperUtilitiesParentContract {
    String parentOperation();
}

interface ClassReflectionHelperUtilitiesChildContract extends ClassReflectionHelperUtilitiesParentContract {
    String childOperation();
}
