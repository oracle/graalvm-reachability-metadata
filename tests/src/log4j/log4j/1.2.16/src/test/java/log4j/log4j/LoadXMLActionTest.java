/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadXMLActionTest {

    @Test
    void initializingLoadXmlActionInFreshClassLoaderResolvesItsOwnClassLiteral() throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(isolatedClassPath(), ClassLoader.getPlatformClassLoader())) {
            Class<?> loadXmlActionClass = Class.forName("org.apache.log4j.chainsaw.LoadXMLAction", true, isolatedLoader);

            assertThat(loadXmlActionClass.getName()).isEqualTo("org.apache.log4j.chainsaw.LoadXMLAction");
            assertThat(loadXmlActionClass.getClassLoader()).isSameAs(isolatedLoader);
        }
    }

    private static URL[] isolatedClassPath() {
        URL testClassesUrl = codeSourceUrl(LoadXMLActionTest.class);
        URL libraryClassesUrl = codeSourceUrl(Logger.class);
        if (testClassesUrl.equals(libraryClassesUrl)) {
            return new URL[] { testClassesUrl };
        }
        return new URL[] { testClassesUrl, libraryClassesUrl };
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }
}
