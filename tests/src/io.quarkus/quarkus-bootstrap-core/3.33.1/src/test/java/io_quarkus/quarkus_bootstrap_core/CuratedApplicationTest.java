/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;

public class CuratedApplicationTest {

    private static final String AUGMENT_ACTION_IMPL = "io.quarkus.runner.bootstrap.AugmentActionImpl";

    @TempDir
    Path applicationRoot;

    @Test
    void createsDefaultAugmentorWithApplicationConstructor() throws Exception {
        try (CuratedApplication application = newCuratedApplication()) {
            final AugmentAction action;
            try {
                action = application.createAugmentor();
            } catch (Error error) {
                assertTrue(NativeImageSupport.isUnsupportedFeatureError(error));
                return;
            }

            assertNotNull(action);
            assertEquals(AUGMENT_ACTION_IMPL, action.getClass().getName());
        }
    }

    @Test
    void createsAugmentorFromListCustomizerFunction() throws Exception {
        final Map<String, Object> properties = Map.of("chain", "customizer");

        try (CuratedApplication application = newCuratedApplication()) {
            final AugmentAction action;
            try {
                action = application.createAugmentor(ListCustomizerFunction.class.getName(), properties);
            } catch (Error error) {
                assertTrue(NativeImageSupport.isUnsupportedFeatureError(error));
                return;
            }

            assertNotNull(action);
            assertEquals(AUGMENT_ACTION_IMPL, action.getClass().getName());
            assertSame(properties, ListCustomizerFunction.lastProperties);
        }
    }

    @Test
    void createsAugmentorFromEntryCustomizerFunction() throws Exception {
        final Map<String, Object> properties = Map.of("execution", "customizer");

        try (CuratedApplication application = newCuratedApplication()) {
            final AugmentAction action;
            try {
                action = application.createAugmentor(EntryCustomizerFunction.class.getName(), properties);
            } catch (Error error) {
                assertTrue(NativeImageSupport.isUnsupportedFeatureError(error));
                return;
            }

            assertNotNull(action);
            assertEquals(AUGMENT_ACTION_IMPL, action.getClass().getName());
            assertSame(properties, EntryCustomizerFunction.lastProperties);
        }
    }

    @Test
    void runsConsumerInAugmentClassLoader() throws Exception {
        final Map<String, Object> parameters = Map.of("message", "hello");

        try (CuratedApplication application = newCuratedApplication()) {
            final Object result = application.runInAugmentClassLoader(RecordingConsumer.class.getName(), parameters);

            assertInstanceOf(RecordingConsumer.class, result);
            final RecordingConsumer consumer = (RecordingConsumer) result;
            assertSame(application, consumer.application);
            assertSame(parameters, consumer.parameters);
        }
    }

    private CuratedApplication newCuratedApplication() throws Exception {
        final ApplicationModel model = new ApplicationModelBuilder()
                .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                        .setGroupId("test")
                        .setArtifactId("curated-application")
                        .setVersion("1.0")
                        .setResolvedPath(applicationRoot))
                .build();
        final QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                .setApplicationRoot(applicationRoot)
                .setExistingModel(model);
        for (String archive : System.getProperty("quarkus.core.deployment.classpath", "").split(File.pathSeparator)) {
            if (!archive.isBlank()) {
                builder.addAdditionalDeploymentArchive(Path.of(archive));
            }
        }
        return builder.build().bootstrap();
    }

    public static final class ListCustomizerFunction implements Function<Object, Object> {
        static Object lastProperties;

        public ListCustomizerFunction() {
        }

        @Override
        public Object apply(Object properties) {
            lastProperties = properties;
            return List.of();
        }
    }

    public static final class EntryCustomizerFunction implements Function<Object, Object> {
        static Object lastProperties;

        public EntryCustomizerFunction() {
        }

        @Override
        public Object apply(Object properties) {
            lastProperties = properties;
            return new AbstractMap.SimpleImmutableEntry<>(List.of(), List.of());
        }
    }

    public static final class RecordingConsumer implements BiConsumer<CuratedApplication, Map<String, Object>> {
        CuratedApplication application;
        Map<String, Object> parameters;

        public RecordingConsumer() {
        }

        @Override
        public void accept(CuratedApplication application, Map<String, Object> parameters) {
            this.application = application;
            this.parameters = parameters;
        }
    }
}
