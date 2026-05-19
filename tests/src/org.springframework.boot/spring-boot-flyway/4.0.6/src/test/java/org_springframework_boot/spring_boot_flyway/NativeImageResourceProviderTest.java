/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_flyway;

import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeImageResourceProviderTest {

    static {
        System.setProperty("org.graalvm.nativeimage.imagecode", "runtime");
    }

    @Test
    void getResourceChecksClassLoaderWhenScannerCannotFindResource() throws Exception {
        ResourceProvider resourceProvider = createNativeImageResourceProvider();

        LoadableResource resource = resourceProvider.getResource("db/migration/V999__missing.sql");

        assertThat(resource).isNull();
    }

    private ResourceProvider createNativeImageResourceProvider() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        FluentConfiguration configuration = Flyway.configure(classLoader);
        configuration.locations("classpath:db/migration");
        Scanner<JavaMigration> scanner = new Scanner<>(JavaMigration.class, configuration, configuration.getLocations());
        Constructor<?> constructor = Class
                .forName("org.springframework.boot.flyway.autoconfigure.NativeImageResourceProvider")
                .getDeclaredConstructor(Scanner.class, ClassLoader.class, Collection.class, Charset.class,
                        boolean.class);
        constructor.setAccessible(true);
        return (ResourceProvider) constructor.newInstance(scanner, classLoader,
                List.of(new Location("classpath:db/migration")), StandardCharsets.UTF_8, false);
    }

}
