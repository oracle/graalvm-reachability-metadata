/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.serialize.ObjectInput;
import com.alibaba.dubbo.common.serialize.ObjectOutput;
import com.alibaba.dubbo.common.serialize.Serialization;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

final class SerializationTestSupport {
    private SerializationTestSupport() { }

    static Object roundTrip(String extension, Object value) throws Exception {
        Serialization serialization =
                ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(extension);
        URL url = new URL("test", "127.0.0.1", 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutput output = serialization.serialize(url, bytes);
        output.writeObject(value);
        output.flushBuffer();
        ObjectInput input =
                serialization.deserialize(url, new ByteArrayInputStream(bytes.toByteArray()));
        return input.readObject();
    }
}
