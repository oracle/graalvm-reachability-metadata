/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.pool.TypePool;
import org.modelmapper.internal.bytebuddy.utility.JavaModule;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerAsynchronousInnerThreadSwitchingClassLoadingDelegateInnerSimpleClassLoadingActionTest {
    @Test
    void loadsSuperTypeThroughSimpleActionWhenCallerDoesNotOwnClassLoaderLock() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ClassLoader targetClassLoader = new DelegatingTestClassLoader(getClass().getClassLoader());
            TypeDescription childDescription = new TypeDescription.Latent(
                "org_modelmapper.modelmapper.GeneratedChildForSimpleClassLoadingAction",
                Modifier.PUBLIC,
                TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(LoadedSuperType.class));
            AgentBuilder.DescriptionStrategy descriptionStrategy =
                new AgentBuilder.DescriptionStrategy.SuperTypeLoading.Asynchronous(
                    new FixedDescriptionStrategy(childDescription),
                    executorService);

            TypeDescription loadingDescription = descriptionStrategy.apply(
                childDescription.getName(),
                null,
                TypePool.Empty.INSTANCE,
                AgentBuilder.CircularityLock.Inactive.INSTANCE,
                targetClassLoader,
                JavaModule.UNSUPPORTED);

            assertThat(loadingDescription).isInstanceOf(TypeDescription.SuperTypeLoading.class);
            assertThat(Thread.holdsLock(targetClassLoader)).isFalse();

            TypeDescription loadedSuperType = loadingDescription.getSuperClass().asErasure();

            assertThat(loadedSuperType.represents(LoadedSuperType.class)).isTrue();
            assertThat(loadedSuperType).isEqualTo(TypeDescription.ForLoadedType.of(LoadedSuperType.class));
        } finally {
            executorService.shutdownNow();
        }
    }

    private static final class FixedDescriptionStrategy implements AgentBuilder.DescriptionStrategy {
        private final TypeDescription typeDescription;

        FixedDescriptionStrategy(TypeDescription typeDescription) {
            this.typeDescription = typeDescription;
        }

        @Override
        public boolean isLoadedFirst() {
            return false;
        }

        @Override
        public TypeDescription apply(
            String typeName,
            Class<?> type,
            TypePool typePool,
            AgentBuilder.CircularityLock circularityLock,
            ClassLoader classLoader,
            JavaModule module) {
            return typeDescription;
        }
    }

    private static final class DelegatingTestClassLoader extends ClassLoader {
        DelegatingTestClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static class LoadedSuperType {
    }
}
