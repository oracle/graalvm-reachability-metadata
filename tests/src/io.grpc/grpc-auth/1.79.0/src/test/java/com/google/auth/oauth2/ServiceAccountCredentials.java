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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ServiceAccountCredentials extends Credentials {
    private final String clientId;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final String privateKeyId;
    private final String quotaProjectId;
    private final Collection<String> scopes;

    public ServiceAccountCredentials(String clientId, String clientEmail, PrivateKey privateKey,
            String privateKeyId, String quotaProjectId, Collection<String> scopes) {
        this.clientId = clientId;
        this.clientEmail = clientEmail;
        this.privateKey = privateKey;
        this.privateKeyId = privateKeyId;
        this.quotaProjectId = quotaProjectId;
        this.scopes = Collections.unmodifiableList(new ArrayList<>(scopes));
    }

    public Collection<String> getScopes() {
        return scopes;
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
        return "service-account";
    }

    @Override
    public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
        return Collections.singletonMap("authorization",
                Collections.singletonList("Bearer original"));
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
}
