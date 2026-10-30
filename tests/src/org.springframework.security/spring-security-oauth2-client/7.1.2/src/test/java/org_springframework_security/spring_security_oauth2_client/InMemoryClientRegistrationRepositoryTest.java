/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_client;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class InMemoryClientRegistrationRepositoryTest {

    @Test
    void findsAndEnumeratesConfiguredRegistrations() {
        ClientRegistration orders = OAuth2ClientTestSupport.registration("orders");
        ClientRegistration inventory = OAuth2ClientTestSupport.registration("inventory");
        InMemoryClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(orders, inventory);

        assertThat(repository.findByRegistrationId("orders")).isSameAs(orders);
        assertThat(repository.findByRegistrationId("inventory")).isSameAs(inventory);
        assertThat(repository.findByRegistrationId("unknown")).isNull();
        assertThat(repository).containsExactlyInAnyOrder(orders, inventory);
    }

    @Test
    void rejectsDuplicateRegistrationIdentifiers() {
        ClientRegistration first = OAuth2ClientTestSupport.registration("orders");
        ClientRegistration second = ClientRegistration.withClientRegistration(first).clientId("other-client").build();

        assertThatIllegalStateException().isThrownBy(() -> new InMemoryClientRegistrationRepository(first, second));
    }
}
