/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.javassist.tools.web.Viewer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ViewerTest {
    @Test
    void dispatchesAnApplicationEntryPointUsingParentDelegation() throws Throwable {
        Viewer viewer = new ClasspathViewer();
        try {
            viewer.run(Application.class.getName(), new String[]{"Ada", "Grace"});
            assertEquals(List.of("Ada", "Grace"), Application.names);
            viewer.run(Application.class.getName(), new String[]{"Lin"});
            assertEquals(List.of("Lin"), Application.names);
            assertSame(String.class, viewer.loadClass("java.lang.String"));
            assertSame(Viewer.class, viewer.loadClass(Viewer.class.getName()));
        } finally {
            Application.names = List.of();
        }
    }

    public static class ClasspathViewer extends Viewer {
        public ClasspathViewer() {
            super("localhost", 0);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            // Delegate the application to its existing loader instead of fetching bytecode.
            if (Application.class.getName().equals(name)) {
                return Application.class.getClassLoader().loadClass(name);
            }
            return super.findClass(name);
        }
    }

    public static class Application {
        private static List<String> names = List.of();

        public static void main(String[] args) {
            names = List.of(args);
        }
    }
}
