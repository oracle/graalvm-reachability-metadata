/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_firestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ClientIdentifierTest {
    @Test
    void generatesClientUidWithCurrentProcessId() throws Throwable {
        Class<?> clientIdentifierClass =
                Class.forName("com.google.cloud.firestore.telemetry.ClientIdentifier");
        MethodHandle getClientUid =
                MethodHandles.privateLookupIn(clientIdentifierClass, MethodHandles.lookup())
                        .findStatic(
                                clientIdentifierClass,
                                "getClientUid",
                                MethodType.methodType(String.class));

        String clientUid = (String) getClientUid.invokeExact();

        String[] components = clientUid.split("@", 3);
        assertThat(components).hasSize(3);
        assertThat(UUID.fromString(components[0])).isNotNull();
        assertThat(components[1]).isEqualTo(Long.toString(ProcessHandle.current().pid()));
        assertThat(components[2]).isNotBlank();
    }
}
