/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clearspring_analytics.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import com.clearspring.analytics.stream.membership.BloomFilter;
import com.clearspring.analytics.stream.membership.DataInputBuffer;
import com.clearspring.analytics.stream.membership.DataOutputBuffer;
import com.clearspring.analytics.stream.membership.Filter;
import com.clearspring.analytics.stream.membership.ICompactSerializer;

public class FilterTest {
    @Test
    void getSerializerResolvesTheConcreteBloomFilterSerializer() throws Throwable {
        BloomFilter original = new BloomFilter(128, 0.01);
        original.add("alpha");

        ICompactSerializer<Filter> serializer = serializerFor(original);
        DataOutputBuffer output = new DataOutputBuffer();
        serializer.serialize(original, output);
        output.close();

        DataInputBuffer input = new DataInputBuffer();
        input.reset(output.getData(), output.getLength());
        Filter deserialized = serializer.deserialize(input);
        input.close();

        assertThat(deserialized).isInstanceOf(BloomFilter.class);
        assertThat(deserialized.isPresent("alpha")).isTrue();
        assertThat(deserialized.isPresent("omega")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static ICompactSerializer<Filter> serializerFor(Filter filter) throws Throwable {
        MethodHandle getSerializer = MethodHandles.privateLookupIn(Filter.class, MethodHandles.lookup())
                .findVirtual(Filter.class, "getSerializer", MethodType.methodType(ICompactSerializer.class));
        return (ICompactSerializer<Filter>) getSerializer.invoke(filter);
    }
}
