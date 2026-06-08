/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.internal.authentication.AwsCredentialHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AwsCredentialHelperTest {
    @Test
    void initializesAwsCredentialProviderSelection() {
        assertThat(AwsCredentialHelper.LOGGER).isNotNull();
        AwsCredentialHelper.requireBuiltInProvider();
    }
}
