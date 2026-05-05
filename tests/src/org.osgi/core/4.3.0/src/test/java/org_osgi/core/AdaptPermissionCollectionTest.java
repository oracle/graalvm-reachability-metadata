/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.PermissionCollection;

import org.junit.jupiter.api.Test;
import org.osgi.framework.AdaptPermission;

public class AdaptPermissionCollectionTest {
    @Test
    void newPermissionCollectionCreatesEmptyAdaptPermissionCollection() {
        PermissionCollection collection = createPermissionCollection();

        assertThat(collection.getClass().getName())
                .isEqualTo("org.osgi.framework.AdaptPermissionCollection");
        assertThat(collection.elements().hasMoreElements()).isFalse();
    }

    @Test
    void serializedPermissionCollectionWritesAddedPermissions() throws IOException {
        PermissionCollection collection = createPermissionCollection();
        collection.add(new AdaptPermission("*", AdaptPermission.ADAPT));

        byte[] serializedCollection = serialize(collection);

        assertThat(serializedCollection).isNotEmpty();
    }

    private static PermissionCollection createPermissionCollection() {
        return new AdaptPermission("*", AdaptPermission.ADAPT).newPermissionCollection();
    }

    private byte[] serialize(PermissionCollection collection) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(collection);
        }
        return output.toByteArray();
    }
}
