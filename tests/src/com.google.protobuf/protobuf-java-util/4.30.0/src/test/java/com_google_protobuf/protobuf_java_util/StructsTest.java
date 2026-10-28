/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import com.google.common.truth.Truth;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class StructsTest {
    @Test
    public void test1pair_constructsObject() throws Exception {
        Struct.Builder expected = Struct.newBuilder();
        JsonFormat.parser().merge("{\"k1\": 1}", expected);
        Truth.assertThat(Structs.of("k1", Values.of(1))).isEqualTo(expected.build());
    }

    @Test
    public void test2pair_constructsObject() throws Exception {
        Struct.Builder expected = Struct.newBuilder();
        JsonFormat.parser().merge("{\"k1\": 1, \"k2\": 2}", expected);
        assertThat(Structs.of("k1", Values.of(1), "k2", Values.of(2))).isEqualTo(expected.build());
    }

    @Test
    public void test3pair_constructsObject() throws Exception {
        Struct.Builder expected = Struct.newBuilder();
        JsonFormat.parser().merge("{\"k1\": 1, \"k2\": 2, \"k3\": 3}", expected);
        assertThat(Structs.of("k1", Values.of(1), "k2", Values.of(2), "k3", Values.of(3)))
                .isEqualTo(expected.build());
    }
}
