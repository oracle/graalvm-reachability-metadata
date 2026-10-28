/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.scopedpool.ScopedClassPool;
import javassist.scopedpool.ScopedClassPoolRepository;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ScopedClassPoolTest {
    @Test
    void resolvesLibraryResourcesAndMaintainsItsLocalCache() throws Exception {
        boolean pruning = ClassPool.doPruning;
        boolean release = ClassPool.releaseUnmodifiedClassFile;
        ScopedClassPool pool = null;
        try {
            ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
            ClassLoader loader = ClassPool.class.getClassLoader();
            pool = repository.createScopedClassPool(loader, TestSupport.classPool());
            String name = "javassist.bytecode.ClassFile";
            CtClass first = pool.get(name);
            assertSame(loader, pool.getClassLoader());
            assertEquals("()Ljava/lang/String;", first.getDeclaredMethod("getName").getSignature());
            assertSame(first, pool.get(name));
            pool.flushClass(name);
            CtClass second = pool.get(name);
            assertNotSame(first, second);
            assertEquals(name, second.getName());
            pool.lockInCache(second);
            assertSame(second, pool.getLocally(name));
            pool.soften(second);
            assertSame(second, pool.get(name));
        } finally {
            if (pool != null) {
                pool.close();
            }
            ClassPool.doPruning = pruning;
            ClassPool.releaseUnmodifiedClassFile = release;
        }
    }
}
