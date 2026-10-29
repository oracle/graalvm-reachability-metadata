/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeNotifyResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.common.remote.client.grpc.GrpcUtils;
import org.junit.jupiter.api.Test;

public class ConfigPayloadTest {
    @Test
    void preservesConfigRequestBodiesAndHeadersAcrossWireEncoding() throws Exception {
        ConfigQueryRequest query = ConfigQueryRequest.build("application", "jobs", "tenant");
        query.putHeader("source", "metadata-test");
        ConfigQueryRequest restoredQuery = (ConfigQueryRequest) GrpcUtils.parse(Payload.parseFrom(GrpcUtils.convert(query).toByteArray()));
        assertEquals("application", restoredQuery.getDataId());
        assertEquals("jobs", restoredQuery.getGroup());
        assertEquals("tenant", restoredQuery.getTenant());
        assertEquals("metadata-test", restoredQuery.getHeader("source"));
        assertEquals("metadata-test", query.getHeader("source"));

        ConfigPublishRequest publish = new ConfigPublishRequest("application", "jobs", "tenant", "replicas=3");
        publish.setCasMd5("expected-checksum");
        publish.putAdditionalParam("type", "properties");
        ConfigPublishRequest restoredPublish = (ConfigPublishRequest) GrpcUtils.parse(GrpcUtils.convert(publish));
        assertEquals("replicas=3", restoredPublish.getContent());
        assertEquals("expected-checksum", restoredPublish.getCasMd5());
        assertEquals("properties", restoredPublish.getAdditionParam("type"));

        ConfigRemoveRequest remove = new ConfigRemoveRequest("application", "jobs", "tenant", "release");
        ConfigRemoveRequest restoredRemove = (ConfigRemoveRequest) GrpcUtils.parse(GrpcUtils.convert(remove));
        assertEquals("application", restoredRemove.getDataId());
        assertEquals("release", restoredRemove.getTag());
    }

    @Test
    void preservesListenContextsAndChangeNotifications() {
        ConfigBatchListenRequest listen = new ConfigBatchListenRequest();
        listen.addConfigListenContext("jobs", "application", "tenant", "checksum");
        ConfigBatchListenRequest restored = (ConfigBatchListenRequest) GrpcUtils.parse(GrpcUtils.convert(listen));
        assertTrue(restored.isListen());
        assertEquals("checksum", restored.getConfigListenContexts().get(0).getMd5());
        assertEquals("application", restored.getConfigListenContexts().get(0).getDataId());

        ConfigChangeBatchListenResponse changes = new ConfigChangeBatchListenResponse();
        changes.addChangeConfig("application", "jobs", "tenant");
        ConfigChangeBatchListenResponse restoredChanges = (ConfigChangeBatchListenResponse) GrpcUtils.parse(GrpcUtils.convert(changes));
        assertEquals("application", restoredChanges.getChangedConfigs().get(0).getDataId());
        ConfigChangeNotifyRequest notification = ConfigChangeNotifyRequest.build("application", "jobs", "tenant");
        ConfigChangeNotifyRequest restoredNotification = (ConfigChangeNotifyRequest) GrpcUtils.parse(GrpcUtils.convert(notification));
        assertEquals("tenant", restoredNotification.getTenant());
        assertEquals("jobs", restoredNotification.getGroup());
    }

    @Test
    void preservesConfigResponsesAcrossWireEncoding() {
        ConfigQueryResponse query = ConfigQueryResponse.buildSuccessResponse("replicas=3");
        query.setMd5("checksum");
        query.setContentType("properties");
        query.setEncryptedDataKey("test-key");
        ConfigQueryResponse restored = (ConfigQueryResponse) GrpcUtils.parse(GrpcUtils.convert(query));
        assertTrue(restored.isSuccess());
        assertEquals("replicas=3", restored.getContent());
        assertEquals("checksum", restored.getMd5());
        assertEquals("properties", restored.getContentType());
        assertEquals("test-key", restored.getEncryptedDataKey());
        assertTrue(((ConfigPublishResponse) GrpcUtils.parse(GrpcUtils.convert(ConfigPublishResponse.buildSuccessResponse()))).isSuccess());
        assertTrue(((ConfigRemoveResponse) GrpcUtils.parse(GrpcUtils.convert(ConfigRemoveResponse.buildSuccessResponse()))).isSuccess());
        assertTrue(((ConfigChangeNotifyResponse) GrpcUtils.parse(GrpcUtils.convert(new ConfigChangeNotifyResponse()))).isSuccess());
    }
}
