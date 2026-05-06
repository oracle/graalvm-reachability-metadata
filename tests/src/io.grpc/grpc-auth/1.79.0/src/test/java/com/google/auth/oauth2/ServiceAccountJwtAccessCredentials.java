/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.auth.oauth2;

import com.google.auth.Credentials;
import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ServiceAccountJwtAccessCredentials extends Credentials {
    private final String clientId;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String privateKeyId;
    private final String quotaProjectId;

    private ServiceAccountJwtAccessCredentials(Builder builder) {
        this.clientId = builder.clientId;
        this.clientEmail = builder.clientEmail;
        this.privateKey = builder.privateKey;
        this.privateKeyId = builder.privateKeyId;
        this.quotaProjectId = builder.quotaProjectId;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getPrivateKeyId() {
        return privateKeyId;
    }

    public String getQuotaProjectId() {
        return quotaProjectId;
    }

    @Override
    public String getAuthenticationType() {
        return "jwt-access";
    }

    @Override
    public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
        String token = String.join(":", clientId, clientEmail, privateKeyId, quotaProjectId,
                uri.toString());
        return Collections.singletonMap("authorization",
                Collections.singletonList("Bearer " + token));
    }

    @Override
    public boolean hasRequestMetadata() {
        return true;
    }

    @Override
    public boolean hasRequestMetadataOnly() {
        return true;
    }

    @Override
    public void refresh() throws IOException {
    }

    public static final class Builder {
        private String clientId;
        private String clientEmail;
        private PrivateKey privateKey;
        private String privateKeyId;
        private String quotaProjectId;

        public Builder setClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder setClientEmail(String clientEmail) {
            this.clientEmail = clientEmail;
            return this;
        }

        public Builder setPrivateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder setPrivateKeyId(String privateKeyId) {
            this.privateKeyId = privateKeyId;
            return this;
        }

        public Builder setQuotaProjectId(String quotaProjectId) {
            this.quotaProjectId = quotaProjectId;
            return this;
        }

        public ServiceAccountJwtAccessCredentials build() {
            return new ServiceAccountJwtAccessCredentials(this);
        }
    }
}
