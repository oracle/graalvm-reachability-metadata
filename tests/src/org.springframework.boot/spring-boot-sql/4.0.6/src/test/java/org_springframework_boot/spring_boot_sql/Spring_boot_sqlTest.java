/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_sql;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.sql.autoconfigure.init.ApplicationScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.ConditionalOnSqlInitialization;
import org.springframework.boot.sql.autoconfigure.init.OnDatabaseInitializationCondition;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDependsOnDatabaseInitializationDetector;
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Spring_boot_sqlTest {

    @Test
    void sqlInitializationPropertiesBindAndAdaptToDefaultSettings() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.sql.init.platform", "postgresql",
                "spring.sql.init.continue-on-error", "true",
                "spring.sql.init.separator", "@@",
                "spring.sql.init.encoding", "UTF-8",
                "spring.sql.init.mode", "always",
                "spring.sql.init.username", "schema-owner",
                "spring.sql.init.password", "secret")));

        SqlInitializationProperties properties = Binder.get(environment)
                .bind("spring.sql.init", SqlInitializationProperties.class)
                .get();
        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertThat(properties.getPlatform()).isEqualTo("postgresql");
        assertThat(properties.getUsername()).isEqualTo("schema-owner");
        assertThat(properties.getPassword()).isEqualTo("secret");
        assertThat(settings.getSchemaLocations()).containsExactly("optional:classpath*:schema-postgresql.sql",
                "optional:classpath*:schema.sql");
        assertThat(settings.getDataLocations()).containsExactly("optional:classpath*:data-postgresql.sql",
                "optional:classpath*:data.sql");
        assertThat(settings.isContinueOnError()).isTrue();
        assertThat(settings.getSeparator()).isEqualTo("@@");
        assertThat(settings.getEncoding()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(settings.getMode()).isEqualTo(DatabaseInitializationMode.ALWAYS);
    }

    @Test
    void explicitInitializationPropertiesOverrideFallbackScriptLocations() {
        SqlInitializationProperties properties = new SqlInitializationProperties();
        properties.setSchemaLocations(List.of("classpath:/db/schema.sql"));
        properties.setDataLocations(List.of("classpath:/db/data.sql"));
        properties.setMode(DatabaseInitializationMode.NEVER);

        DatabaseInitializationSettings settings = ApplicationScriptDatabaseInitializer.getSettings(properties);

        assertThat(settings.getSchemaLocations()).containsExactly("classpath:/db/schema.sql");
        assertThat(settings.getDataLocations()).containsExactly("classpath:/db/data.sql");
        assertThat(settings.getMode()).isEqualTo(DatabaseInitializationMode.NEVER);
    }

    @Test
    void initializerResolvesSortedSchemaAndDataScriptsWithConfiguredOptions(@TempDir Path directory)
            throws IOException {
        Path secondSchema = writeScript(directory, "schema-b.sql", "create table b(id int);");
        Path firstSchema = writeScript(directory, "schema-a.sql", "create table a(id int);");
        Path data = writeScript(directory, "data.sql", "insert into a values (1);");
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(fileUri(directory) + "schema-*.sql"));
        settings.setDataLocations(List.of(fileUri(data)));
        settings.setContinueOnError(true);
        settings.setSeparator("@@");
        settings.setEncoding(StandardCharsets.UTF_8);
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        RecordingScriptDatabaseInitializer initializer = new RecordingScriptDatabaseInitializer(settings, false);
        initializer.setResourceLoader(resourceLoader());

        boolean initialized = initializer.initializeDatabase();

        assertThat(initialized).isTrue();
        assertThat(initializer.batches()).hasSize(2);
        assertThat(initializer.batches().get(0).resourceNames()).containsExactly(firstSchema.getFileName().toString(),
                secondSchema.getFileName().toString());
        assertThat(initializer.batches().get(1).resourceNames()).containsExactly(data.getFileName().toString());
        assertThat(initializer.batches()).allSatisfy((batch) -> {
            assertThat(batch.continueOnError()).isTrue();
            assertThat(batch.separator()).isEqualTo("@@");
            assertThat(batch.encoding()).isEqualTo(StandardCharsets.UTF_8.name());
        });
    }

    @Test
    void initializerHonorsInitializationModeAndRequiredScriptLocations(@TempDir Path directory) throws IOException {
        Path schema = writeScript(directory, "schema.sql", "create table sample(id int);");
        DatabaseInitializationSettings neverSettings = new DatabaseInitializationSettings();
        neverSettings.setSchemaLocations(List.of(fileUri(schema)));
        neverSettings.setMode(DatabaseInitializationMode.NEVER);
        RecordingScriptDatabaseInitializer neverInitializer = new RecordingScriptDatabaseInitializer(neverSettings,
                true);
        neverInitializer.setResourceLoader(resourceLoader());

        assertThat(neverInitializer.initializeDatabase()).isFalse();
        assertThat(neverInitializer.batches()).isEmpty();

        DatabaseInitializationSettings missingOptionalSettings = new DatabaseInitializationSettings();
        missingOptionalSettings.setSchemaLocations(List.of("optional:" + fileUri(directory) + "missing.sql"));
        missingOptionalSettings.setMode(DatabaseInitializationMode.ALWAYS);
        RecordingScriptDatabaseInitializer optionalInitializer = new RecordingScriptDatabaseInitializer(
                missingOptionalSettings, true);
        optionalInitializer.setResourceLoader(resourceLoader());

        assertThat(optionalInitializer.initializeDatabase()).isFalse();

        DatabaseInitializationSettings missingRequiredSettings = new DatabaseInitializationSettings();
        missingRequiredSettings.setSchemaLocations(List.of(fileUri(directory) + "missing.sql"));
        missingRequiredSettings.setMode(DatabaseInitializationMode.ALWAYS);
        RecordingScriptDatabaseInitializer requiredInitializer = new RecordingScriptDatabaseInitializer(
                missingRequiredSettings, true);
        requiredInitializer.setResourceLoader(resourceLoader());

        assertThatIllegalStateException().isThrownBy(requiredInitializer::initializeDatabase)
                .withMessageContaining("No schema scripts found at location");
    }

    @Test
    void embeddedInitializationModeRunsOnlyWhenDatabaseIsEmbedded(@TempDir Path directory) throws IOException {
        Path schema = writeScript(directory, "schema.sql", "create table embedded_sample(id int);");
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(fileUri(schema)));
        settings.setMode(DatabaseInitializationMode.EMBEDDED);
        RecordingScriptDatabaseInitializer nonEmbeddedInitializer = new RecordingScriptDatabaseInitializer(settings,
                false);
        nonEmbeddedInitializer.setResourceLoader(resourceLoader());
        RecordingScriptDatabaseInitializer embeddedInitializer = new RecordingScriptDatabaseInitializer(settings, true);
        embeddedInitializer.setResourceLoader(resourceLoader());

        assertThat(nonEmbeddedInitializer.initializeDatabase()).isFalse();
        assertThat(nonEmbeddedInitializer.batches()).isEmpty();
        assertThat(embeddedInitializer.initializeDatabase()).isTrue();
        assertThat(embeddedInitializer.batches()).singleElement()
                .extracting(RecordedScripts::resourceNames)
                .isEqualTo(List.of(schema.getFileName().toString()));
    }

    @Test
    void conditionalOnSqlInitializationMatchesUnlessModeIsNever() {
        TestDatabaseInitializationCondition condition = new TestDatabaseInitializationCondition();

        assertThat(matchOutcome(condition, null).isMatch()).isTrue();
        assertThat(matchOutcome(condition, "always").isMatch()).isTrue();
        assertThat(matchOutcome(condition, "never").isMatch()).isFalse();
        try (AnnotationConfigApplicationContext context = contextWithSqlInitializationMode("always")) {
            assertThat(context.containsBean("sqlInitializationAwareBean")).isTrue();
        }
    }

    @Test
    void publicDatabaseInitializationDetectorsFindBeansByType() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                DetectorSampleConfiguration.class)) {
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            DatabaseInitializerDetector initializerDetector = new SampleDatabaseInitializerDetector();
            DependsOnDatabaseInitializationDetector dependencyDetector = new SampleDependsOnInitializationDetector();

            assertThat(initializerDetector.detect(beanFactory)).containsExactly("sampleInitializer");
            assertThat(dependencyDetector.detect(beanFactory)).containsExactlyInAnyOrder("annotatedDependent",
                    "sampleDependent");
            assertThat(beanFactory.findAnnotationOnBean("annotatedDependent", DependsOnDatabaseInitialization.class))
                    .isNotNull();
            assertThat(beanFactory.findAnnotationOnBean("sampleDependent", DependsOnDatabaseInitialization.class))
                    .isNull();
            assertThat(initializerDetector.getOrder()).isZero();
        }
    }

    private static Path writeScript(Path directory, String name, String content) throws IOException {
        Path script = directory.resolve(name);
        Files.writeString(script, content, StandardCharsets.UTF_8);
        return script;
    }

    private static String fileUri(Path path) {
        URI uri = path.toUri();
        return uri.toString();
    }

    private static ResourceLoader resourceLoader() {
        return new DefaultResourceLoader(Spring_boot_sqlTest.class.getClassLoader());
    }

    private static AnnotationConfigApplicationContext contextWithSqlInitializationMode(String mode) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (mode != null) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("spring.sql.init.mode", mode)));
        }
        context.register(ConditionalSqlInitializationConfiguration.class);
        context.refresh();
        return context;
    }

    private static ConditionOutcome matchOutcome(TestDatabaseInitializationCondition condition, String mode) {
        StandardEnvironment environment = new StandardEnvironment();
        if (mode != null) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of("spring.sql.init.mode", mode)));
        }
        return condition.getMatchOutcome(new TestConditionContext(environment), new EmptyAnnotatedTypeMetadata());
    }

    private record RecordedScripts(List<String> resourceNames, boolean continueOnError, String separator,
            String encoding) {
    }

    private record TestConditionContext(Environment environment) implements ConditionContext {

        @Override
        public BeanDefinitionRegistry getRegistry() {
            return null;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return null;
        }

        @Override
        public Environment getEnvironment() {
            return this.environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return resourceLoader();
        }

        @Override
        public ClassLoader getClassLoader() {
            return Spring_boot_sqlTest.class.getClassLoader();
        }

    }

    private static class EmptyAnnotatedTypeMetadata implements AnnotatedTypeMetadata {

        @Override
        public MergedAnnotations getAnnotations() {
            return MergedAnnotations.from();
        }

    }

    public static class TestDatabaseInitializationCondition extends OnDatabaseInitializationCondition {

        TestDatabaseInitializationCondition() {
            super("Test SQL", "spring.sql.init.mode");
        }

    }

    public static class RecordingScriptDatabaseInitializer extends AbstractScriptDatabaseInitializer {

        private final boolean embeddedDatabase;

        private final List<RecordedScripts> batches = new ArrayList<>();

        RecordingScriptDatabaseInitializer(DatabaseInitializationSettings settings, boolean embeddedDatabase) {
            super(settings);
            this.embeddedDatabase = embeddedDatabase;
        }

        List<RecordedScripts> batches() {
            return this.batches;
        }

        @Override
        protected boolean isEmbeddedDatabase() {
            return this.embeddedDatabase;
        }

        @Override
        protected void runScripts(Scripts scripts) {
            List<String> resourceNames = new ArrayList<>();
            for (Resource resource : scripts) {
                resourceNames.add(resource.getFilename());
            }
            String encoding = (scripts.getEncoding() != null) ? scripts.getEncoding().name() : null;
            this.batches.add(new RecordedScripts(resourceNames, scripts.isContinueOnError(), scripts.getSeparator(),
                    encoding));
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class ConditionalSqlInitializationConfiguration {

        @Bean
        @ConditionalOnSqlInitialization
        String sqlInitializationAwareBean() {
            return "enabled";
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class DetectorSampleConfiguration {

        @Bean
        SampleInitializer sampleInitializer() {
            return new SampleInitializer();
        }

        @Bean
        SampleDependent sampleDependent() {
            return new SampleDependent();
        }

        @Bean
        AnnotatedDependent annotatedDependent() {
            return new AnnotatedDependent();
        }

    }

    public static class SampleDatabaseInitializerDetector extends AbstractBeansOfTypeDatabaseInitializerDetector {

        @Override
        protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
            return Set.of(SampleInitializer.class);
        }

    }

    public static class SampleDependsOnInitializationDetector
            extends AbstractBeansOfTypeDependsOnDatabaseInitializationDetector {

        @Override
        protected Set<Class<?>> getDependsOnDatabaseInitializationBeanTypes() {
            return Set.of(SampleDependent.class, AnnotatedDependent.class);
        }

    }

    public static class SampleInitializer {

    }

    public static class SampleDependent {

    }

    @DependsOnDatabaseInitialization
    public static class AnnotatedDependent {

    }

}
