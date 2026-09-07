/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void resolvesClassesAndPublicInterfaceMethods() throws Exception {
        assertThat(
            Modifier.isPublic(ClassUtils.getPublicMethod(HiddenList.class, "size").getDeclaringClass().getModifiers())
        )
            .isTrue();
    }

    private static class HiddenList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public int size() {
            return super.size();
        }
    }
}
