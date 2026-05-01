/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.scopedpool.ScopedClassPool;
import javassist.scopedpool.ScopedClassPoolRepository;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScopedClassPoolTest {
    @Test
    void getLooksForLocalClassResourceThroughScopedClassLoader() throws Exception {
        ScopedClassPool classPool = newScopedClassPool();
        try {
            CtClass testClass = classPool.get(ScopedClassPoolTest.class.getName());

            assertThat(testClass.getName()).isEqualTo(ScopedClassPoolTest.class.getName());
        } finally {
            classPool.close();
        }
    }

    private static ScopedClassPool newScopedClassPool() {
        ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
        ClassLoader classLoader = ScopedClassPoolTest.class.getClassLoader();
        return repository.createScopedClassPool(classLoader, new ClassPool(true));
    }
}
