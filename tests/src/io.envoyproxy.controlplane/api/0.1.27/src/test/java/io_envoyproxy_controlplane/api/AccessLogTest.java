/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.accesslog.v3.AccessLog;
import io.envoyproxy.envoy.extensions.access_loggers.file.v3.FileAccessLog;
import org.junit.jupiter.api.Test;

public class AccessLogTest {
    @Test
    void roundTripsTypedFileLoggerConfiguration() throws Exception {
        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder()
                .add(FileAccessLog.getDescriptor()).build();
        AccessLog.Builder builder = AccessLog.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge("""
                {
                  "name": "envoy.access_loggers.file",
                  "typedConfig": {
                    "@type": "type.googleapis.com/envoy.extensions.access_loggers.file.v3.FileAccessLog",
                    "path": "access.log",
                    "logFormat": {"textFormat": "%RESPONSE_CODE% %REQ(:PATH)%"}
                  }
                }
                """, builder);
        AccessLog log = builder.build();
        assertEquals("envoy.access_loggers.file", log.getName());
        FileAccessLog config = log.getTypedConfig().unpack(FileAccessLog.class);
        assertEquals("access.log", config.getPath());
        assertEquals("%RESPONSE_CODE% %REQ(:PATH)%", config.getLogFormat().getTextFormat());

        AccessLog.Builder copy = AccessLog.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(log), copy);
        assertEquals(log, copy.build());
    }
}
