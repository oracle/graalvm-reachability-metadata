/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.HandlesTypes;
import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextConfigTest {

    private static final String SCI_RESOURCE = "META-INF/services/" + ServletContainerInitializer.class.getName();

    @Test
    void scansSuperclassResourcesForHandlesTypes(@TempDir Path temporaryDirectory) throws Exception {
        Path serviceFile = temporaryDirectory.resolve(SCI_RESOURCE);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, TestInitializer.class.getName() + System.lineSeparator(),
                StandardCharsets.UTF_8);

        ResourceClassLoader classLoader = new ResourceClassLoader(
                getClass().getClassLoader(), serviceFile.toUri().toURL());
        StandardContext context = new StandardContext();
        context.setParentClassLoader(classLoader);
        context.setLoader(new FixedLoader(classLoader));

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("base").toString());
        Path docBase = Files.createDirectories(temporaryDirectory.resolve("webapp"));
        context.setName("/context-config");
        context.setPath("/context-config");
        context.setDocBase(docBase.toAbsolutePath().toString());
        tomcat.getHost().addChild(context);

        ExposedContextConfig config = new ExposedContextConfig();
        config.attach(context);
        try (InputStream classBytes = classLoader.getResourceAsStream(
                "org/apache/catalina/core/StandardContext.class")) {
            assertThat(classBytes).isNotNull();
            config.loadInitializers();
            config.inspect(new ClassParser(classBytes).parse());
            assertThat(config.matchedClassCount()).isPositive();
        } finally {
            tomcat.destroy();
        }
    }

    @HandlesTypes(Object.class)
    public static final class TestInitializer implements ServletContainerInitializer {

        @Override
        public void onStartup(Set<Class<?>> classes, ServletContext context) {
        }
    }

    private static final class ExposedContextConfig extends ContextConfig {

        private void attach(Context context) {
            this.context = context;
        }

        private void loadInitializers() {
            processServletContainerInitializers();
        }

        private void inspect(JavaClass javaClass) {
            checkHandlesTypes(javaClass, new HashMap<>());
        }

        private int matchedClassCount() {
            return initializerClassMap.values().stream().mapToInt(Set::size).sum();
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {

        private final URL serviceFile;

        private ResourceClassLoader(ClassLoader parent, URL serviceFile) {
            super(parent);
            this.serviceFile = serviceFile;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SCI_RESOURCE.equals(name)) {
                return Collections.enumeration(List.of(serviceFile));
            }
            return super.getResources(name);
        }
    }

    private static final class FixedLoader implements Loader {

        private final ClassLoader classLoader;
        private Context context;

        private FixedLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void backgroundProcess() {
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void setContext(Context context) {
            this.context = context;
        }

        @Override
        public boolean getDelegate() {
            return true;
        }

        @Override
        public void setDelegate(boolean delegate) {
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public boolean modified() {
            return false;
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }
}
