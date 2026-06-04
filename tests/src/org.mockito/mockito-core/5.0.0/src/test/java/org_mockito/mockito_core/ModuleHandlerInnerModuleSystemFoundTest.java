/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FixedValue;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class ModuleHandlerInnerModuleSystemFoundTest {
    private static final String MODULE_NAME = "mockito.test.module";
    private static final String HIDDEN_PACKAGE = "sample.hidden";
    private static final String HIDDEN_TYPE_NAME = HIDDEN_PACKAGE + ".HiddenService";
    private static final String HIDDEN_TYPE_RESOURCE = "sample/hidden/HiddenService.class";

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void subclassMockMakerMocksTypeFromNonExportedNamedModule() throws Exception {
        try {
            Object mock = mockHiddenTypeFromNamedModule();

            assertThat(Mockito.mockingDetails(mock).isMock()).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static Object mockHiddenTypeFromNamedModule() throws Exception {
        Class<?> hiddenType = loadHiddenTypeFromNamedModule();

        assertThat(hiddenType.getModule().isNamed()).isTrue();
        assertThat(hiddenType.getModule().isExported(HIDDEN_PACKAGE)).isFalse();

        return Mockito.mock(hiddenType, withSettings().mockMaker(MockMakers.SUBCLASS));
    }

    private static Class<?> loadHiddenTypeFromNamedModule() throws ClassNotFoundException {
        ModuleLayer parent = ModuleLayer.boot();
        ModuleFinder finder = new InMemoryModuleFinder();
        Configuration configuration =
                parent.configuration().resolve(finder, ModuleFinder.of(), Set.of(MODULE_NAME));
        ModuleLayer layer =
                parent.defineModulesWithOneLoader(
                        configuration, ClassLoader.getSystemClassLoader());

        return layer.findLoader(MODULE_NAME).loadClass(HIDDEN_TYPE_NAME);
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class InMemoryModuleFinder implements ModuleFinder {
        private final ModuleReference moduleReference;

        private InMemoryModuleFinder() {
            ModuleDescriptor descriptor =
                    ModuleDescriptor.newModule(MODULE_NAME)
                            .packages(Set.of(HIDDEN_PACKAGE))
                            .build();
            moduleReference =
                    new InMemoryModuleReference(
                            descriptor, Map.of(HIDDEN_TYPE_RESOURCE, hiddenServiceBytes()));
        }

        @Override
        public Optional<ModuleReference> find(String name) {
            if (MODULE_NAME.equals(name)) {
                return Optional.of(moduleReference);
            }
            return Optional.empty();
        }

        @Override
        public Set<ModuleReference> findAll() {
            return Set.of(moduleReference);
        }
    }

    private static byte[] hiddenServiceBytes() {
        return new ByteBuddy()
                .subclass(Object.class)
                .name(HIDDEN_TYPE_NAME)
                .modifiers(Visibility.PUBLIC)
                .defineMethod("greeting", String.class, Visibility.PUBLIC)
                .intercept(FixedValue.value("real"))
                .make()
                .getBytes();
    }

    private static final class InMemoryModuleReference extends ModuleReference {
        private final Map<String, byte[]> resources;

        private InMemoryModuleReference(
                ModuleDescriptor descriptor, Map<String, byte[]> resources) {
            super(descriptor, URI.create("memory:///" + descriptor.name()));
            this.resources = resources;
        }

        @Override
        public ModuleReader open() {
            return new InMemoryModuleReader(resources);
        }
    }

    private static final class InMemoryModuleReader implements ModuleReader {
        private final Map<String, byte[]> resources;

        private InMemoryModuleReader(Map<String, byte[]> resources) {
            this.resources = resources;
        }

        @Override
        public Optional<URI> find(String name) {
            if (resources.containsKey(name)) {
                return Optional.of(URI.create("memory:///" + name));
            }
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            byte[] bytes = resources.get(name);
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(new ByteArrayInputStream(bytes));
        }

        @Override
        public Optional<ByteBuffer> read(String name) {
            byte[] bytes = resources.get(name);
            if (bytes == null) {
                return Optional.empty();
            }
            return Optional.of(ByteBuffer.wrap(bytes));
        }

        @Override
        public void release(ByteBuffer byteBuffer) {
        }

        @Override
        public Stream<String> list() {
            return resources.keySet().stream();
        }

        @Override
        public void close() throws IOException {
        }
    }
}
