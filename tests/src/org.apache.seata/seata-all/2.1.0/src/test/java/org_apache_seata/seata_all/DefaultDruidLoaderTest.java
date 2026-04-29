/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;

import org.apache.seata.common.DefaultValues;
import org.apache.seata.sqlparser.druid.DruidDelegatingDbTypeParser;
import org.apache.seata.sqlparser.util.DbTypeParser;
import org.junit.jupiter.api.Test;

public class DefaultDruidLoaderTest {
    private static final String DRUID_LOADER_EXERCISE =
            "org_apache_seata.seata_all.DefaultDruidLoaderTest$DruidLoaderExercise";

    @Test
    void fallsBackToDruidClassCodeSourceWhenEmbeddedDruidResourceIsUnavailable() throws Exception {
        Path druidJar = copyEmbeddedDruidJar();
        URL testClassesUrl = DefaultDruidLoaderTest.class.getProtectionDomain().getCodeSource().getLocation();
        URL seataAllUrl = DruidDelegatingDbTypeParser.class.getProtectionDomain().getCodeSource().getLocation();
        URL[] urls = new URL[] {testClassesUrl, seataAllUrl, druidJar.toUri().toURL()};

        try (HidingDruidResourceClassLoader classLoader = new HidingDruidResourceClassLoader(urls)) {
            BooleanSupplier exercise = loadDruidLoaderExercise(classLoader);

            assertThat(exercise.getAsBoolean()).isTrue();
        }
    }

    private static BooleanSupplier loadDruidLoaderExercise(ClassLoader classLoader) {
        ServiceLoader<BooleanSupplier> exercises = ServiceLoader.load(BooleanSupplier.class, classLoader);
        for (BooleanSupplier exercise : exercises) {
            if (exercise.getClass().getName().equals(DRUID_LOADER_EXERCISE)) {
                return exercise;
            }
        }
        throw new IllegalStateException("Druid loader exercise service was not found");
    }

    private static Path copyEmbeddedDruidJar() throws IOException {
        Path druidJar = Files.createTempFile("seata-druid", ".jar");
        try (InputStream input = DefaultDruidLoaderTest.class.getClassLoader()
                .getResourceAsStream(DefaultValues.DRUID_LOCATION)) {
            assertThat(input).as("embedded Druid jar resource").isNotNull();
            Files.copy(input, druidJar, REPLACE_EXISTING);
        }
        druidJar.toFile().deleteOnExit();
        return druidJar;
    }

    public static final class DruidLoaderExercise implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            DbTypeParser parser = new DruidDelegatingDbTypeParser();
            return "mysql".equals(parser.parseFromJdbcUrl("jdbc:mysql://localhost:3306/seata"));
        }
    }

    private static final class HidingDruidResourceClassLoader extends URLClassLoader {
        private HidingDruidResourceClassLoader(URL[] urls) {
            super(urls, DefaultDruidLoaderTest.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            if (DefaultValues.DRUID_LOCATION.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (DefaultValues.DRUID_LOCATION.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isIsolated(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isIsolated(String name) {
            return name.startsWith("org.apache.seata.sqlparser.druid.")
                    || name.startsWith("com.alibaba.druid.")
                    || name.startsWith("org_apache_seata.seata_all.DefaultDruidLoaderTest");
        }
    }
}
