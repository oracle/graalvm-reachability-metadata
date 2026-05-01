/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.hadoop.thirdparty.protobuf.Any;
import org.apache.hadoop.thirdparty.protobuf.ByteString;
import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

public class GeneratedMessageV3Test {
    private static final String TYPE_URL_FIELD_NAME = "type_url";
    private static final String VALUE_FIELD_NAME = "value";
    private static final String TYPE_URL = "type.googleapis.com/coverage.Message";
    private static final ByteString VALUE = ByteString.copyFromUtf8("payload");

    @Test
    void generatedMessageV3FieldAccessorsUseGeneratedMethods() {
        Descriptors.Descriptor descriptor = Any.getDescriptor();
        Descriptors.FieldDescriptor typeUrlField = descriptor.findFieldByName(TYPE_URL_FIELD_NAME);
        Descriptors.FieldDescriptor valueField = descriptor.findFieldByName(VALUE_FIELD_NAME);
        Any message = Any.newBuilder()
                .setTypeUrl(TYPE_URL)
                .setValue(VALUE)
                .build();

        assertThat(message.hasField(typeUrlField)).isTrue();
        assertThat(message.getField(typeUrlField)).isEqualTo(TYPE_URL);
        assertThat(message.hasField(valueField)).isTrue();
        assertThat(message.getField(valueField)).isEqualTo(VALUE);

        Map<Descriptors.FieldDescriptor, Object> allFields = message.getAllFields();
        assertThat(allFields)
                .containsEntry(typeUrlField, TYPE_URL)
                .containsEntry(valueField, VALUE);
    }

    @Test
    void generatedMessageV3BuilderFieldAccessorsUseGeneratedMethods() {
        Descriptors.Descriptor descriptor = Any.getDescriptor();
        Descriptors.FieldDescriptor typeUrlField = descriptor.findFieldByName(TYPE_URL_FIELD_NAME);
        Descriptors.FieldDescriptor valueField = descriptor.findFieldByName(VALUE_FIELD_NAME);
        Any.Builder builder = Any.newBuilder();

        builder.setField(typeUrlField, TYPE_URL);
        builder.setField(valueField, VALUE);

        assertThat(builder.hasField(typeUrlField)).isTrue();
        assertThat(builder.getField(typeUrlField)).isEqualTo(TYPE_URL);
        assertThat(builder.hasField(valueField)).isTrue();
        assertThat(builder.getField(valueField)).isEqualTo(VALUE);
        assertThat(builder.build().getAllFields())
                .containsEntry(typeUrlField, TYPE_URL)
                .containsEntry(valueField, VALUE);
    }
}
