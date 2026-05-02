/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.JList;
import javax.swing.ListModel;

import bsh.classpath.BshClassPath;
import bsh.classpath.ClassManagerImpl;
import bsh.util.ClassBrowser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassBrowserTest {
    private static final String TEST_PACKAGE = ClassBrowserTest.class.getPackage().getName();
    private static final String TEST_CLASS = ClassBrowserTest.class.getName();

    public String browsedField = "visible";

    public ClassBrowserTest() {
    }

    @Test
    void displaysDeclaredPublicConstructorsMethodsAndFieldsForSelectedClass() throws Exception {
        ClassBrowser browser = new ClassBrowser(new FixedClassManager());

        browser.init();
        browser.driveToClass(TEST_CLASS);

        assertThat(listValues(browser))
                .contains(
                        "ClassBrowserTest",
                        "org_apache_extras_beanshell.bsh.ClassBrowserTest()",
                        "browsedMethod( java.lang.String )",
                        "browsedField");
    }

    public String browsedMethod(String prefix) {
        return prefix + browsedField;
    }

    private static List<String> listValues(Container container) {
        List<String> values = new ArrayList<>();
        collectListValues(container, values);
        return values;
    }

    private static void collectListValues(Component component, List<String> values) {
        if (component instanceof JList<?>) {
            ListModel<?> model = ((JList<?>) component).getModel();
            for (int index = 0; index < model.getSize(); index++) {
                values.add(String.valueOf(model.getElementAt(index)));
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectListValues(child, values);
            }
        }
    }

    private static final class FixedClassManager extends ClassManagerImpl {
        private final BshClassPath classPath = new FixedClassPath();

        @Override
        public BshClassPath getClassPath() {
            return classPath;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Class classForName(String name) {
            if (TEST_CLASS.equals(name)) {
                return ClassBrowserTest.class;
            }
            return null;
        }
    }

    private static final class FixedClassPath extends BshClassPath {
        private FixedClassPath() {
            super("ClassBrowser test class path");
        }

        @Override
        public Set<String> getPackagesSet() {
            return Collections.singleton(TEST_PACKAGE);
        }

        @Override
        public Set<String> getClassesForPackage(String pack) {
            if (TEST_PACKAGE.equals(pack)) {
                return Collections.singleton(TEST_CLASS);
            }
            return Collections.emptySet();
        }
    }
}
