/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auth.google_auth_library_oauth2_http;

import java.nio.file.Path;

final class AppEngineCredentialsTestSupport implements AutoCloseable {
    static final String ACCESS_TOKEN = "app-engine-access-token";
    static final String SERVICE_ACCOUNT = "app-engine-service@example.iam.gserviceaccount.com";
    static final byte[] SIGNATURE = new byte[] {9, 8, 7, 6};

    private static final String GAE_RUNTIME_VERSION_PROPERTY =
            "com.google.appengine.runtime.version";
    private static final String GAE_RUNTIME_ENVIRONMENT_PROPERTY =
            "com.google.appengine.runtime.environment";
    private static final String JETTY_LOGGER_PROPERTY = "org.eclipse.jetty.util.log.class";
    private static final String USER_HOME_PROPERTY = "user.home";

    private final String previousRuntimeVersion;
    private final String previousRuntimeEnvironment;
    private final String previousJettyLogger;
    private final String previousUserHome;

    private AppEngineCredentialsTestSupport(Path temporaryHome) {
        previousRuntimeVersion = System.getProperty(GAE_RUNTIME_VERSION_PROPERTY);
        previousRuntimeEnvironment = System.getProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY);
        previousJettyLogger = System.getProperty(JETTY_LOGGER_PROPERTY);
        previousUserHome = System.getProperty(USER_HOME_PROPERTY);

        configureAppEngineStandardEnvironment();
        System.setProperty(USER_HOME_PROPERTY, temporaryHome.toString());
    }

    static void configureAppEngineStandardEnvironment() {
        System.setProperty(GAE_RUNTIME_VERSION_PROPERTY, "test-runtime");
        System.setProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, "Development");
        System.clearProperty(JETTY_LOGGER_PROPERTY);
    }

    static AppEngineCredentialsTestSupport install(Path temporaryHome) {
        return new AppEngineCredentialsTestSupport(temporaryHome);
    }

    @Override
    public void close() {
        restoreProperty(GAE_RUNTIME_VERSION_PROPERTY, previousRuntimeVersion);
        restoreProperty(GAE_RUNTIME_ENVIRONMENT_PROPERTY, previousRuntimeEnvironment);
        restoreProperty(JETTY_LOGGER_PROPERTY, previousJettyLogger);
        restoreProperty(USER_HOME_PROPERTY, previousUserHome);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
