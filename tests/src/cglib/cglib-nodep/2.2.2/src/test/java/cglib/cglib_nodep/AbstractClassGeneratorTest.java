/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.cglib.core.NamingPolicy;
import net.sf.cglib.core.Predicate;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.NoOp;
import org.junit.jupiter.api.Test;

public class AbstractClassGeneratorTest {
    @Test
    void attemptLoadReusesClassResolvedByNamingPolicy() {
        RecordingClassLoader classLoader = new RecordingClassLoader(LoadableService.class.getClassLoader());
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(LoadableService.class);
        enhancer.setCallbackType(NoOp.class);
        enhancer.setClassLoader(classLoader);
        enhancer.setNamingPolicy(new FixedNamingPolicy(LoadableService.class.getName()));
        enhancer.setAttemptLoad(true);

        Class<?> reusableClass = enhancer.createClass();

        assertThat(reusableClass).isSameAs(LoadableService.class);
        assertThat(classLoader.loadedClassNames()).contains(LoadableService.class.getName());
    }

    public static class LoadableService {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    private static class FixedNamingPolicy implements NamingPolicy {
        private final String className;

        FixedNamingPolicy(String className) {
            this.className = className;
        }

        @Override
        public String getClassName(String prefix, String source, Object key, Predicate names) {
            return className;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FixedNamingPolicy)) {
                return false;
            }
            FixedNamingPolicy that = (FixedNamingPolicy) other;
            return className.equals(that.className);
        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<String>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name);
        }

        List<String> loadedClassNames() {
            return Collections.unmodifiableList(loadedClassNames);
        }
    }
}
