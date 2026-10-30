/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.commons.lang.ClassUtils;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CommonsClassUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals(List.of(String.class, Integer.class), ClassUtils.convertClassNamesToClasses(List.of("java.lang.String", "java.lang.Integer")));
        assertEquals(String[].class, ClassUtils.getClass("java.lang.String[]"));
        assertEquals(int.class, ClassUtils.getClass("int"));
        assertEquals("substring", ClassUtils.getPublicMethod(String.class, "substring", new Class<?>[]{int.class}).getName());
        assertEquals("size", ClassUtils.getPublicMethod(Collections.singletonList("item").getClass(), "size", new Class<?>[0]).getName());
    }
}
