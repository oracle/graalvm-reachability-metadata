/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.DefiningClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefiningClassLoaderTest {
    @Test
    void loadClassFallsBackToSystemClassLookupWhenParentLoaderIsAbsent() throws Exception {
        try {
            DefiningClassLoader loader = new DefiningClassLoader(null);

            Class<?> loadedClass = loader.loadClass("java.lang.String", false);

            assertThat(loadedClass).isSameAs(String.class);
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
