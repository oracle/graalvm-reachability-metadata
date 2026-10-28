/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Gson's built-in JDK value adapters to import and publish a service registration.
public class ServiceRegistrationValueAdaptersTest {

    @Test
    @Timeout(60)
    public void resolvesRelativeCallbacksAndPreservesTypedRegistrationValues() {
        Gson gson = new Gson();
        ServiceRegistration registration = gson.fromJson("""
                {
                  "registrationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                  "apiBase": "https://api.example.invalid/v1/orders/",
                  "callback": "../callbacks/order%20ready?source=warehouse",
                  "healthCheck": "https://status.example.invalid:8443/health/ready"
                }
                """, ServiceRegistration.class);

        UUID registrationId = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        assertEquals(registrationId, registration.registrationId);
        assertEquals(URI.create("https://api.example.invalid/v1/orders/"), registration.apiBase);
        assertFalse(registration.callback.isAbsolute());
        assertEquals("https", registration.healthCheck.getProtocol());
        assertEquals("status.example.invalid", registration.healthCheck.getHost());
        assertEquals(8443, registration.healthCheck.getPort());
        assertEquals("/health/ready", registration.healthCheck.getPath());

        registration.callback = registration.apiBase.resolve(registration.callback);
        URI callback = URI.create("https://api.example.invalid/v1/callbacks/order%20ready?source=warehouse");
        assertEquals(callback, registration.callback);
        assertEquals("/v1/callbacks/order ready", registration.callback.getPath());

        String published = gson.toJson(registration);
        assertEquals(new JsonParser().parse("""
                {
                  "registrationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                  "apiBase": "https://api.example.invalid/v1/orders/",
                  "callback": "https://api.example.invalid/v1/callbacks/order%20ready?source=warehouse",
                  "healthCheck": "https://status.example.invalid:8443/health/ready"
                }
                """), new JsonParser().parse(published));

        ServiceRegistration restored = gson.fromJson(published, ServiceRegistration.class);
        assertEquals(registrationId, restored.registrationId);
        assertEquals(registration.apiBase, restored.apiBase);
        assertEquals(callback, restored.callback);
        assertEquals("https://status.example.invalid:8443/health/ready", restored.healthCheck.toExternalForm());
    }

    public static class ServiceRegistration {
        private UUID registrationId;
        private URI apiBase;
        private URI callback;
        private URL healthCheck;
    }
}
