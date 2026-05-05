/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import org.junit.Test;
import protobuf_unittest.UnittestProto.NestedTestAllTypes;
import protobuf_unittest.UnittestProto.TestAllTypes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@SuppressWarnings("UnusedAssignment")
public class FieldMaskUtilTest {
    @Test
    public void testIsValid() {
        Truth.assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "nonexist")).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.optional_int32")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.repeated_int32")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.optional_nested_message")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.repeated_nested_message")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.nonexist")).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, FieldMaskUtil.fromString("payload"))).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, FieldMaskUtil.fromString("nonexist"))).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, FieldMaskUtil.fromString("payload,nonexist"))).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.getDescriptor(), "payload")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.getDescriptor(), "nonexist")).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.getDescriptor(), FieldMaskUtil.fromString("payload"))).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.getDescriptor(), FieldMaskUtil.fromString("nonexist"))).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.optional_nested_message.bb")).isTrue();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.repeated_nested_message.bb")).isFalse();
        assertThat(FieldMaskUtil.isValid(NestedTestAllTypes.class, "payload.optional_int32.bb")).isFalse();
    }

    @Test
    public void testToString() {
        assertThat(FieldMaskUtil.toString(FieldMask.getDefaultInstance())).isEmpty();
        FieldMask mask = FieldMask.newBuilder().addPaths("foo").build();
        assertThat(FieldMaskUtil.toString(mask)).isEqualTo("foo");
        mask = FieldMask.newBuilder().addPaths("foo").addPaths("bar").build();
        assertThat(FieldMaskUtil.toString(mask)).isEqualTo("foo,bar");
        mask = FieldMask.newBuilder().addPaths("").addPaths("foo").addPaths("").addPaths("bar").addPaths("").build();
        assertThat(FieldMaskUtil.toString(mask)).isEqualTo("foo,bar");
    }

    @Test
    public void testFromString() {
        FieldMask mask = FieldMaskUtil.fromString("");
        assertThat(mask.getPathsCount()).isEqualTo(0);
        mask = FieldMaskUtil.fromString("foo");
        assertThat(mask.getPathsCount()).isEqualTo(1);
        assertThat(mask.getPaths(0)).isEqualTo("foo");
        mask = FieldMaskUtil.fromString("foo,bar.baz");
        assertThat(mask.getPathsCount()).isEqualTo(2);
        assertThat(mask.getPaths(0)).isEqualTo("foo");
        assertThat(mask.getPaths(1)).isEqualTo("bar.baz");
        mask = FieldMaskUtil.fromString(",foo,,bar,");
        assertThat(mask.getPathsCount()).isEqualTo(2);
        assertThat(mask.getPaths(0)).isEqualTo("foo");
        assertThat(mask.getPaths(1)).isEqualTo("bar");
        mask = FieldMaskUtil.fromString(NestedTestAllTypes.class, ",payload");
        try {
            mask = FieldMaskUtil.fromString(NestedTestAllTypes.class, "payload,nonexist");
            assertWithMessage("Exception is expected.").fail();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testFromFieldNumbers() {
        FieldMask mask = FieldMaskUtil.fromFieldNumbers(TestAllTypes.class);
        assertThat(mask.getPathsCount()).isEqualTo(0);
        mask = FieldMaskUtil.fromFieldNumbers(TestAllTypes.class, TestAllTypes.OPTIONAL_INT32_FIELD_NUMBER);
        assertThat(mask.getPathsCount()).isEqualTo(1);
        assertThat(mask.getPaths(0)).isEqualTo("optional_int32");
        mask = FieldMaskUtil.fromFieldNumbers(
                TestAllTypes.class,
                TestAllTypes.OPTIONAL_INT32_FIELD_NUMBER,
                TestAllTypes.OPTIONAL_INT64_FIELD_NUMBER);
        assertThat(mask.getPathsCount()).isEqualTo(2);
        assertThat(mask.getPaths(0)).isEqualTo("optional_int32");
        assertThat(mask.getPaths(1)).isEqualTo("optional_int64");
        try {
            int invalidFieldNumber = 1000;
            mask = FieldMaskUtil.fromFieldNumbers(TestAllTypes.class, invalidFieldNumber);
            assertWithMessage("Exception is expected.").fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testToJsonString() {
        FieldMask mask = FieldMask.getDefaultInstance();
        assertThat(FieldMaskUtil.toJsonString(mask)).isEmpty();
        mask = FieldMask.newBuilder().addPaths("foo").build();
        assertThat(FieldMaskUtil.toJsonString(mask)).isEqualTo("foo");
        mask = FieldMask.newBuilder().addPaths("foo.bar_baz").addPaths("").build();
        assertThat(FieldMaskUtil.toJsonString(mask)).isEqualTo("foo.barBaz");
        mask = FieldMask.newBuilder().addPaths("foo").addPaths("bar_baz").build();
        assertThat(FieldMaskUtil.toJsonString(mask)).isEqualTo("foo,barBaz");
    }

    @Test
    public void testFromJsonString() {
        FieldMask mask = FieldMaskUtil.fromJsonString("");
        assertThat(mask.getPathsCount()).isEqualTo(0);
        mask = FieldMaskUtil.fromJsonString("foo");
        assertThat(mask.getPathsCount()).isEqualTo(1);
        assertThat(mask.getPaths(0)).isEqualTo("foo");
        mask = FieldMaskUtil.fromJsonString("foo.barBaz");
        assertThat(mask.getPathsCount()).isEqualTo(1);
        assertThat(mask.getPaths(0)).isEqualTo("foo.bar_baz");
        mask = FieldMaskUtil.fromJsonString("foo,barBaz");
        assertThat(mask.getPathsCount()).isEqualTo(2);
        assertThat(mask.getPaths(0)).isEqualTo("foo");
        assertThat(mask.getPaths(1)).isEqualTo("bar_baz");
    }

    @Test
    public void testFromStringList() {
        FieldMask mask = FieldMaskUtil.fromStringList(NestedTestAllTypes.class, ImmutableList.of("payload.repeated_nested_message", "child"));
        assertThat(mask).isEqualTo(FieldMask.newBuilder()
                .addPaths("payload.repeated_nested_message")
                .addPaths("child")
                .build());
        mask = FieldMaskUtil.fromStringList(NestedTestAllTypes.getDescriptor(), ImmutableList.of("payload.repeated_nested_message", "child"));
        assertThat(mask).isEqualTo(FieldMask.newBuilder().addPaths("payload.repeated_nested_message").addPaths("child").build());
        mask = FieldMaskUtil.fromStringList(ImmutableList.of("payload.repeated_nested_message", "child"));
        assertThat(mask).isEqualTo(FieldMask.newBuilder()
                .addPaths("payload.repeated_nested_message")
                .addPaths("child")
                .build());
    }

    @Test
    public void testUnion() {
        FieldMask mask1 = FieldMaskUtil.fromString("foo,bar.baz,bar.quz");
        FieldMask mask2 = FieldMaskUtil.fromString("foo.bar,bar");
        FieldMask result = FieldMaskUtil.union(mask1, mask2);
        assertThat(FieldMaskUtil.toString(result)).isEqualTo("bar,foo");
    }

    @Test
    public void testUnion_usingVarArgs() {
        FieldMask mask1 = FieldMaskUtil.fromString("foo");
        FieldMask mask2 = FieldMaskUtil.fromString("foo.bar,bar.quz");
        FieldMask mask3 = FieldMaskUtil.fromString("bar.quz");
        FieldMask mask4 = FieldMaskUtil.fromString("bar");
        FieldMask result = FieldMaskUtil.union(mask1, mask2, mask3, mask4);
        assertThat(FieldMaskUtil.toString(result)).isEqualTo("bar,foo");
    }

    @Test
    public void testSubstract() {
        FieldMask mask1 = FieldMaskUtil.fromString("foo,bar.baz,bar.quz");
        FieldMask mask2 = FieldMaskUtil.fromString("foo.bar,bar");
        FieldMask result = FieldMaskUtil.subtract(mask1, mask2);
        assertThat(FieldMaskUtil.toString(result)).isEqualTo("foo");
    }

    @Test
    public void testSubstract_usingVarArgs() {
        FieldMask mask1 = FieldMaskUtil.fromString("foo,bar.baz,bar.quz.bar");
        FieldMask mask2 = FieldMaskUtil.fromString("foo.bar,bar.baz.quz");
        FieldMask mask3 = FieldMaskUtil.fromString("bar.quz");
        FieldMask mask4 = FieldMaskUtil.fromString("foo,bar.baz");
        FieldMask result = FieldMaskUtil.subtract(mask1, mask2, mask3, mask4);
        assertThat(FieldMaskUtil.toString(result)).isEmpty();
    }

    @Test
    public void testIntersection() {
        FieldMask mask1 = FieldMaskUtil.fromString("foo,bar.baz,bar.quz");
        FieldMask mask2 = FieldMaskUtil.fromString("foo.bar,bar");
        FieldMask result = FieldMaskUtil.intersection(mask1, mask2);
        assertThat(FieldMaskUtil.toString(result)).isEqualTo("bar.baz,bar.quz,foo.bar");
    }

    @Test
    public void testMerge() {
        NestedTestAllTypes source = NestedTestAllTypes.newBuilder().setPayload(TestAllTypes.newBuilder().setOptionalInt32(1234)).build();
        NestedTestAllTypes.Builder builder = NestedTestAllTypes.newBuilder();
        FieldMaskUtil.merge(FieldMaskUtil.fromString("payload"), source, builder);
        assertThat(builder.getPayload().getOptionalInt32()).isEqualTo(1234);
    }

    @Test
    public void testTrim() {
        NestedTestAllTypes source = NestedTestAllTypes.newBuilder().setPayload(TestAllTypes.newBuilder()
                                .setOptionalInt32(1234)
                                .setOptionalString("1234")
                                .setOptionalBool(true))
                .build();
        FieldMask mask = FieldMaskUtil.fromStringList(ImmutableList.of("payload.optional_int32", "payload.optional_string"));
        NestedTestAllTypes actual = FieldMaskUtil.trim(mask, source);
        assertThat(actual).isEqualTo(NestedTestAllTypes.newBuilder().setPayload(TestAllTypes.newBuilder().setOptionalInt32(1234).setOptionalString("1234"))
                .build());
    }
}
