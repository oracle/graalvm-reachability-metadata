/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.reflect.Classes;
import org.junit.jupiter.api.Test;

public class ClassesTest {
    @Test
    public void loadsClassesAndFindsDeclaredMethods() {
        Class<?> locationClass = Classes.load(Location.class.getName());
        Method method = Classes.getDeclaredMethod(Location.class, "getLineNumber");

        assertThat(locationClass).isEqualTo(Location.class);
        assertThat(method.getName()).isEqualTo("getLineNumber");
        assertThat(method.getParameterTypes()).isEmpty();
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
