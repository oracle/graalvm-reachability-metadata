/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

class JsonModelApiTest {
    @Test
    void arraysSupportOrderedMutationIterationAndScalarViews() {
        JsonArray array = new JsonArray();
        assertThat(array.size()).isZero();
        array.add(new JsonPrimitive("7"));
        array.add(null);
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.contains(JsonNull.INSTANCE)).isTrue();
        assertThat(array.get(0).getAsString()).isEqualTo("7");

        JsonArray extra = new JsonArray();
        extra.add(new JsonPrimitive("8"));
        array.addAll(extra);
        assertThat(array.remove(JsonNull.INSTANCE)).isTrue();
        assertThat(array.remove(1)).isEqualTo(new JsonPrimitive("8"));
        assertThat(array.set(0, new JsonPrimitive("42"))).isEqualTo(new JsonPrimitive("7"));

        Iterator<JsonElement> iterator = array.iterator();
        assertThat(iterator.next().getAsInt()).isEqualTo(42);
        assertThat(iterator.hasNext()).isFalse();

        JsonArray scalar = new JsonArray();
        scalar.add(new JsonPrimitive("12"));
        assertThat(scalar.getAsBigDecimal()).isEqualTo(new BigDecimal("12"));
        assertThat(scalar.getAsBigInteger()).isEqualTo(new BigInteger("12"));
        assertThat(scalar.getAsByte()).isEqualTo((byte) 12);
        assertThat(scalar.getAsCharacter()).isEqualTo('1');
        assertThat(scalar.getAsDouble()).isEqualTo(12d);
        assertThat(scalar.getAsFloat()).isEqualTo(12f);
        assertThat(scalar.getAsInt()).isEqualTo(12);
        assertThat(scalar.getAsLong()).isEqualTo(12L);
        assertThat(scalar.getAsBoolean()).isFalse();
        assertThat(scalar.getAsNumber().doubleValue()).isEqualTo(12d);
        assertThat(scalar.getAsShort()).isEqualTo((short) 12);
        assertThat(scalar.getAsString()).isEqualTo("12");
        JsonArray multiple = new JsonArray();
        multiple.add(new JsonPrimitive(1));
        multiple.add(new JsonPrimitive(2));
        assertThatThrownBy(multiple::getAsInt).isInstanceOf(IllegalStateException.class);

        JsonArray same = new JsonArray();
        same.add(new JsonPrimitive("42"));
        assertThat(array).isEqualTo(same);
        assertThat(array.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void objectsExposeTypedMembersAndStableEquality() {
        JsonObject object = new JsonObject();
        object.addProperty("name", "Ada");
        object.addProperty("age", 37);
        object.addProperty("active", Boolean.TRUE);
        object.addProperty("initial", Character.valueOf('A'));
        object.add("tags", new JsonArray());
        object.addProperty("missing", (String) null);

        assertThat(object.has("name")).isTrue();
        assertThat(object.get("name").getAsString()).isEqualTo("Ada");
        assertThat(object.getAsJsonPrimitive("age").getAsInt()).isEqualTo(37);
        assertThat(object.getAsJsonArray("tags").size()).isZero();
        assertThat(object.getAsJsonObject("nested")).isNull();
        assertThat(object.entrySet()).extracting("key")
                .containsExactly("name", "age", "active", "initial", "tags", "missing");
        assertThat(object.remove("missing")).isEqualTo(JsonNull.INSTANCE);
        assertThat(object.remove("unknown")).isNull();

        JsonObject copy = new JsonObject();
        copy.addProperty("name", "Ada");
        copy.addProperty("age", 37);
        copy.addProperty("active", Boolean.TRUE);
        copy.addProperty("initial", Character.valueOf('A'));
        copy.add("tags", new JsonArray());
        assertThat(object).isEqualTo(copy);
        assertThat(object.hashCode()).isEqualTo(copy.hashCode());
    }

    @Test
    void objectRemovalRebalancesBothAdjacentSubtreeDirections() {
        JsonObject removeFirst = new JsonObject();
        addNumericTreeKeys(removeFirst, new int[] {4, 2, 6, 1, 3, 5, 7, 8, 9});
        assertThat(removeFirst.remove("4").getAsInt()).isEqualTo(4);

        JsonObject removeLast = new JsonObject();
        addNumericTreeKeys(removeLast, new int[] {4, 2, 6, 1, 3, 5, 0});
        assertThat(removeLast.remove("4").getAsInt()).isEqualTo(4);
    }

    private static void addNumericTreeKeys(JsonObject object, int[] keys) {
        for (int key : keys) {
            object.addProperty(String.valueOf(key), key);
        }
    }

    @Test
    void primitivesAndElementKindsProvideUsefulConversions() {
        JsonElement abstractElement = new JsonElement() {
            @Override JsonElement deepCopy() {
                return this;
            }
        };
        assertThat(abstractElement.isJsonArray()).isFalse();
        assertThat(abstractElement.isJsonObject()).isFalse();
        assertThat(abstractElement.isJsonPrimitive()).isFalse();
        assertThat(abstractElement.isJsonNull()).isFalse();
        assertThatThrownBy(abstractElement::getAsJsonObject).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(abstractElement::getAsJsonArray).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(abstractElement::getAsJsonPrimitive).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(abstractElement::getAsJsonNull).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(abstractElement::getAsBoolean).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsBigDecimal).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsBigInteger).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsByte).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsCharacter).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsDouble).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsFloat).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsInt).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsLong).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsNumber).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsShort).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(abstractElement::getAsString).isInstanceOf(UnsupportedOperationException.class);

        JsonPrimitive booleanValue = new JsonPrimitive(Boolean.TRUE);
        JsonPrimitive characterValue = new JsonPrimitive(Character.valueOf('x'));
        JsonPrimitive numberValue = new JsonPrimitive(Integer.valueOf(9));
        JsonPrimitive stringValue = new JsonPrimitive("123");
        assertThat(booleanValue.isBoolean()).isTrue();
        assertThat(booleanValue.getAsBoolean()).isTrue();
        assertThat(characterValue.isString()).isTrue();
        assertThat(characterValue.getAsCharacter()).isEqualTo('x');
        assertThat(numberValue.isNumber()).isTrue();
        assertThat(numberValue.getAsNumber()).isEqualTo(9);
        assertThat(numberValue.getAsBigDecimal()).isEqualTo(new BigDecimal("9"));
        assertThat(numberValue.getAsBigInteger()).isEqualTo(BigInteger.valueOf(9));
        assertThat(numberValue.getAsByte()).isEqualTo((byte) 9);
        assertThat(numberValue.getAsDouble()).isEqualTo(9d);
        assertThat(numberValue.getAsFloat()).isEqualTo(9f);
        assertThat(numberValue.getAsInt()).isEqualTo(9);
        assertThat(numberValue.getAsLong()).isEqualTo(9L);
        assertThat(numberValue.getAsShort()).isEqualTo((short) 9);
        assertThat(stringValue.getAsBoolean()).isFalse();
        assertThat(stringValue.getAsInt()).isEqualTo(123);
        assertThat(stringValue.getAsLong()).isEqualTo(123L);
        assertThat(stringValue.getAsShort()).isEqualTo((short) 123);
        assertThat(stringValue.getAsByte()).isEqualTo((byte) 123);
        assertThat(stringValue.getAsDouble()).isEqualTo(123d);
        assertThat(stringValue.getAsFloat()).isEqualTo(123f);
        assertThat(stringValue.getAsBigDecimal()).isEqualTo(new BigDecimal("123"));
        assertThat(stringValue.getAsBigInteger()).isEqualTo(new BigInteger("123"));
        assertThat(stringValue.getAsString()).isEqualTo("123");
        assertThat(stringValue.isNumber()).isFalse();
        assertThat(stringValue.isString()).isTrue();
        assertThat(numberValue).isEqualTo(new JsonPrimitive(9L));
        assertThat(numberValue.hashCode()).isEqualTo(new JsonPrimitive(9L).hashCode());

        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        JsonNull jsonNull = new JsonNull();
        object.add("value", stringValue);
        array.add(object);
        assertThat(object.isJsonObject()).isTrue();
        assertThat(object.getAsJsonObject()).isSameAs(object);
        assertThat(array.isJsonArray()).isTrue();
        assertThat(array.getAsJsonArray()).isSameAs(array);
        assertThat(stringValue.isJsonPrimitive()).isTrue();
        assertThat(stringValue.getAsJsonPrimitive()).isSameAs(stringValue);
        assertThat(jsonNull.isJsonNull()).isTrue();
        assertThat(jsonNull.getAsJsonNull()).isSameAs(jsonNull);
        assertThat(jsonNull).isEqualTo(new JsonNull());
        assertThat(jsonNull.hashCode()).isEqualTo(JsonNull.INSTANCE.hashCode());
        assertThat(object.toString()).contains("value");
    }

    @Test
    void enumsAndJsonNullHaveNormalJavaValueSemantics() {
        assertThat(FieldNamingPolicy.values()).contains(FieldNamingPolicy.IDENTITY);
        assertThat(FieldNamingPolicy.valueOf("UPPER_CAMEL_CASE"))
                .isEqualTo(FieldNamingPolicy.UPPER_CAMEL_CASE);
        assertThat(LongSerializationPolicy.values()).contains(LongSerializationPolicy.STRING);
        assertThat(LongSerializationPolicy.valueOf("DEFAULT"))
                .isEqualTo(LongSerializationPolicy.DEFAULT);
        assertThat(com.google.gson.stream.JsonToken.values()).contains(com.google.gson.stream.JsonToken.NULL);
        assertThat(com.google.gson.stream.JsonToken.valueOf("BEGIN_OBJECT"))
                .isEqualTo(com.google.gson.stream.JsonToken.BEGIN_OBJECT);
        assertThat(new ArrayList<>(Arrays.asList("one", "two"))).containsExactly("one", "two");
    }
}
