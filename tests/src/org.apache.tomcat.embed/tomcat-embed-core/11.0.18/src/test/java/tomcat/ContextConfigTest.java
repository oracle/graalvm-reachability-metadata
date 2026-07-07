/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HandlesTypes;
import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Valve;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextConfigTest {

    private static final AtomicReference<Set<Class<?>>> STARTUP_CLASSES = new AtomicReference<>();

    @Test
    void startsWebappWithAuthenticatorAndHandlesTypesScanning(@TempDir Path temporaryDirectory) throws Exception {
        Path webappDirectory = temporaryDirectory.resolve("webapp");
        prepareWebappClasses(webappDirectory);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("base").toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);
        Connector connector = new Connector();
        connector.setPort(0);
        tomcat.setConnector(connector);

        try {
            Context context = tomcat.addWebapp("", webappDirectory.toAbsolutePath().toString());
            ((StandardContext) context).setDelegate(true);
            context.setRealm(new NullRealm());
            context.setLoginConfig(new LoginConfig("BASIC", null, null, null));

            STARTUP_CLASSES.set(null);
            tomcat.start();

            assertThat(context.getPipeline().getValves()).anyMatch(ContextConfigTest::isBasicAuthenticator);
            assertThat(STARTUP_CLASSES.get()).contains(MatchingServlet.class);
        } finally {
            stopAndDestroy(tomcat);
        }
    }

    private static void prepareWebappClasses(Path webappDirectory) throws IOException {
        Path classesDirectory = webappDirectory.resolve("WEB-INF/classes");
        Files.createDirectories(classesDirectory.resolve("META-INF/services"));
        Files.writeString(classesDirectory.resolve("META-INF/services/" + ServletContainerInitializer.class.getName()),
                HandlesTypesInitializer.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);
        copyClassResource(MatchingServlet.class, classesDirectory);
    }

    private static void copyClassResource(Class<?> type, Path classesDirectory) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        Path target = classesDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, target);
        }
    }

    private static boolean isBasicAuthenticator(Valve valve) {
        return valve instanceof BasicAuthenticator;
    }

    private static void stopAndDestroy(Tomcat tomcat) throws LifecycleException {
        try {
            if (tomcat.getServer() != null && tomcat.getServer().getState().isAvailable()) {
                tomcat.stop();
            }
        } finally {
            if (tomcat.getServer() != null && tomcat.getServer().getState() != LifecycleState.DESTROYED) {
                tomcat.destroy();
            }
        }
    }

    public interface TrackedServlet {
    }

    public static class MatchingServlet extends HttpServlet implements TrackedServlet {
    }

    @HandlesTypes(TrackedServlet.class)
    public static class HandlesTypesInitializer implements ServletContainerInitializer {

        @Override
        public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
            STARTUP_CLASSES.set(classes);
        }
    }
}
