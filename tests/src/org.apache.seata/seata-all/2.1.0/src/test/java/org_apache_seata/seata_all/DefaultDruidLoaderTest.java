/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;

import com.alibaba.druid.util.StringUtils;
import org.apache.seata.sqlparser.druid.DruidDelegatingDbTypeParser;
import org.apache.seata.sqlparser.util.DbTypeParser;
import org.apache.seata.sqlparser.util.JdbcConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultDruidLoaderTest {
    private static final String DRUID_LOCATION = "lib/sqlparser/druid.jar";
    private static final String DRUID_DELEGATING_DB_TYPE_PARSER =
            "org.apache.seata.sqlparser.druid.DruidDelegatingDbTypeParser";
    private static final String DRUID_STRING_UTILS = "com.alibaba.druid.util.StringUtils";

    @Test
    void constructorFallsBackToTheDruidCodeSourceWhenTheEmbeddedJarResourceIsHidden() throws Exception {
        URL seataJarUrl = DruidDelegatingDbTypeParser.class.getProtectionDomain().getCodeSource().getLocation();
        URL druidJarUrl = StringUtils.class.getProtectionDomain().getCodeSource().getLocation();

        try (FilteringDruidResourceClassLoader classLoader = new FilteringDruidResourceClassLoader(
                new URL[] {seataJarUrl, druidJarUrl},
                DbTypeParser.class.getClassLoader())) {
            DbTypeParser parser = Class.forName(DRUID_DELEGATING_DB_TYPE_PARSER, true, classLoader)
                    .asSubclass(DbTypeParser.class)
                    .getDeclaredConstructor()
                    .newInstance();

            String dbType = parser.parseFromJdbcUrl("jdbc:mysql://localhost:3306/seata");

            assertThat(dbType).isEqualTo(JdbcConstants.MYSQL);
            assertThat(classLoader.getRequestedResourceNames()).contains(DRUID_LOCATION);
            assertThat(classLoader.getChildLoadedClassNames()).contains(DRUID_STRING_UTILS);
        }
    }

    private static final class FilteringDruidResourceClassLoader extends URLClassLoader {
        private final Set<String> requestedResourceNames = new LinkedHashSet<>();
        private final Set<String> childLoadedClassNames = new LinkedHashSet<>();

        private FilteringDruidResourceClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public URL getResource(String name) {
            requestedResourceNames.add(name);
            if (DRUID_LOCATION.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!shouldLoadInChild(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                        childLoadedClassNames.add(name);
                    } catch (ClassNotFoundException ignored) {
                        return super.loadClass(name, resolve);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private boolean shouldLoadInChild(String name) {
            return name.startsWith("org.apache.seata.sqlparser.druid.")
                    || name.startsWith("com.alibaba.druid.");
        }

        private Set<String> getRequestedResourceNames() {
            return requestedResourceNames;
        }

        private Set<String> getChildLoadedClassNames() {
            return childLoadedClassNames;
        }
    }
}
