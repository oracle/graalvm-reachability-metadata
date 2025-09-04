/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast.customSerializer;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomSerializer implements StreamSerializer<CustomSerializable> {
    @Override
    public int getTypeId() {
        return 10;
    }

    @Override
    public void destroy() {
    }
    @Override
    public void write(ObjectDataOutput out, CustomSerializable object) throws IOException {
        byte[] bytes = object.value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    @Override
    public CustomSerializable read(ObjectDataInput in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new CustomSerializable(new String(bytes, StandardCharsets.UTF_8));
    }
}
