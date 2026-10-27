/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc_provider_azure;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.provider.azure.keyvault.AzureKeyVaultUriValidator;
import org.junit.jupiter.api.Test;

public class AzureKeyVaultUriValidatorTest {
    @Test
    void parsesVersionedSecretFromSovereignCloudVault() {
        AzureKeyVaultUriValidator.SecretReference secret =
                AzureKeyVaultUriValidator.parseSecretUri(
                        "https://Orders-Vault.vault.usgovcloudapi.net/"
                                + "secrets/database-password/version-7");

        assertThat(secret.getVaultUrl())
                .isEqualTo("https://orders-vault.vault.usgovcloudapi.net");
        assertThat(secret.getSecretName()).isEqualTo("database-password");
        assertThat(secret.getSecretVersion()).isEqualTo("version-7");
    }

    @Test
    void parsesCurrentSecretFromManagedHsm() {
        AzureKeyVaultUriValidator.SecretReference secret =
                AzureKeyVaultUriValidator.parseSecretUri(
                        "https://orders-hsm.managedhsm.azure.net/secrets/database-wallet");

        assertThat(secret.getVaultUrl()).isEqualTo("https://orders-hsm.managedhsm.azure.net");
        assertThat(secret.getSecretName()).isEqualTo("database-wallet");
        assertThat(secret.getSecretVersion()).isNull();
    }
}
