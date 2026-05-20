/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer.Scripts;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDependsOnDatabaseInitializationDetector;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Spring_boot_sqlTest {

    @Test
    void sqlInitializationPropertiesExposeDefaultsAndMutableOptions() {
        SqlInitializationProperties properties = new SqlInitializationProperties();

        assertThat(properties.getPlatform()).isEqualTo("all");
        assertThat(properties.getSeparator()).isEqualTo(";");
        assertThat(properties.getMode()).isEqualTo(DatabaseInitializationMode.EMBEDDED);
        assertThat(properties.isContinueOnError()).isFalse();
        assertThat(properties.getSchemaLocations()).isNull();
        assertThat(properties.getDataLocations()).isNull();

        properties.setSchemaLocations(List.of("classpath:/db/schema.sql"));
        properties.setDataLocations(List.of("classpath:/db/data.sql"));
        properties.setPlatform("postgresql");
        properties.setUsername("db-user");
        properties.setPassword("db-secret");
        properties.setContinueOnError(true);
        properties.setSeparator("@@");
        properties.setEncoding(StandardCharsets.UTF_16);
        properties.setMode(DatabaseInitializationMode.ALWAYS);

        assertThat(properties.getSchemaLocations()).containsExactly("classpath:/db/schema.sql");
        assertThat(properties.getDataLocations()).containsExactly("classpath:/db/data.sql");
        assertThat(properties.getPlatform()).isEqualTo("postgresql");
        assertThat(properties.getUsername()).isEqualTo("db-user");
        assertThat(properties.getPassword()).isEqualTo("db-secret");
        assertThat(properties.isContinueOnError()).isTrue();
        assertThat(properties.getSeparator()).isEqualTo("@@");
        assertThat(properties.getEncoding()).isEqualTo(StandardCharsets.UTF_16);
        assertThat(properties.getMode()).isEqualTo(DatabaseInitializationMode.ALWAYS);
        assertThat(DatabaseInitializationMode.valueOf("NEVER")).isEqualTo(DatabaseInitializationMode.NEVER);
    }

    @Test
    void applicationInitializerSettingsUsePlatformDefaultsWhenLocationsAreNotConfigured() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setPlatform("postgresql");
        properties.setContinueOnError(true);
        properties.setSeparator("//");
        properties.setEncoding(StandardCharsets.UTF_8);
        properties.setMode(DatabaseInitializationMode.ALWAYS);

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertThat(settings.getSchemaLocations()).containsExactly("optional:classpath*:schema-postgresql.sql",
            "optional:classpath*:schema.sql");
        assertThat(settings.getDataLocations()).containsExactly("optional:classpath*:data-postgresql.sql",
            "optional:classpath*:data.sql");
        assertThat(settings.isContinueOnError()).isTrue();
        assertThat(settings.getSeparator()).isEqualTo("//");
        assertThat(settings.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(settings.getMode()).isEqualTo(DatabaseInitializationMode.ALWAYS);
    }

    @Test
    void applicationInitializerSettingsPreferExplicitLocations() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setPlatform("ignored");
        properties.setSchemaLocations(List.of("classpath:/custom/schema-a.sql", "classpath:/custom/schema-b.sql"));
        properties.setDataLocations(List.of("classpath:/custom/data.sql"));

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertThat(settings.getSchemaLocations()).containsExactly("classpath:/custom/schema-a.sql",
            "classpath:/custom/schema-b.sql");
        assertThat(settings.getDataLocations()).containsExactly("classpath:/custom/data.sql");
    }

    @Test
    void scriptsContainerIsIterableAndFluentlyCarriesExecutionOptions() {
        Resource schema = resource("schema.sql", "create table orders(id int)");
        Resource data = resource("data.sql", "insert into orders values (1)");

        Scripts scripts = new Scripts(List.of(schema, data)).continueOnError(true)
            .separator("@@")
            .encoding(StandardCharsets.UTF_16);

        assertThat(scripts).containsExactly(schema, data);
        assertThat(scripts.isContinueOnError()).isTrue();
        assertThat(scripts.getSeparator()).isEqualTo("@@");
        assertThat(scripts.getEncoding()).isEqualTo(StandardCharsets.UTF_16);
    }

    @Test
    void scriptDatabaseInitializerResolvesOptionalSchemaAndDataLocationsAndPassesSettingsToRunner() {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("optional:missing-schema.sql", "schema.sql"));
        settings.setDataLocations(List.of("data.sql"));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setContinueOnError(true);
        settings.setSeparator("@@");
        settings.setEncoding(StandardCharsets.UTF_16);
        CapturingScriptDatabaseInitializer initializer = new CapturingScriptDatabaseInitializer(settings);
        initializer.setResourceLoader(new TestResourceLoader(Map.of("schema.sql", List.of(resource("schema.sql",
            "create table orders(id int)")), "data.sql", List.of(resource("data.sql",
                "insert into orders values (1)")))));

        boolean initialized = initializer.initializeDatabase();

        assertThat(initialized).isTrue();
        assertThat(initializer.runs).hasSize(2);
        assertThat(initializer.runs.get(0).contents()).containsExactly("create table orders(id int)");
        assertThat(initializer.runs.get(1).contents()).containsExactly("insert into orders values (1)");
        assertThat(initializer.runs).allSatisfy((run) -> {
            assertThat(run.continueOnError()).isTrue();
            assertThat(run.separator()).isEqualTo("@@");
            assertThat(run.encoding()).isEqualTo(StandardCharsets.UTF_16);
        });
    }

    @Test
    void scriptDatabaseInitializerRunsPatternResourcesInUrlOrder() {
        DatabaseInitializationSettings settings = scriptSettings(DatabaseInitializationMode.ALWAYS);
        settings.setSchemaLocations(List.of("classpath*:schema.sql"));
        CapturingScriptDatabaseInitializer initializer = new CapturingScriptDatabaseInitializer(settings);
        Resource later = resource("schema-later.sql", "create table later(id int)", "file:/schema-z.sql");
        Resource earlier = resource("schema-earlier.sql", "create table earlier(id int)", "file:/schema-a.sql");
        initializer.setResourceLoader(new TestResourceLoader(Map.of("classpath*:schema.sql", List.of(later,
            earlier))));

        assertThat(initializer.initializeDatabase()).isTrue();

        assertThat(initializer.runs).hasSize(1);
        assertThat(initializer.runs.get(0).contents()).containsExactly("create table earlier(id int)",
            "create table later(id int)");
    }

    @Test
    void scriptDatabaseInitializerHonorsNeverAndEmbeddedModes() {
        DatabaseInitializationSettings neverSettings = scriptSettings(DatabaseInitializationMode.NEVER);
        CapturingScriptDatabaseInitializer neverInitializer = new CapturingScriptDatabaseInitializer(neverSettings);
        neverInitializer.setResourceLoader(resourceLoaderWithSchema());

        assertThat(neverInitializer.initializeDatabase()).isFalse();
        assertThat(neverInitializer.runs).isEmpty();

        DatabaseInitializationSettings embeddedSettings = scriptSettings(DatabaseInitializationMode.EMBEDDED);
        CapturingScriptDatabaseInitializer embeddedInitializer = new CapturingScriptDatabaseInitializer(
            embeddedSettings);
        embeddedInitializer.embedded = true;
        embeddedInitializer.setResourceLoader(resourceLoaderWithSchema());

        assertThat(embeddedInitializer.initializeDatabase()).isTrue();
        assertThat(embeddedInitializer.runs).hasSize(1);
    }

    @Test
    void scriptDatabaseInitializerRejectsMissingRequiredLocationsAndUnknownEmbeddedDatabases() {
        DatabaseInitializationSettings missingSettings = scriptSettings(DatabaseInitializationMode.ALWAYS);
        missingSettings.setSchemaLocations(List.of("missing-schema.sql"));
        CapturingScriptDatabaseInitializer missingInitializer = new CapturingScriptDatabaseInitializer(missingSettings);
        missingInitializer.setResourceLoader(new TestResourceLoader(Map.of()));

        assertThatIllegalStateException().isThrownBy(missingInitializer::initializeDatabase)
            .withMessageContaining("No schema scripts found at location 'missing-schema.sql'");

        DatabaseInitializationSettings embeddedSettings = scriptSettings(DatabaseInitializationMode.EMBEDDED);
        ThrowingEmbeddedScriptDatabaseInitializer embeddedInitializer = new ThrowingEmbeddedScriptDatabaseInitializer(
            embeddedSettings);
        embeddedInitializer.setResourceLoader(resourceLoaderWithSchema());

        assertThatIllegalStateException().isThrownBy(embeddedInitializer::initializeDatabase)
            .withMessageContaining("Database initialization mode is 'EMBEDDED' and database type is unknown");
    }

    @Test
    void beanTypeDetectorsFindInitializersAndDatabaseDependentBeans() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("schemaInitializer", TestDatabaseInitializer.class);
            context.registerBean("reportingService", ReportingService.class);
            context.registerBean("unrelated", String.class, () -> "ignored");

            TestDatabaseInitializerDetector initializerDetector = new TestDatabaseInitializerDetector(
                TestDatabaseInitializer.class);
            TestDependsOnInitializationDetector dependsOnDetector = new TestDependsOnInitializationDetector(
                ReportingService.class);

            assertThat(initializerDetector.detect(context.getBeanFactory())).containsExactly("schemaInitializer");
            assertThat(dependsOnDetector.detect(context.getBeanFactory())).containsExactly("reportingService");
            assertThat(initializerDetector.getOrder()).isEqualTo(0);
            initializerDetector.detectionComplete(context.getBeanFactory(), Set.of("schemaInitializer"));
        }
    }

    @Test
    void dependencyConfigurerRegistersItsBeanFactoryPostProcessorOnlyOnce() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            DatabaseInitializationDependencyConfigurer configurer = new DatabaseInitializationDependencyConfigurer();
            BeanDefinitionRegistry registry = context;

            configurer.registerBeanDefinitions(null, registry);
            configurer.registerBeanDefinitions(null, registry);

            assertThat(context.getBeanDefinitionNames())
                .filteredOn((name) -> name.contains("DependsOnDatabaseInitializationPostProcessor"))
                .hasSize(1);
        }
    }

    private static DatabaseInitializationSettings scriptSettings(DatabaseInitializationMode mode) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of("schema.sql"));
        settings.setDataLocations(List.of());
        settings.setMode(mode);
        return settings;
    }

    private static TestResourceLoader resourceLoaderWithSchema() {
        return new TestResourceLoader(Map.of("schema.sql", List.of(resource("schema.sql",
            "create table test(id int)"))));
    }

    private static Resource resource(String filename, String content) {
        return resource(filename, content, null);
    }

    private static Resource resource(String filename, String content, String url) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), filename) {

            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public URL getURL() throws IOException {
                return (url != null) ? URI.create(url).toURL() : super.getURL();
            }

        };
    }

    private record ScriptRun(boolean continueOnError, String separator, Charset encoding, List<String> contents) {
    }

    private static class CapturingScriptDatabaseInitializer extends AbstractScriptDatabaseInitializer {

        private final List<ScriptRun> runs = new ArrayList<>();

        private boolean embedded;

        CapturingScriptDatabaseInitializer(DatabaseInitializationSettings settings) {
            super(settings);
        }

        @Override
        protected boolean isEmbeddedDatabase() {
            return this.embedded;
        }

        @Override
        protected void runScripts(Scripts scripts) {
            List<String> contents = new ArrayList<>();
            for (Resource resource : scripts) {
                contents.add(content(resource));
            }
            this.runs.add(new ScriptRun(scripts.isContinueOnError(), scripts.getSeparator(), scripts.getEncoding(),
                contents));
        }

        private static String content(Resource resource) {
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

    }

    private static final class ThrowingEmbeddedScriptDatabaseInitializer extends AbstractScriptDatabaseInitializer {

        private ThrowingEmbeddedScriptDatabaseInitializer(DatabaseInitializationSettings settings) {
            super(settings);
        }

        @Override
        protected void runScripts(Scripts scripts) {
            // No scripts should run when the initializer cannot determine whether the database is embedded.
        }

    }

    private static final class TestResourceLoader implements ResourcePatternResolver {

        private final Map<String, List<Resource>> resources;

        private TestResourceLoader(Map<String, List<Resource>> resources) {
            this.resources = new HashMap<>(resources);
        }

        @Override
        public Resource getResource(String location) {
            List<Resource> candidates = this.resources.get(location);
            return (candidates != null && !candidates.isEmpty()) ? candidates.get(0)
                : new DescriptiveResource(location);
        }

        @Override
        public Resource[] getResources(String locationPattern) {
            List<Resource> candidates = this.resources.get(locationPattern);
            if (candidates != null) {
                return candidates.toArray(Resource[]::new);
            }
            return new Resource[] {new DescriptiveResource(locationPattern) };
        }

        @Override
        public ClassLoader getClassLoader() {
            return ResourceLoader.class.getClassLoader();
        }

    }

    private static final class TestDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

        private final Set<Class<?>> types;

        private TestDatabaseInitializerDetector(Class<?>... types) {
            this.types = Set.of(types);
        }

        @Override
        protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
            return this.types;
        }

    }

    private static final class TestDependsOnInitializationDetector
            extends AbstractBeansOfTypeDependsOnDatabaseInitializationDetector {

        private final Set<Class<?>> types;

        private TestDependsOnInitializationDetector(Class<?>... types) {
            this.types = Set.of(types);
        }

        @Override
        protected Set<Class<?>> getDependsOnDatabaseInitializationBeanTypes() {
            return this.types;
        }

    }

    private static final class TestDatabaseInitializer {
    }

    private static final class ReportingService {
    }

}
