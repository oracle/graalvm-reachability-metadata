/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import java.net.URL;
import java.util.Base64;

import bsh.classpath.BshClassLoader;
import bsh.classpath.ClassManagerImpl;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BshClassLoaderTest {
    private static final String GENERATED_CLASS_NAME =
            "org_apache_extras_beanshell.bsh.BshClassLoaderGeneratedFixture";
    private static final String GENERATED_CLASS_BYTES = """
            yv66vgAAADQADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAI
            AQA+b3JnX2FwYWNoZV9leHRyYXNfYmVhbnNoZWxsL2JzaC9Cc2hDbGFzc0xvYWRlckdlbmVyYXRlZEZp
            eHR1cmUBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAKU291cmNlRmlsZQEAI0JzaENsYXNzTG9hZGVy
            R2VuZXJhdGVkRml4dHVyZS5qYXZhACEABwACAAAAAAABAAEABQAGAAEACQAAAB0AAQABAAAABSq3AAGx
            AAAAAQAKAAAABgABAAAAAwABAAsAAAACAAw=
            """;

    @Test
    void delegatesClassLookupToDesignatedReloadLoader() throws Exception {
        ClassManagerImpl classManager = new ClassManagerImpl();
        BshClassLoader classLoader = new BshClassLoader(classManager, new URL[0]);
        byte[] classBytes = Base64.getMimeDecoder().decode(GENERATED_CLASS_BYTES);

        try {
            Class<?> reloadedClass = classManager.defineClass(GENERATED_CLASS_NAME, classBytes);
            Class<?> loadedClass = classLoader.loadClass(GENERATED_CLASS_NAME, false);

            assertThat(loadedClass).isSameAs(reloadedClass);
            assertThat(loadedClass.getName()).isEqualTo(GENERATED_CLASS_NAME);
            assertThat(loadedClass.getClassLoader())
                    .isNotSameAs(BshClassLoaderTest.class.getClassLoader());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
