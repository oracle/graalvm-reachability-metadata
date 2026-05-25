/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.lang.reflect.Method;

import org.codehaus.plexus.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsPublicMethodsFromNonPublicClassToPublicInterfaceMethods() throws Exception {
        ClassMap classMap = new ClassMap(HiddenWorker.class);

        Method method = classMap.findMethod("work", new Object[] {"input"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(Worker.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface Worker {
        String work(String value);
    }

    private static class HiddenWorker implements Worker {
        public String work(String value) {
            return "processed " + value;
        }
    }
}
