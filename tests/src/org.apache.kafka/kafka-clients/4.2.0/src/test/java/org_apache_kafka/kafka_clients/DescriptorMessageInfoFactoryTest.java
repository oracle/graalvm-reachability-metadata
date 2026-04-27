/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.ListValue;
import org.apache.kafka.shaded.com.google.protobuf.Struct;
import org.apache.kafka.shaded.com.google.protobuf.Value;
import org.junit.jupiter.api.Test;

public class DescriptorMessageInfoFactoryTest {

    @Test
    void parsingValueWithMessageBackedOneofRoundTripsListAndStructKinds() throws Exception {
        ListValue listValue = ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("alpha").build())
            .addValues(Value.newBuilder().setBoolValue(true).build())
            .build();
        Value listBackedValue = Value.newBuilder()
            .setListValue(listValue)
            .build();

        Value parsedListBackedValue = Value.parseFrom(listBackedValue.toByteArray());

        assertThat(parsedListBackedValue.getKindCase()).isEqualTo(Value.KindCase.LIST_VALUE);
        assertThat(parsedListBackedValue.getListValue().getValuesList())
            .extracting(Value::getKindCase)
            .containsExactly(Value.KindCase.STRING_VALUE, Value.KindCase.BOOL_VALUE);

        Struct structValue = Struct.newBuilder()
            .putFields("child", Value.newBuilder().setNumberValue(7.5d).build())
            .build();
        Value structBackedValue = Value.newBuilder()
            .setStructValue(structValue)
            .build();

        Value parsedStructBackedValue = Value.parseFrom(structBackedValue.toByteArray());

        assertThat(parsedStructBackedValue.getKindCase()).isEqualTo(Value.KindCase.STRUCT_VALUE);
        assertThat(parsedStructBackedValue.getStructValue().getFieldsMap())
            .containsOnlyKeys("child");
        assertThat(parsedStructBackedValue.getStructValue().getFieldsOrThrow("child").getNumberValue())
            .isEqualTo(7.5d);
    }

    @Test
    void parsingListValueRoundTripsRepeatedMessageEntries() throws Exception {
        ListValue original = ListValue.newBuilder()
            .addValues(Value.newBuilder().setStringValue("first").build())
            .addValues(
                Value.newBuilder()
                    .setStructValue(
                        Struct.newBuilder()
                            .putFields("flag", Value.newBuilder().setBoolValue(true).build())
                            .build()
                    )
                    .build()
            )
            .build();

        ListValue parsed = ListValue.parseFrom(original.toByteArray());

        assertThat(parsed.getValuesCount()).isEqualTo(2);
        assertThat(parsed.getValues(0).getStringValue()).isEqualTo("first");
        assertThat(parsed.getValues(1).getStructValue().getFieldsOrThrow("flag").getBoolValue()).isTrue();
    }
}
