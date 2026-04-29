/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;
import org.modelmapper.internal.bytebuddy.pool.TypePool;
import org.modelmapper.internal.bytebuddy.utility.JavaModule;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerUnlockingClassLoadingDelegateTest {
    @Test
    void loadsSuperTypeAfterReleasingCircularityLock() {
        TrackingCircularityLock circularityLock = new TrackingCircularityLock();
        ClassLoadingProbeClassLoader targetClassLoader = new ClassLoadingProbeClassLoader(
            getClass().getClassLoader(),
            circularityLock);
        TypeDescription childDescription = new TypeDescription.Latent(
            "org_modelmapper.modelmapper.GeneratedChildForUnlockingClassLoadingDelegate",
            Modifier.PUBLIC,
            TypeDescription.Generic.OfNonGenericType.ForLoadedType.of(LoadedSuperType.class));
        AgentBuilder.DescriptionStrategy descriptionStrategy =
            new AgentBuilder.DescriptionStrategy.SuperTypeLoading(new FixedDescriptionStrategy(childDescription));

        TypeDescription loadingDescription = descriptionStrategy.apply(
            childDescription.getName(),
            null,
            TypePool.Empty.INSTANCE,
            circularityLock,
            targetClassLoader,
            JavaModule.UNSUPPORTED);

        assertThat(loadingDescription).isInstanceOf(TypeDescription.SuperTypeLoading.class);

        TypeDescription loadedSuperType = loadingDescription.getSuperClass().asErasure();

        assertThat(loadedSuperType.represents(LoadedSuperType.class)).isTrue();
        assertThat(loadedSuperType).isEqualTo(TypeDescription.ForLoadedType.of(LoadedSuperType.class));
        assertThat(targetClassLoader.loadedRequestedSuperType()).isTrue();
        assertThat(targetClassLoader.lockHeldDuringRequestedLoad()).isFalse();
        assertThat(circularityLock.events()).containsExactly("release", "acquire");
        assertThat(circularityLock.isAcquired()).isTrue();
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

    private static final class TrackingCircularityLock implements AgentBuilder.CircularityLock {
        private final List<String> events = new ArrayList<>();
        private boolean acquired = true;

        @Override
        public boolean acquire() {
            events.add("acquire");
            acquired = true;
            return true;
        }

        @Override
        public void release() {
            events.add("release");
            acquired = false;
        }

        boolean isAcquired() {
            return acquired;
        }

        List<String> events() {
            return events;
        }
    }

    private static final class ClassLoadingProbeClassLoader extends ClassLoader {
        private final TrackingCircularityLock circularityLock;
        private boolean loadedRequestedSuperType;
        private boolean lockHeldDuringRequestedLoad;

        ClassLoadingProbeClassLoader(ClassLoader parent, TrackingCircularityLock circularityLock) {
            super(parent);
            this.circularityLock = circularityLock;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (LoadedSuperType.class.getName().equals(name)) {
                loadedRequestedSuperType = true;
                lockHeldDuringRequestedLoad = circularityLock.isAcquired();
            }
            return super.loadClass(name, resolve);
        }

        boolean loadedRequestedSuperType() {
            return loadedRequestedSuperType;
        }

        boolean lockHeldDuringRequestedLoad() {
            return lockHeldDuringRequestedLoad;
        }
    }

    public static class LoadedSuperType {
    }
}
