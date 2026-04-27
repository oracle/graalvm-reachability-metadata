/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.apache.log4j.LogManager;
import org.apache.log4j.or.ObjectRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RendererMapTest {

    @BeforeEach
    @AfterEach
    void resetConfiguration() {
        LogManager.resetConfiguration();
    }

    @Test
    void addsRendererByClassNameInFreshClassLoaderAndUsesIt() throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(isolatedClassPath(), ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previousContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(isolatedLoader);

            Class<?> logManagerClass = Class.forName("org.apache.log4j.LogManager", true, isolatedLoader);
            try {
                invokeStatic(logManagerClass, "resetConfiguration");

                Class<?> rendererMapClass = Class.forName("org.apache.log4j.or.RendererMap", true, isolatedLoader);
                Class<?> rendererSupportClass = Class.forName("org.apache.log4j.spi.RendererSupport", true, isolatedLoader);
                Class<?> isolatedMessageClass = Class.forName(RenderedMessage.class.getName(), true, isolatedLoader);

                Object repository = invokeStatic(logManagerClass, "getLoggerRepository");
                Method addRenderer = rendererMapClass.getMethod(
                        "addRenderer",
                        rendererSupportClass,
                        String.class,
                        String.class);
                addRenderer.invoke(null, repository, isolatedMessageClass.getName(), TrackingRenderer.class.getName());

                Object rendererMap = rendererSupportClass.getMethod("getRendererMap").invoke(repository);
                Object message = isolatedMessageClass.getConstructor(String.class).newInstance("payload");
                String rendered = (String) rendererMapClass.getMethod("findAndRender", Object.class).invoke(rendererMap, message);
                Object renderer = rendererMapClass.getMethod("get", Object.class).invoke(rendererMap, message);

                assertThat(rendered).isEqualTo("rendered:payload");
                assertThat(renderer.getClass().getName()).isEqualTo(TrackingRenderer.class.getName());
            } finally {
                thread.setContextClassLoader(previousContextClassLoader);
                invokeStatic(logManagerClass, "resetConfiguration");
            }
        }
    }

    private static Object invokeStatic(Class<?> type, String methodName) throws Exception {
        return type.getMethod(methodName).invoke(null);
    }

    private static URL[] isolatedClassPath() {
        URL testClassesUrl = codeSourceUrl(RendererMapTest.class);
        URL libraryClassesUrl = codeSourceUrl(org.apache.log4j.or.RendererMap.class);
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

    public static final class RenderedMessage {
        private final String value;

        public RenderedMessage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class TrackingRenderer implements ObjectRenderer {
        @Override
        public String doRender(Object object) {
            return "rendered:" + ((RenderedMessage) object).getValue();
        }
    }
}
