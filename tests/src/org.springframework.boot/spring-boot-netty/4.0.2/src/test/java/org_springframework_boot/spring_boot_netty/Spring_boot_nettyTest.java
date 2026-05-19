/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_netty;

import io.netty.util.NettyRuntime;
import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.netty.autoconfigure.NettyAutoConfiguration;
import org.springframework.boot.netty.autoconfigure.NettyProperties;
import org.springframework.boot.netty.autoconfigure.NettyProperties.LeakDetection;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_nettyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NettyAutoConfiguration.class));

    @Test
    void nettyPropertiesExposeAllLeakDetectionLevels() {
        NettyProperties properties = new NettyProperties();

        assertThat(properties.getLeakDetection()).isNull();
        for (LeakDetection leakDetection : LeakDetection.values()) {
            properties.setLeakDetection(leakDetection);

            assertThat(properties.getLeakDetection()).isSameAs(leakDetection);
            assertThat(ResourceLeakDetector.Level.valueOf(leakDetection.name()).name()).isEqualTo(leakDetection.name());
        }

        properties.setLeakDetection(null);
        assertThat(properties.getLeakDetection()).isNull();
    }

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(NettyAutoConfiguration.class.getName());
    }

    @Test
    void autoConfigurationBacksOffWhenNettyRuntimeIsUnavailable() {
        new ApplicationContextRunner().withClassLoader(new FilteredClassLoader(NettyRuntime.class))
            .withConfiguration(AutoConfigurations.of(NettyAutoConfiguration.class))
            .run((context) -> {
                assertThat(context.getBeanNamesForType(NettyAutoConfiguration.class)).isEmpty();
                assertThat(context.getBeanNamesForType(NettyProperties.class)).isEmpty();
                assertThat(context.getBeanNamesForType(LazyInitializationExcludeFilter.class)).isEmpty();
            });
    }

    @Test
    void autoConfigurationBindsLeakDetectionAndUpdatesNettyGlobalLevel() throws Throwable {
        runWithRestoredLeakDetectionLevel(() -> this.contextRunner
                .withPropertyValues("spring.netty.leak-detection=paranoid")
                .run((context) -> {
                    assertThat(context.getBeanNamesForType(NettyAutoConfiguration.class)).hasSize(1);
                    assertThat(context.getBeanNamesForType(NettyProperties.class)).hasSize(1);
                    NettyProperties properties = context.getBean(NettyProperties.class);

                    assertThat(properties.getLeakDetection()).isSameAs(LeakDetection.PARANOID);
                    assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.PARANOID);
                }));
    }

    @Test
    void autoConfigurationContributesLazyInitializationExcludeFilter() {
        this.contextRunner.run((context) -> {
            assertThat(context.getBeanNamesForType(NettyAutoConfiguration.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(LazyInitializationExcludeFilter.class)).hasSize(1);
            LazyInitializationExcludeFilter filter = context.getBean(LazyInitializationExcludeFilter.class);

            assertThat(filter.isExcluded("nettyAutoConfiguration", null, NettyAutoConfiguration.class)).isTrue();
            assertThat(filter.isExcluded("nettyProperties", null, NettyProperties.class)).isFalse();
        });
    }

    @Test
    void leakDetectionIsAppliedWhenSpringLazyInitializationIsEnabled() throws Throwable {
        runWithRestoredLeakDetectionLevel(() -> this.contextRunner
                .withInitializer((context) -> context
                    .addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .withPropertyValues("spring.netty.leak-detection=advanced")
                .run((context) -> assertThat(ResourceLeakDetector.getLevel())
                    .isEqualTo(ResourceLeakDetector.Level.ADVANCED)));
    }

    @Test
    void autoConfigurationLeavesExistingNettyGlobalLevelWhenPropertyIsNotSet() throws Throwable {
        runWithRestoredLeakDetectionLevel(() -> {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);

            this.contextRunner.run((context) -> {
                assertThat(context.getBean(NettyProperties.class).getLeakDetection()).isNull();
                assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.ADVANCED);
            });
        });
    }

    private void runWithRestoredLeakDetectionLevel(ThrowingRunnable runnable) throws Throwable {
        ResourceLeakDetector.Level originalLevel = ResourceLeakDetector.getLevel();
        try {
            runnable.run();
        }
        finally {
            ResourceLeakDetector.setLevel(originalLevel);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Throwable;

    }

}
