/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.Map;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.utility.JavaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ByteArrayClassLoaderTest {
    @Test
    void reusesParentClassWhenExistingTypesAreAllowed() throws Exception {
        TypeDescription type = TypeDescription.ForLoadedType.of(JavaType.class);
        byte[] bytes = ClassFileLocator.ForClassLoader.read(JavaType.class).resolve();
        Map<TypeDescription, Class<?>> loaded = ByteArrayClassLoader.load(getClass().getClassLoader(), Map.of(type, bytes));
        assertEquals(1, loaded.size());
        assertSame(JavaType.class, loaded.get(type));
        assertEquals(JavaType.METHOD_TYPE, Enum.valueOf(loaded.get(type).asSubclass(JavaType.class), "METHOD_TYPE"));
    }
}
