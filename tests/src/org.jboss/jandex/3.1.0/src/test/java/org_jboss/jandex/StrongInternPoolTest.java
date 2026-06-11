/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jandex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrongInternPoolTest {
    @Test
    void serializesAndDeserializesInternedEntries() throws Exception {
        Object pool = newPool();

        String alpha = new String("alpha");
        String beta = new String("beta");

        assertThat(intern(pool, alpha)).isSameAs(alpha);
        assertThat(intern(pool, new String("alpha"))).isSameAs(alpha);
        assertThat(intern(pool, beta)).isSameAs(beta);
        assertThat(size(pool)).isEqualTo(2);

        Object restoredPool = deserialize(serialize(pool));

        assertThat(size(restoredPool)).isEqualTo(2);
        assertThat(contains(restoredPool, "alpha")).isTrue();
        assertThat(contains(restoredPool, "beta")).isTrue();

        String duplicateAlpha = new String("alpha");
        Object restoredAlpha = intern(restoredPool, duplicateAlpha);
        assertThat(restoredAlpha).isEqualTo("alpha");
        assertThat(restoredAlpha).isNotSameAs(duplicateAlpha);
        assertThat(size(restoredPool)).isEqualTo(2);
    }

    private static Object newPool() throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("forStrings");
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object intern(Object pool, Object entry) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("intern", Object.class);
        method.setAccessible(true);
        return method.invoke(pool, entry);
    }

    private static int size(Object pool) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("size");
        method.setAccessible(true);
        return (Integer) method.invoke(pool);
    }

    private static boolean contains(Object pool, Object entry) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("contains", Object.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(pool, entry);
    }

    private static byte[] serialize(Object pool) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pool);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static Class<?> strongInternPoolClass() throws ClassNotFoundException {
        return Class.forName("org.jboss.jandex.StrongInternPool");
    }
}
