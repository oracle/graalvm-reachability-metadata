/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.IAppIdentityServiceFactory;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.spi.ServiceFactoryFactory;
import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AppEngineCredentialsTest {
    static final String GAE_RUNTIME_VERSION_PROPERTY = "com.google.appengine.runtime.version";
    static final String GAE_RUNTIME_ENVIRONMENT_PROPERTY =
            "com.google.appengine.runtime.environment";
    static final String JETTY_LOGGER_PROPERTY = "org.eclipse.jetty.util.log.class";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String SERVICE_ACCOUNT = "appengine-service@example.test";
    private static final String ACCESS_TOKEN = "appengine-access-token";
    private static final byte[] SIGNATURE = "signed-by-appengine".getBytes(StandardCharsets.UTF_8);
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean();

    private String previousRuntimeVersion;
    private String previousRuntimeEnvironment;
    private String previousJettyLogger;
    private String previousUserHome;

    @TempDir
    Path temporaryHome;

    static {
        configureAppEngineStandardEnvironment();
        installAppIdentityServiceFactory();
    }

    @BeforeEach
    public void setUp() {
        previousRuntimeVersion = System.getProperty(GAE_RUNTIME_VERSION_PROPERTY);
        previousRuntimeEnvironment = System.getProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY);
        previousJettyLogger = System.getProperty(JETTY_LOGGER_PROPERTY);
        previousUserHome = System.getProperty(USER_HOME_PROPERTY);
        configureAppEngineStandardEnvironment();
        System.setProperty(USER_HOME_PROPERTY, temporaryHome.toString());
    }

    @AfterEach
    public void tearDown() {
        restoreProperty(GAE_RUNTIME_VERSION_PROPERTY, previousRuntimeVersion);
        restoreProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, previousRuntimeEnvironment);
        restoreProperty(JETTY_LOGGER_PROPERTY, previousJettyLogger);
        restoreProperty(USER_HOME_PROPERTY, previousUserHome);
    }

    @Test
    public void applicationDefaultCredentialsRefreshesAndSignsWithAppIdentityService()
            throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        assertAppEngineCredentials(credentials);
        assertThat(credentials.createScopedRequired()).isTrue();

        GoogleCredentials scopedCredentials = credentials.createScoped(List.of("scope-one"));
        AccessToken token = scopedCredentials.refreshAccessToken();

        assertThat(token.getTokenValue()).isEqualTo(ACCESS_TOKEN);
        assertThat(token.getExpirationTime()).isAfter(new Date());
    }

    @Test
    public void serializedApplicationDefaultCredentialsReinitializesAppIdentityService()
            throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("scope-one"));

        GoogleCredentials restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialize(credentials)))) {
            restored = (GoogleCredentials) input.readObject();
        }

        assertAppEngineCredentials(restored);
        AccessToken token = restored.refreshAccessToken();
        assertThat(token.getTokenValue()).isEqualTo(ACCESS_TOKEN);
        assertThat(token.getExpirationTime()).isAfter(new Date());
    }

    private static void assertAppEngineCredentials(GoogleCredentials credentials) {
        assertThat(credentials.getClass().getName())
                .isEqualTo("com.google.auth.oauth2.AppEngineCredentials");
        assertThat(credentials).isInstanceOf(ServiceAccountSigner.class);
        ServiceAccountSigner signer = (ServiceAccountSigner) credentials;
        assertThat(signer.getAccount()).isEqualTo(SERVICE_ACCOUNT);
        assertThat(signer.sign("payload".getBytes(StandardCharsets.UTF_8))).isEqualTo(SIGNATURE);
    }

    private static byte[] serialize(GoogleCredentials credentials) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(credentials);
        }
        return bytes.toByteArray();
    }

    static void configureAppEngineStandardEnvironment() {
        System.setProperty(GAE_RUNTIME_VERSION_PROPERTY, "test-runtime");
        System.setProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, "Development");
        System.clearProperty(JETTY_LOGGER_PROPERTY);
    }

    static void installAppIdentityServiceFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            ServiceFactoryFactory.register(new TestAppIdentityServiceFactoryProvider());
        }
    }

    static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class TestAppIdentityServiceFactoryProvider
            extends FactoryProvider<IAppIdentityServiceFactory> {
        private TestAppIdentityServiceFactoryProvider() {
            super(IAppIdentityServiceFactory.class);
        }

        @Override
        protected IAppIdentityServiceFactory getFactoryInstance() {
            return TestAppIdentityService::new;
        }
    }

    private static final class TestAppIdentityService implements AppIdentityService {
        @Override
        public SigningResult signForApp(byte[] signBlob) {
            return new SigningResult("test-key", SIGNATURE.clone());
        }

        @Override
        public Collection<PublicCertificate> getPublicCertificatesForApp() {
            return List.of();
        }

        @Override
        public String getServiceAccountName() {
            return SERVICE_ACCOUNT;
        }

        @Override
        public String getDefaultGcsBucketName() {
            return "test-bucket";
        }

        @Override
        public GetAccessTokenResult getAccessTokenUncached(Iterable<String> scopes) {
            return new GetAccessTokenResult(ACCESS_TOKEN, futureExpirationTime());
        }

        @Override
        public GetAccessTokenResult getAccessToken(Iterable<String> scopes) {
            assertThat(scopes).containsExactly("scope-one");
            return new GetAccessTokenResult(ACCESS_TOKEN, futureExpirationTime());
        }

        @Override
        public ParsedAppId parseFullAppId(String fullAppId) {
            throw new UnsupportedOperationException("Parsing app ids is not needed by this test");
        }

        private static Date futureExpirationTime() {
            return new Date(System.currentTimeMillis() + 3_600_000);
        }
    }
}
