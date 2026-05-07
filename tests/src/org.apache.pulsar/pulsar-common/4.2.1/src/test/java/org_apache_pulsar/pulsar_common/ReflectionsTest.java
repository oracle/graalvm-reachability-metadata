/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import org.apache.pulsar.common.api.raw.RawMessageId;
import org.apache.pulsar.common.api.raw.RawMessageIdImpl;
import org.apache.pulsar.common.policies.data.DrainingHash;
import org.apache.pulsar.common.policies.data.stats.DrainingHashImpl;
import org.apache.pulsar.common.protocol.schema.LongSchemaVersionResponse;
import org.apache.pulsar.common.util.Reflections;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {
    private static final ClassLoader CLASS_LOADER = ReflectionsTest.class.getClassLoader();

    @Test
    void createInstanceLoadsImplementationAndInvokesNoArgConstructor() {
        RawMessageId messageId = Reflections.createInstance(
                RawMessageIdImpl.class.getName(), RawMessageId.class, CLASS_LOADER);

        assertThat(messageId)
                .isInstanceOf(RawMessageIdImpl.class)
                .hasToString("(0,0,0)");
    }

    @Test
    void createInstanceLoadsConcreteClassAndInvokesNoArgConstructor() {
        Object instance = Reflections.createInstance(DrainingHashImpl.class.getName(), CLASS_LOADER);

        assertThat(instance)
                .isInstanceOf(DrainingHashImpl.class)
                .isInstanceOf(DrainingHash.class);
    }

    @Test
    void createInstanceLoadsConcreteClassAndInvokesMatchingConstructor() {
        LongSchemaVersionResponse response = (LongSchemaVersionResponse) Reflections.createInstance(
                LongSchemaVersionResponse.class.getName(),
                CLASS_LOADER,
                new Object[] {42L},
                new Class<?>[] {Long.class});

        assertThat(response.getVersion()).isEqualTo(42L);
    }

    @Test
    void classLookupMethodsResolveClassesFromApplicationAndJarClassLoaders() throws Exception {
        File emptyJar = Files.createTempFile("pulsar-common-reflections", ".jar").toFile();
        emptyJar.deleteOnExit();

        assertThat(Reflections.classExists(RawMessageIdImpl.class.getName())).isTrue();
        assertThat(Reflections.classImplementsIface(RawMessageIdImpl.class.getName(), RawMessageId.class)).isTrue();

        assertThat(Reflections.classExistsInJar(emptyJar, String.class.getName())).isTrue();
        assertThat(Reflections.classInJarImplementsIface(emptyJar, String.class.getName(), CharSequence.class))
                .isTrue();
    }

    @Test
    void loadClassResolvesDescriptorNamesRegularNamesAndArrayNames() throws Exception {
        assertThat(Reflections.loadClass("Ljava.lang.String;", CLASS_LOADER)).isEqualTo(String.class);
        assertThat(Reflections.loadClass(String.class.getName(), CLASS_LOADER)).isEqualTo(String.class);
        assertThat(Reflections.loadClass("[[Ljava.lang.String;", new ArrayRejectingClassLoader(CLASS_LOADER)))
                .isEqualTo(String[][].class);
    }

    @Test
    void getAllFieldsReturnsDeclaredAndInheritedFields() {
        List<Field> fields = Reflections.getAllFields(RawMessageIdImpl.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("ledgerId", "entryId", "batchIndex");
    }

    private static final class ArrayRejectingClassLoader extends ClassLoader {
        private ArrayRejectingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("[")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
