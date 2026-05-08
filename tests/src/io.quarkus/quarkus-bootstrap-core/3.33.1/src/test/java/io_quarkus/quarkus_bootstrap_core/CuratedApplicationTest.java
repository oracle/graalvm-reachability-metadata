/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.runner.bootstrap.AugmentActionImpl;

public class CuratedApplicationTest {

    @TempDir
    Path applicationRoot;

    @Test
    void createsDefaultAugmentorInAugmentClassLoader() throws Exception {
        try (CuratedApplication application = curatedApplication()) {
            AugmentAction action = application.createAugmentor();

            assertThat(action).isInstanceOf(AugmentActionImpl.class);
            AugmentActionImpl augmentor = (AugmentActionImpl) action;
            assertThat(augmentor.getApplication()).isSameAs(application);
            assertThat(augmentor.getBuildChainCustomizers()).isEmpty();
            assertThat(augmentor.getBuildExecutionCustomizers()).isEmpty();
        }
    }

    @Test
    void createsAugmentorWithBuildChainCustomizersFromFunction() throws Exception {
        try (CuratedApplication application = curatedApplication()) {
            Map<String, Object> properties = Map.of("source", "list");

            AugmentActionImpl augmentor = (AugmentActionImpl) application.createAugmentor(
                    ListFunction.class.getName(), properties);

            assertThat(augmentor.getApplication()).isSameAs(application);
            assertThat(augmentor.getBuildChainCustomizers()).containsExactly("build-chain", properties);
            assertThat(augmentor.getBuildExecutionCustomizers()).isEmpty();
            assertThat(augmentor.getExtraCustomizers()).isEmpty();
        }
    }

    @Test
    void createsAugmentorWithBuildChainAndExecutionCustomizersFromFunction() throws Exception {
        try (CuratedApplication application = curatedApplication()) {
            Map<String, Object> properties = Map.of("source", "entry");

            AugmentActionImpl augmentor = (AugmentActionImpl) application.createAugmentor(
                    EntryFunction.class.getName(), properties);

            assertThat(augmentor.getApplication()).isSameAs(application);
            assertThat(augmentor.getBuildChainCustomizers()).containsExactly("build-chain", properties);
            assertThat(augmentor.getBuildExecutionCustomizers()).containsExactly("build-execution", properties);
            assertThat(augmentor.getExtraCustomizers()).isEmpty();
        }
    }

    @Test
    void runsConsumerInAugmentClassLoader() throws Exception {
        try (CuratedApplication application = curatedApplication()) {
            Map<String, Object> parameters = new HashMap<>();

            Object consumer = application.runInAugmentClassLoader(
                    AugmentClassLoaderConsumer.class.getName(), parameters);

            assertThat(consumer).isInstanceOf(AugmentClassLoaderConsumer.class);
            assertThat(parameters)
                    .containsEntry("accepted", Boolean.TRUE)
                    .containsEntry("application", application);
            assertThat(parameters.get("contextClassLoader")).isSameAs(application.getAugmentClassLoader());
        }
    }

    private CuratedApplication curatedApplication() throws Exception {
        return QuarkusBootstrap.builder()
                .setApplicationRoot(applicationRoot)
                .setBaseName("curated-application-test")
                .setExistingModel(new ApplicationModelBuilder()
                        .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                                .setGroupId("io.quarkus.test")
                                .setArtifactId("curated-application-test")
                                .setVersion("1.0")
                                .setResolvedPath(applicationRoot))
                        .build())
                .build()
                .bootstrap();
    }

    public static final class ListFunction implements Function<Object, Object> {

        public ListFunction() {
        }

        @Override
        public Object apply(Object properties) {
            return List.of("build-chain", properties);
        }
    }

    public static final class EntryFunction implements Function<Object, Object> {

        public EntryFunction() {
        }

        @Override
        public Object apply(Object properties) {
            return Map.entry(List.of("build-chain", properties), List.of("build-execution", properties));
        }
    }

    public static final class AugmentClassLoaderConsumer
            implements BiConsumer<CuratedApplication, Map<String, Object>> {

        public AugmentClassLoaderConsumer() {
        }

        @Override
        public void accept(CuratedApplication application, Map<String, Object> parameters) {
            parameters.put("accepted", Boolean.TRUE);
            parameters.put("application", application);
            parameters.put("contextClassLoader", Thread.currentThread().getContextClassLoader());
        }
    }
}
