/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.javassist.Loader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoaderTest {
    @Test
    void reportsAnUnavailableApplicationClass() {
        Loader loader = new Loader(getClass().getClassLoader(), null);
        assertThrows(ClassNotFoundException.class,
                () -> loader.loadClass("org_mybatis.mybatis.UnavailableApplication"));
    }

    @Test
    void invokesAParentDelegatedApplicationEntryPoint() throws Throwable {
        Loader loader = new Loader(getClass().getClassLoader(), null);
        loader.delegateLoadingOf(Application.class.getName());
        try {
            loader.run(new String[]{Application.class.getName(), "Ada", "Grace"});
            assertEquals(List.of("Ada", "Grace"), Application.names);
        } finally {
            Application.names = List.of();
        }
    }

    @Test
    void invokesASystemApplicationWithoutAParentLoader() throws Throwable {
        Loader loader = new Loader(null, null);
        loader.delegateLoadingOf(Application.class.getName());
        try {
            loader.run(Application.class.getName(), new String[]{"Lin", "Margaret"});
            assertEquals(List.of("Lin", "Margaret"), Application.names);
        } finally {
            Application.names = List.of();
        }
    }

    public static class Application {
        private static List<String> names = List.of();

        public static void main(String[] args) {
            names = List.of(args);
        }
    }
}
