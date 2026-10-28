/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package p6spy.p6spy;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.common.P6Util;
import com.p6spy.engine.spy.P6SpyDriver;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class P6UtilTest {
    @Test
    void locatesP6SpyServiceOnClassPath() {
        URL resource = P6Util.locateOnClassPath("META-INF/services/java.sql.Driver");

        assertThat(resource).isNotNull();
    }

    @Test
    void fallsBackFromContextLoaderForResourcesAndClasses() throws ClassNotFoundException {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        ClassLoader emptyLoader =
                new ClassLoader(null) {
                    @Override
                    public URL getResource(String name) {
                        return null;
                    }

                    @Override
                    protected Class<?> loadClass(String name, boolean resolve)
                            throws ClassNotFoundException {
                        throw new ClassNotFoundException(name);
                    }
                };

        try {
            thread.setContextClassLoader(emptyLoader);

            assertThat(P6Util.locateOnClassPath("META-INF/services/java.sql.Driver")).isNotNull();
            assertThat(P6Util.forName(P6SpyDriver.class.getName())).isEqualTo(P6SpyDriver.class);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }
}
