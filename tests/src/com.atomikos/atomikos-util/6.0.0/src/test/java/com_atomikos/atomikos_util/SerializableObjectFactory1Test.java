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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SerializableObjectFactory1Test {

    private static final String MISSING_FIXTURE_CLASS_NAME = "missing.serializable.MissingFixture";
    private static final byte[] MISSING_FIXTURE_SERIALIZED_FORM = Base64.getDecoder().decode(
        "rO0ABXNyACNtaXNzaW5nLnNlcmlhbGl6YWJsZS5NaXNzaW5nRml4dHVyZQAAAAAAAAABAgABTAAFdmFsdWV0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQACGZhbGxiYWNr"
    );

    @Test
    void resolveClassFallsBackToTheThreadContextClassLoaderWhenDefaultLookupMisses() throws Exception {
        Reference reference = new Reference(MISSING_FIXTURE_CLASS_NAME);
        reference.add(new BinaryRefAddr("com.atomikos.serializable", MISSING_FIXTURE_SERIALIZED_FORM));

        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        RejectingClassLoader rejectingClassLoader = new RejectingClassLoader(
            originalContextClassLoader,
            MISSING_FIXTURE_CLASS_NAME
        );

        Thread.currentThread().setContextClassLoader(rejectingClassLoader);
        try {
            assertThatThrownBy(() -> new SerializableObjectFactory().getObjectInstance(reference, null, null, null))
                .isInstanceOf(ClassNotFoundException.class)
                .hasMessage(MISSING_FIXTURE_CLASS_NAME);

            assertThat(rejectingClassLoader.requestedClassNames()).containsExactly(MISSING_FIXTURE_CLASS_NAME);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {

        private final String rejectedClassName;
        private final List<String> requestedClassNames = new ArrayList<>();

        private RejectingClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (this.rejectedClassName.equals(name)) {
                this.requestedClassNames.add(name);
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        private List<String> requestedClassNames() {
            return this.requestedClassNames;
        }
    }
}
