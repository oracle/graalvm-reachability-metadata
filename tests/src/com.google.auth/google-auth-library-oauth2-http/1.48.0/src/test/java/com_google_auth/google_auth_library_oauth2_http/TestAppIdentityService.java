/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityService.GetAccessTokenResult;
import com.google.appengine.api.appidentity.AppIdentityService.ParsedAppId;
import com.google.appengine.api.appidentity.AppIdentityService.SigningResult;
import com.google.appengine.api.appidentity.PublicCertificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

final class TestAppIdentityService implements AppIdentityService {
    @Override
    public SigningResult signForApp(byte[] bytesToSign) {
        return new SigningResult("test-key", AppEngineCredentialsTestSupport.SIGNATURE);
    }

    @Override
    public Collection<PublicCertificate> getPublicCertificatesForApp() {
        return Collections.emptyList();
    }

    @Override
    public String getServiceAccountName() {
        return AppEngineCredentialsTestSupport.SERVICE_ACCOUNT;
    }

    @Override
    public String getDefaultGcsBucketName() {
        return "test-bucket";
    }

    @Override
    public GetAccessTokenResult getAccessTokenUncached(Iterable<String> scopes) {
        return getAccessToken(scopes);
    }

    @Override
    public GetAccessTokenResult getAccessToken(Iterable<String> scopes) {
        Date expirationTime = new Date(System.currentTimeMillis() + 3_600_000L);
        return new GetAccessTokenResult(
                AppEngineCredentialsTestSupport.ACCESS_TOKEN, expirationTime);
    }

    @Override
    public ParsedAppId parseFullAppId(String fullAppId) {
        throw new UnsupportedOperationException("Parsing App Engine app IDs is not used here.");
    }
}
