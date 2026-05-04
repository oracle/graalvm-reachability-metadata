/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.serialization.ZipSerializableView;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;

public class ZipSerializableViewImplTest {
    private static final String ARCHIVE_NAME = "serializable-view.jar";
    private static final String RESOURCE_PATH = "/payload/message.txt";
    private static final String RESOURCE_CONTENT = "serialized archive payload";

    @Test
    void objectSerializationRoundTripPreservesArchiveContentsAndId() throws Exception {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME)
            .add(new StringAsset(RESOURCE_CONTENT), RESOURCE_PATH);
        String originalId = archive.getId();
        ZipSerializableView serializableView = archive.as(ZipSerializableView.class);

        ZipSerializableView deserializedView = roundTrip(serializableView);

        JavaArchive deserializedArchive = deserializedView.as(JavaArchive.class);
        assertThat(deserializedArchive.getName()).isEqualTo(ARCHIVE_NAME);
        assertThat(deserializedArchive.getId()).isEqualTo(originalId);
        assertThat(readAsset(deserializedArchive.get(RESOURCE_PATH))).isEqualTo(RESOURCE_CONTENT);
    }

    private ZipSerializableView roundTrip(ZipSerializableView serializableView) throws IOException,
        ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(serializableView);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Object deserialized = input.readObject();
            assertThat(deserialized).isInstanceOf(ZipSerializableView.class);
            return (ZipSerializableView) deserialized;
        }
    }

    private String readAsset(Node node) throws IOException {
        assertThat(node).isNotNull();
        try (InputStream input = node.getAsset().openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
