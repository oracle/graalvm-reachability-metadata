/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Providers;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResteasyProviderFactoryImplTest {
    @Test
    void constructsConfiguredInjectorFactory() throws Exception {
        ResteasyProviderFactoryImpl factory = new ResteasyProviderFactoryImpl();
        factory.addInjectorFactory(InjectorFactoryImpl.class);
        CreatedService service = factory.injectedInstance(CreatedService.class);
        assertThat(service.greeting()).isEqualTo("created");
    }

    @Test
    void createsMultipartEntityPartBuilderFromServiceProvider() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            ResteasyContext.pushContext(Providers.class, session.dispatcher.getProviderFactory());
            EntityPart part = session.dispatcher.getProviderFactory().createEntityPartBuilder("message")
                    .mediaType(MediaType.TEXT_PLAIN_TYPE).content("multipart-text").build();
            assertThat(part.getName()).isEqualTo("message");
            assertThat(part.getMediaType()).isEqualTo(MediaType.TEXT_PLAIN_TYPE);
            try (InputStream content = part.getContent()) {
                assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("multipart-text");
            }
        }
    }

    public static class CreatedService {
        public String greeting() {
            return "created";
        }
    }
}
