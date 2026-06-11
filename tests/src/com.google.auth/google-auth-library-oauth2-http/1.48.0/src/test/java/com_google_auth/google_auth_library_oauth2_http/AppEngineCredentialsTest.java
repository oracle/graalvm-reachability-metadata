/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.appengine.api.appidentity.AppIdentityServicePb.GetAccessTokenResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetServiceAccountNameResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.SignForAppResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse.GetStatusCode;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse.SetStatusCode;
import com.google.appengine.repackaged.com.google.protobuf.ByteString;
import com.google.apphosting.api.ApiProxy;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("appengine-api-proxy")
public class AppEngineCredentialsTest {
    private static final String GAE_RUNTIME_VERSION_PROPERTY =
            "com.google.appengine.runtime.version";
    private static final String GAE_RUNTIME_ENVIRONMENT_PROPERTY =
            "com.google.appengine.runtime.environment";
    private static final String JETTY_LOGGER_PROPERTY =
            "org.eclipse.jetty.util.log.class";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String INITIAL_GAE_RUNTIME_VERSION =
            System.getProperty(GAE_RUNTIME_VERSION_PROPERTY);
    private static final String INITIAL_GAE_RUNTIME_ENVIRONMENT =
            System.getProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY);
    private static final String INITIAL_JETTY_LOGGER = System.getProperty(JETTY_LOGGER_PROPERTY);

    static {
        configureAppEngineStandardEnvironment();
    }

    @TempDir
    Path temporaryHome;

    private String previousUserHome;
    private ApiProxy.EnvironmentFactory previousEnvironmentFactory;
    private ApiProxy.Delegate<?> previousDelegate;

    @BeforeEach
    public void setUp() {
        configureAppEngineStandardEnvironment();
        previousUserHome = System.getProperty(USER_HOME_PROPERTY);
        previousEnvironmentFactory = ApiProxy.getEnvironmentFactory();
        previousDelegate = ApiProxy.getDelegate();
        System.setProperty(USER_HOME_PROPERTY, temporaryHome.toString());
        setUpAppEngineApiProxy();
    }

    @AfterEach
    public void tearDown() {
        tearDownAppEngineApiProxy(previousEnvironmentFactory, previousDelegate);
        restoreProperty(GAE_RUNTIME_VERSION_PROPERTY, INITIAL_GAE_RUNTIME_VERSION);
        restoreProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, INITIAL_GAE_RUNTIME_ENVIRONMENT);
        restoreProperty(JETTY_LOGGER_PROPERTY, INITIAL_JETTY_LOGGER);
        restoreProperty(USER_HOME_PROPERTY, previousUserHome);
    }

    @Test
    public void appEngineCredentialsRefreshAccessTokenAndSignBytes() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        assertThat(credentials.getClass().getName())
                .isEqualTo("com.google.auth.oauth2.AppEngineCredentials");
        ServiceAccountSigner signer = (ServiceAccountSigner) credentials;

        GoogleCredentials scopedCredentials =
                credentials.createScoped(
                        Collections.singletonList(
                                "https://www.googleapis.com/auth/cloud-platform"));
        AccessToken accessToken = scopedCredentials.refreshAccessToken();
        assertThat(accessToken.getTokenValue()).isNotBlank();
        assertThat(accessToken.getExpirationTime()).isNotNull();

        byte[] signature = signer.sign("payload".getBytes(StandardCharsets.UTF_8));
        assertThat(signature).isNotEmpty();
    }

    private static void configureAppEngineStandardEnvironment() {
        System.setProperty(GAE_RUNTIME_VERSION_PROPERTY, "test-runtime");
        System.setProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, "Development");
        System.clearProperty(JETTY_LOGGER_PROPERTY);
    }

    static void setUpAppEngineApiProxy() {
        ApiProxy.setEnvironmentFactory(TestEnvironment::new);
        ApiProxy.setEnvironmentForCurrentThread(new TestEnvironment());
        ApiProxy.setDelegate(new TestAppIdentityDelegate());
    }

    static void tearDownAppEngineApiProxy(
            ApiProxy.EnvironmentFactory previousEnvironmentFactory,
            ApiProxy.Delegate<?> previousDelegate) {
        ApiProxy.setDelegate(previousDelegate);
        if (previousEnvironmentFactory != null) {
            ApiProxy.setEnvironmentFactory(previousEnvironmentFactory);
        }
        ApiProxy.clearEnvironmentForCurrentThread();
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class TestEnvironment implements ApiProxy.Environment {
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public String getAppId() {
            return "test-app";
        }

        @Override
        public String getModuleId() {
            return "default";
        }

        @Override
        public String getVersionId() {
            return "1";
        }

        @Override
        public String getEmail() {
            return "test@example.com";
        }

        @Override
        public boolean isLoggedIn() {
            return true;
        }

        @Override
        public boolean isAdmin() {
            return true;
        }

        @Override
        public String getAuthDomain() {
            return "gmail.com";
        }

        @Override
        public String getRequestNamespace() {
            return "";
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public long getRemainingMillis() {
            return 60_000L;
        }
    }

    private static final class TestAppIdentityDelegate
            implements ApiProxy.Delegate<ApiProxy.Environment> {
        @Override
        public byte[] makeSyncCall(
                ApiProxy.Environment environment,
                String packageName,
                String methodName,
                byte[] request) {
            if ("memcache".equals(packageName) && "Get".equals(methodName)) {
                return MemcacheGetResponse.newBuilder()
                        .addGetStatus(GetStatusCode.MISS)
                        .build()
                        .toByteArray();
            }
            if ("memcache".equals(packageName) && "Set".equals(methodName)) {
                return MemcacheSetResponse.newBuilder()
                        .addSetStatus(SetStatusCode.STORED)
                        .build()
                        .toByteArray();
            }
            if ("GetServiceAccountName".equals(methodName)) {
                return GetServiceAccountNameResponse.newBuilder()
                        .setServiceAccountName("test-app@appspot.gserviceaccount.com")
                        .build()
                        .toByteArray();
            }
            if ("GetAccessToken".equals(methodName)) {
                return GetAccessTokenResponse.newBuilder()
                        .setAccessToken("test-access-token")
                        .setExpirationTime(System.currentTimeMillis() / 1000L + 3_600L)
                        .build()
                        .toByteArray();
            }
            if ("SignForApp".equals(methodName)) {
                return SignForAppResponse.newBuilder()
                        .setKeyName("test-key")
                        .setSignatureBytes(ByteString.copyFromUtf8("test-signature"))
                        .build()
                        .toByteArray();
            }
            throw new ApiProxy.ApplicationException(0, packageName + "." + methodName);
        }

        @Override
        public Future<byte[]> makeAsyncCall(
                ApiProxy.Environment environment,
                String packageName,
                String methodName,
                byte[] request,
                ApiProxy.ApiConfig apiConfig) {
            return CompletableFuture.completedFuture(
                    makeSyncCall(environment, packageName, methodName, request));
        }

        @Override
        public void log(ApiProxy.Environment environment, ApiProxy.LogRecord logRecord) {
            environment.getAttributes().put("lastLogLevel", logRecord.getLevel());
        }

        @Override
        public void flushLogs(ApiProxy.Environment environment) {
            environment.getAttributes().remove("lastLogLevel");
        }

        @Override
        public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
            return Collections.singletonList(Thread.currentThread());
        }
    }
}
