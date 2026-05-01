/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.util.introspection.ClassMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClassMapInnerMethodInfoTest {
    @Test
    void upcastsMethodFromNonPublicImplementationToPublicInterface() throws Exception {
        Map<String, String> values = new HashMap<>();
        values.put("template", "merged");
        Map<String, String> nonPublicMap = Collections.unmodifiableMap(values);
        ClassMap classMap = new ClassMap(nonPublicMap.getClass());

        Method method = classMap.findMethod("get", new Object[] {"template" });

        assertNotNull(method);
        assertEquals("get", method.getName());
        assertEquals(Map.class, method.getDeclaringClass());
    }
}
