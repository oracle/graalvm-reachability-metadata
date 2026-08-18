/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.DelegatingSerializationFilter;

public class DelegatingSerializationFilterTest {
    @Test
    void buildsFilterPatternWithAllowedClassesAndPatterns() {
        String filterPattern = DelegatingSerializationFilter.builder()
                .addAllowedClass(Integer.class)
                .addAllowedPattern("com.example.safe.*")
                .toString();

        assertThat(filterPattern)
                .contains(Integer.class.getName() + ";", "com.example.safe.*;", "java.util.*;")
                .endsWith("!*");
    }

    @Test
    void installsSerializationFilterWhenObjectInputStreamHasNone() throws IOException {
        try (ObjectInputStream objectInputStream = newObjectInputStream()) {
            assertThat(objectInputStream.getObjectInputFilter()).isNull();

            DelegatingSerializationFilter.builder()
                    .addAllowedClass(String.class)
                    .setFilter(objectInputStream);

            assertThat(objectInputStream.getObjectInputFilter()).isNotNull();
        }
    }

    @Test
    void keepsExistingSerializationFilter() throws IOException {
        try (ObjectInputStream objectInputStream = newObjectInputStream()) {
            ObjectInputFilter existingFilter = new ExistingFilter();
            objectInputStream.setObjectInputFilter(existingFilter);

            DelegatingSerializationFilter.builder()
                    .addAllowedClass(String.class)
                    .setFilter(objectInputStream);

            assertThat(objectInputStream.getObjectInputFilter()).isSameAs(existingFilter);
        }
    }

    private static ObjectInputStream newObjectInputStream() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            objectOutputStream.flush();
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static final class ExistingFilter implements ObjectInputFilter {
        @Override
        public Status checkInput(FilterInfo filterInfo) {
            return Status.ALLOWED;
        }
    }
}
