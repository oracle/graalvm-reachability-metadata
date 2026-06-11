/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jandex;

import java.lang.reflect.Method;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrongInternPoolInnerIdentityHashSetIteratorTest {
    @Test
    void iteratorRemoveRelocatesEntriesFromWrappedCluster() throws Exception {
        Object pool = newPool();
        expandTable(pool);
        clear(pool);

        intern(pool, "n");
        intern(pool, "ao");
        intern(pool, "bp");

        Iterator<?> iterator = iterator(pool);
        assertThat(iterator.next()).isEqualTo("bp");
        assertThat(iterator.next()).isEqualTo("n");
        assertThat(iterator.next()).isEqualTo("ao");

        iterator.remove();

        assertThat(iterator.hasNext()).isFalse();
        assertThat(size(pool)).isEqualTo(2);
        assertThat(contains(pool, "n")).isTrue();
        assertThat(contains(pool, "bp")).isTrue();
        assertThat(contains(pool, "ao")).isFalse();
    }

    private static Object newPool() throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("forStrings");
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static void expandTable(Object pool) throws Exception {
        intern(pool, "alpha");
        intern(pool, "bravo");
        intern(pool, "charlie");
        intern(pool, "delta");
        intern(pool, "echo");
    }

    private static Object intern(Object pool, String entry) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("intern", Object.class);
        method.setAccessible(true);
        return method.invoke(pool, entry);
    }

    private static void clear(Object pool) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("clear");
        method.setAccessible(true);
        method.invoke(pool);
    }

    private static Iterator<?> iterator(Object pool) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("iterator");
        method.setAccessible(true);
        return (Iterator<?>) method.invoke(pool);
    }

    private static int size(Object pool) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("size");
        method.setAccessible(true);
        return (Integer) method.invoke(pool);
    }

    private static boolean contains(Object pool, String entry) throws Exception {
        Method method = strongInternPoolClass().getDeclaredMethod("contains", Object.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(pool, entry);
    }

    private static Class<?> strongInternPoolClass() throws ClassNotFoundException {
        return Class.forName("org.jboss.jandex.StrongInternPool");
    }
}
