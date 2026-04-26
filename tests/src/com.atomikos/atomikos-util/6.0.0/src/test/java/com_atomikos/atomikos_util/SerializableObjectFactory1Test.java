/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import com.atomikos.util.SerializableObjectFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.naming.BinaryRefAddr;
import javax.naming.Reference;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableObjectFactory1Test {

    private static final String MISSING_FIXTURE_CLASS_NAME = "missing.serializable.MissingFixture";
    private static final byte[] MISSING_FIXTURE_CLASS_BYTES = Base64.getDecoder().decode(
        "yv66vgAAAEUAHAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAIZmFsbGJhY2sJ"
            + "AAoACwcADAwADQAOAQAjbWlzc2luZy9zZXJpYWxpemFibGUvTWlzc2luZ0ZpeHR1cmUBAAV2YWx1ZQEAEkxqYXZhL2xhbmcv"
            + "U3RyaW5nOwcAEAEAFGphdmEvaW8vU2VyaWFsaXphYmxlAQAQc2VyaWFsVmVyc2lvblVJRAEAAUoBAA1Db25zdGFudFZhbHVl"
            + "BQAAAAAAAAABAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEACGdldFZhbHVlAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAApT"
            + "b3VyY2VGaWxlAQATTWlzc2luZ0ZpeHR1cmUuamF2YQAxAAoAAgABAA8AAgAaABEAEgABABMAAAACABQAEgANAA4AAAACAAEA"
            + "BQAGAAEAFgAAACsAAgABAAAACyq3AAEqEge1AAmxAAAAAQAXAAAADgADAAAACQAEAAoACgALAAEAGAAZAAEAFgAAAB0AAQAB"
            + "AAAABSq0AAmwAAAAAQAXAAAABgABAAAADgABABoAAAACABs="
    );
    private static final byte[] MISSING_FIXTURE_SERIALIZED_FORM = Base64.getDecoder().decode(
        "rO0ABXNyACNtaXNzaW5nLnNlcmlhbGl6YWJsZS5NaXNzaW5nRml4dHVyZQAAAAAAAAABAgABTAAFdmFsdWV0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQACGZhbGxiYWNr"
    );

    @Test
    void resolveClassFallsBackToTheThreadContextClassLoaderWhenDefaultLookupMisses() throws Exception {
        Reference reference = new Reference(MISSING_FIXTURE_CLASS_NAME);
        reference.add(new BinaryRefAddr("com.atomikos.serializable", MISSING_FIXTURE_SERIALIZED_FORM));

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        DefiningClassLoader definingClassLoader = new DefiningClassLoader(
            originalContextClassLoader,
            MISSING_FIXTURE_CLASS_NAME,
            MISSING_FIXTURE_CLASS_BYTES
        );

        Thread.currentThread().setContextClassLoader(definingClassLoader);
        try {
            Object restored = new SerializableObjectFactory().getObjectInstance(reference, null, null, null);

            assertThat(restored).isNotNull();
            assertThat(restored.getClass().getName()).isEqualTo(MISSING_FIXTURE_CLASS_NAME);
            assertThat(restored.getClass().getClassLoader()).isEqualTo(definingClassLoader);
            assertThat(definingClassLoader.requestedClassNames()).containsExactly(MISSING_FIXTURE_CLASS_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {

        private final String definedClassName;
        private final byte[] definedClassBytes;
        private final List<String> requestedClassNames = new ArrayList<>();

        private DefiningClassLoader(ClassLoader parent, String definedClassName, byte[] definedClassBytes) {
            super(parent);
            this.definedClassName = definedClassName;
            this.definedClassBytes = definedClassBytes;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (!this.definedClassName.equals(name)) {
                return super.loadClass(name);
            }

            this.requestedClassNames.add(name);
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }
            return defineClass(name, this.definedClassBytes, 0, this.definedClassBytes.length);
        }

        private List<String> requestedClassNames() {
            return this.requestedClassNames;
        }
    }
}
