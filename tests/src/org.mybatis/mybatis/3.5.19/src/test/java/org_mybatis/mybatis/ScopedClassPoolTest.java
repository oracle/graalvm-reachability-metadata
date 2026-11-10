/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.javassist.ClassPool;
import org.apache.ibatis.javassist.CtClass;
import org.apache.ibatis.javassist.scopedpool.ScopedClassPool;
import org.apache.ibatis.javassist.scopedpool.ScopedClassPoolRepository;
import org.apache.ibatis.javassist.scopedpool.ScopedClassPoolRepositoryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ScopedClassPoolTest {
    @Test
    void inspectsAndCachesExistingClassDefinitions() throws Exception {
        ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
        ScopedClassPool pool = repository.createScopedClassPool(getClass().getClassLoader(), new ClassPool(false));
        try {
            CtClass type = pool.get("org.apache.ibatis.session.RowBounds");
            assertEquals("org.apache.ibatis.session.RowBounds", type.getName());
            assertEquals("int", type.getDeclaredMethod("getLimit").getReturnType().getName());
            assertSame(type, pool.get("org.apache.ibatis.session.RowBounds"));
            pool.flushClass(type.getName());
            assertEquals("org.apache.ibatis.session.RowBounds",
                    pool.get("org.apache.ibatis.session.RowBounds").getName());
        } finally {
            pool.close();
        }
    }
}
