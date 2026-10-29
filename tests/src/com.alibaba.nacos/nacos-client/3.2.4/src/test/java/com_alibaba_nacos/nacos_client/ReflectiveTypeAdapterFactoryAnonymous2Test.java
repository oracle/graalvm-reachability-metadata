/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.gson.Gson;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryAnonymous2Test {
    public record Endpoint(String host, int port) {
    }

    @Test
    void readsAndWritesFieldsAndRecordAccessors() {
        Gson gson = new Gson();
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        Instance restored = gson.fromJson(gson.toJson(instance), Instance.class);
        assertEquals(instance.getIp(), restored.getIp());
        assertEquals(8080, restored.getPort());
        Endpoint endpoint = new Endpoint("localhost", 8080);
        assertEquals(endpoint, gson.fromJson(gson.toJson(endpoint), Endpoint.class));
    }
}
