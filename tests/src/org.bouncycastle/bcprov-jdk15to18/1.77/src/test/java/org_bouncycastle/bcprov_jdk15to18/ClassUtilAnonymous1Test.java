/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.bouncycastle.jcajce.provider.symmetric.util.ClassUtil;
import org.junit.jupiter.api.Test;

public class ClassUtilAnonymous1Test {
    private static final String BOUNCY_CASTLE_TARGET_CLASS =
        "org.bouncycastle.crypto.prng.ReversedWindowGenerator";
    private static final String JDK_TARGET_CLASS = "javax.crypto.spec.GCMParameterSpec";

    @Test
    void loadBouncyCastleClassUsesSystemClassLoaderWhenSourceClassUsesBootstrapLoader() {
        Class<?> loadedClass = ClassUtil.loadClass(Object.class, BOUNCY_CASTLE_TARGET_CLASS);

        assertNotNull(loadedClass);
        assertEquals(BOUNCY_CASTLE_TARGET_CLASS, loadedClass.getName());
    }

    @Test
    void loadJdkClassUsesSystemClassLoaderWhenSourceClassUsesBootstrapLoader() {
        Class<?> loadedClass = ClassUtil.loadClass(Object.class, JDK_TARGET_CLASS);

        assertNotNull(loadedClass);
        assertEquals(JDK_TARGET_CLASS, loadedClass.getName());
    }
}
