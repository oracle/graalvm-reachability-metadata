/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_auth_plugin;

import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestResourceTest {
    @Test
    void buildsResourcesForEachSupportedRequestType() {
        assertResource(RequestResource.namingBuilder(), SignType.NAMING, "catalog-service");
        assertResource(RequestResource.configBuilder(), SignType.CONFIG, "application.yaml");
        assertResource(RequestResource.lockBuilder(), SignType.LOCK, "update-lock");
        assertResource(RequestResource.aiBuilder(), SignType.AI, "assistant");
    }

    @Test
    void reusingABuilderDoesNotChangePreviouslyBuiltResources() {
        RequestResource.Builder builder = RequestResource.configBuilder()
                .setNamespace("tenant-a").setGroup("application").setResource("first.yaml");
        RequestResource first = builder.build();
        RequestResource second = builder.setResource("second.yaml").build();

        assertEquals("first.yaml", first.getResource());
        assertEquals("second.yaml", second.getResource());
        assertEquals(first.getNamespace(), second.getNamespace());
        assertEquals(first.getGroup(), second.getGroup());
        assertEquals(SignType.CONFIG, second.getType());
    }

    private static void assertResource(RequestResource.Builder builder, String type, String name) {
        RequestResource resource = builder.setNamespace("tenant-a")
                .setGroup("application").setResource(name).build();

        assertEquals(type, resource.getType());
        assertEquals("tenant-a", resource.getNamespace());
        assertEquals("application", resource.getGroup());
        assertEquals(name, resource.getResource());
    }
}
