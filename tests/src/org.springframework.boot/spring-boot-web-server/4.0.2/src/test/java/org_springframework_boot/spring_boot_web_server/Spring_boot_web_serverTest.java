/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_web_server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.Http2;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizerBeanPostProcessor;
import org.springframework.boot.web.server.WebServerSslBundle;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.context.MissingWebServerFactoryBeanException;
import org.springframework.boot.web.server.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.context.WebServerPortFileWriter;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ContextPath;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.boot.web.server.servlet.DocumentRoot;
import org.springframework.boot.web.server.servlet.Jsp;
import org.springframework.boot.web.server.servlet.ServletContextInitializers;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ServletWebServerSettings;
import org.springframework.boot.web.server.servlet.Session;
import org.springframework.boot.web.server.servlet.Session.SessionTrackingMode;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Spring_boot_web_serverTest {
    @Test
    void serverPropertiesExposeNestedConfigurationObjects() {
        ServerProperties properties = new ServerProperties();
        properties.setPort(8081);
        properties.setAddress(InetAddress.getLoopbackAddress());
        properties.setServerHeader("test-server");
        properties.setMaxHttpRequestHeaderSize(DataSize.ofKilobytes(16));
        properties.setShutdown(Shutdown.GRACEFUL);
        properties.setForwardHeadersStrategy(ServerProperties.ForwardHeadersStrategy.FRAMEWORK);
        properties.setMimeMappings(Map.of("graphql", "application/graphql"));

        Compression compression = properties.getCompression();
        compression.setEnabled(true);
        compression.setMimeTypes(new String[] {"application/json", "text/plain" });
        compression.setExcludedUserAgents(new String[] {"legacy-client" });
        compression.setMinResponseSize(DataSize.ofKilobytes(2));

        Http2 http2 = properties.getHttp2();
        http2.setEnabled(true);

        Ssl ssl = Ssl.forBundle("server-bundle");
        ssl.setClientAuth(Ssl.ClientAuth.NEED);
        ssl.setCiphers(new String[] {"TLS_AES_128_GCM_SHA256" });
        ssl.setEnabledProtocols(new String[] {"TLSv1.3" });
        ssl.setKeyAlias("server");
        ssl.setKeyPassword("key-password");
        ssl.setKeyStore("classpath:server.p12");
        ssl.setKeyStorePassword("store-password");
        ssl.setKeyStoreType("PKCS12");
        ssl.setKeyStoreProvider("SUN");
        ssl.setTrustStore("classpath:trust.p12");
        ssl.setTrustStorePassword("trust-password");
        ssl.setTrustStoreType("PKCS12");
        ssl.setTrustStoreProvider("SUN");
        ssl.setCertificate("classpath:server.crt");
        ssl.setCertificatePrivateKey("classpath:server.key");
        ssl.setTrustCertificate("classpath:ca.crt");
        ssl.setTrustCertificatePrivateKey("classpath:ca.key");
        ssl.setProtocol("TLSv1.3");
        ssl.setServerNameBundles(List.of(new Ssl.ServerNameSslBundle("api.example.com", "api-bundle")));
        properties.setSsl(ssl);

        ServerProperties.Servlet servlet = properties.getServlet();
        servlet.setContextPath("/api");
        servlet.setApplicationDisplayName("Spring Boot Web Server Test");
        servlet.setRegisterDefaultServlet(true);
        servlet.getContextParameters().put("mode", "test");
        servlet.getEncoding().setMapping(Map.of(Locale.CANADA_FRENCH, StandardCharsets.UTF_8));
        servlet.getJsp().setClassName("org.example.TestJspServlet");
        servlet.getJsp().setInitParameters(Map.of("development", "false"));
        servlet.getSession().setTimeout(Duration.ofMinutes(7));
        servlet.getSession().setTrackingModes(Set.of(SessionTrackingMode.COOKIE));
        servlet.getSession().getCookie().setSameSite(Cookie.SameSite.STRICT);

        ServerProperties.Reactive.Session reactiveSession = properties.getReactive().getSession();
        reactiveSession.setTimeout(Duration.ofSeconds(30));
        reactiveSession.setMaxSessions(4);
        reactiveSession.getCookie().setName("RSESSIONID");

        assertThat(properties.getPort()).isEqualTo(8081);
        assertThat(properties.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
        assertThat(properties.getServerHeader()).isEqualTo("test-server");
        assertThat(properties.getMaxHttpRequestHeaderSize()).isEqualTo(DataSize.ofKilobytes(16));
        assertThat(properties.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
        assertThat(properties.getForwardHeadersStrategy()).isEqualTo(ServerProperties.ForwardHeadersStrategy.FRAMEWORK);
        assertThat(properties.getMimeMappings().get("graphql")).isEqualTo("application/graphql");
        assertThat(compression.getEnabled()).isTrue();
        assertThat(compression.getMimeTypes()).containsExactly("application/json", "text/plain");
        assertThat(compression.getExcludedUserAgents()).containsExactly("legacy-client");
        assertThat(compression.getMinResponseSize()).isEqualTo(DataSize.ofKilobytes(2));
        assertThat(http2.isEnabled()).isTrue();
        assertThat(Ssl.isEnabled(ssl)).isTrue();
        assertThat(ssl.getBundle()).isEqualTo("server-bundle");
        assertThat(ssl.getClientAuth()).isEqualTo(Ssl.ClientAuth.NEED);
        assertThat(ssl.getCiphers()).containsExactly("TLS_AES_128_GCM_SHA256");
        assertThat(ssl.getEnabledProtocols()).containsExactly("TLSv1.3");
        assertThat(ssl.getServerNameBundles())
                .containsExactly(new Ssl.ServerNameSslBundle("api.example.com", "api-bundle"));
        assertThat(Ssl.ClientAuth.map(ssl.getClientAuth(), "none", "want", "need")).isEqualTo("need");
        assertThat(servlet.getContextPath()).isEqualTo("/api");
        assertThat(servlet.getApplicationDisplayName()).isEqualTo("Spring Boot Web Server Test");
        assertThat(servlet.isRegisterDefaultServlet()).isTrue();
        assertThat(servlet.getContextParameters()).containsEntry("mode", "test");
        assertThat(servlet.getEncoding().getMapping()).containsEntry(Locale.CANADA_FRENCH, StandardCharsets.UTF_8);
        assertThat(servlet.getJsp().getClassName()).isEqualTo("org.example.TestJspServlet");
        assertThat(servlet.getSession().getCookie().getSameSite().attributeValue()).isEqualTo("Strict");
        assertThat(reactiveSession.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(reactiveSession.getMaxSessions()).isEqualTo(4);
        assertThat(reactiveSession.getCookie().getName()).isEqualTo("RSESSIONID");
    }

    @Test
    void mimeMappingsCanBeCopiedMadeLazyAndMadeUnmodifiable() {
        MimeMappings mappings = new MimeMappings(Map.of("json", "application/json"));

        assertThat(mappings.add("txt", "text/plain")).isNull();
        assertThat(mappings.add("json", "application/vnd.test+json")).isEqualTo("application/json");
        assertThat(mappings.get("JSON")).isEqualTo("application/vnd.test+json");
        assertThat(mappings.get("txt")).isEqualTo("text/plain");
        assertThat(mappings.remove("txt")).isEqualTo("text/plain");

        MimeMappings copy = new MimeMappings(mappings);
        MimeMappings lazyCopy = MimeMappings.lazyCopy(mappings);
        mappings.add("yaml", "application/yaml");

        assertThat(copy).isNotEqualTo(mappings);
        assertThat(lazyCopy.get("yaml")).isEqualTo("application/yaml");
        assertThat(lazyCopy.add("xml", "application/xml")).isNull();
        assertThat(mappings.get("xml")).isNull();
        assertThat(lazyCopy.getAll())
                .extracting(MimeMappings.Mapping::getExtension)
                .containsExactlyInAnyOrder("json", "yaml", "xml");
        assertThat(lazyCopy).contains(new MimeMappings.Mapping("xml", "application/xml"));
        assertThat(new MimeMappings.Mapping("xml", "application/xml").toString()).contains("xml", "application/xml");
        assertThat(MimeMappings.DEFAULT.get("html")).isEqualTo("text/html");

        MimeMappings unmodifiable = MimeMappings.unmodifiableMappings(lazyCopy);
        assertThat(unmodifiable.get("xml")).isEqualTo("application/xml");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> unmodifiable.add("bin", "application/octet-stream"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> unmodifiable.remove("xml"));
    }

    @Test
    void servletSettingsCollectInitializersListenersSessionsAndSameSiteSuppliers(@TempDir File tempDir) {
        ServletWebServerSettings settings = new ServletWebServerSettings();
        Session session = new Session();
        session.setTimeout(Duration.ofMinutes(5));
        session.setTrackingModes(Set.of(SessionTrackingMode.COOKIE, SessionTrackingMode.SSL));
        session.setPersistent(true);
        session.setStoreDir(tempDir);
        session.getCookie().setName("SESSION");
        session.getCookie().setDomain("example.com");
        session.getCookie().setPath("/");
        session.getCookie().setHttpOnly(true);
        session.getCookie().setSecure(true);
        session.getCookie().setMaxAge(Duration.ofHours(1));
        session.getCookie().setSameSite(Cookie.SameSite.LAX);
        session.getCookie().setPartitioned(true);
        Jsp jsp = new Jsp();
        jsp.setRegistered(true);
        jsp.setClassName("org.example.JspServlet");
        jsp.setInitParameters(Map.of("fork", "false"));
        ServletContextInitializer initializer = servletContext -> servletContext.setInitParameter(
                "initialized", "true");

        settings.setContextPath(ContextPath.of("/test"));
        settings.setDisplayName("test-app");
        settings.setSession(session);
        settings.setRegisterDefaultServlet(true);
        settings.setMimeMappings(new MimeMappings(Map.of("csv", "text/csv")));
        settings.addMimeMappings(new MimeMappings(Map.of("avif", "image/avif")));
        settings.setDocumentRoot(tempDir);
        settings.setInitializers(List.of(initializer));
        settings.addInitializers(servletContext -> servletContext.setAttribute("extra", Boolean.TRUE));
        settings.setJsp(jsp);
        settings.setLocaleCharsetMappings(Map.of(Locale.JAPANESE, StandardCharsets.UTF_8));
        settings.setInitParameters(Map.of("spring.profiles.active", "test"));
        settings.setCookieSameSiteSuppliers(List.of(CookieSameSiteSupplier.ofStrict().whenHasName("SESSION")));
        settings.addCookieSameSiteSuppliers(
                CookieSameSiteSupplier.ofNone().whenHasNameMatching(Pattern.compile("XSRF-.*")));
        settings.addWebListenerClassNames("org.example.FirstListener", "org.example.SecondListener");

        jakarta.servlet.http.Cookie sessionCookie = new jakarta.servlet.http.Cookie("SESSION", "123");
        jakarta.servlet.http.Cookie xsrfCookie = new jakarta.servlet.http.Cookie("XSRF-TOKEN", "abc");
        jakarta.servlet.http.Cookie otherCookie = new jakarta.servlet.http.Cookie("OTHER", "value");
        CookieSameSiteSupplier secureCookieSupplier = CookieSameSiteSupplier.of(Cookie.SameSite.LAX)
                .when(cookie -> cookie.getSecure());
        otherCookie.setSecure(true);

        assertThat(settings.getContextPath().toString()).isEqualTo("/test");
        assertThat(settings.getDisplayName()).isEqualTo("test-app");
        assertThat(settings.getSession().getTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.getSession().getTrackingModes())
                .containsExactlyInAnyOrder(SessionTrackingMode.COOKIE, SessionTrackingMode.SSL);
        assertThat(settings.getSession().isPersistent()).isTrue();
        assertThat(settings.getSession().getStoreDir()).isEqualTo(tempDir);
        assertThat(settings.getSession().getSessionStoreDirectory().getValidDirectory(true)).isDirectory();
        assertThat(settings.getSession().getCookie().getName()).isEqualTo("SESSION");
        assertThat(settings.getSession().getCookie().getDomain()).isEqualTo("example.com");
        assertThat(settings.getSession().getCookie().getHttpOnly()).isTrue();
        assertThat(settings.getSession().getCookie().getSecure()).isTrue();
        assertThat(settings.getSession().getCookie().getMaxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(settings.getSession().getCookie().getSameSite()).isEqualTo(Cookie.SameSite.LAX);
        assertThat(settings.getSession().getCookie().getPartitioned()).isTrue();
        assertThat(settings.isRegisterDefaultServlet()).isTrue();
        assertThat(settings.getMimeMappings().get("csv")).isEqualTo("text/csv");
        assertThat(settings.getMimeMappings().get("avif")).isEqualTo("image/avif");
        assertThat(settings.getDocumentRoot()).isEqualTo(tempDir);
        assertThat(settings.getInitializers()).hasSize(2);
        assertThat(settings.getJsp().getRegistered()).isTrue();
        assertThat(settings.getJsp().getInitParameters()).containsEntry("fork", "false");
        assertThat(settings.getLocaleCharsetMappings()).containsEntry(Locale.JAPANESE, StandardCharsets.UTF_8);
        assertThat(settings.getInitParameters()).containsEntry("spring.profiles.active", "test");
        assertThat(settings.getCookieSameSiteSuppliers().get(0).getSameSite(sessionCookie))
                .isEqualTo(Cookie.SameSite.STRICT);
        assertThat(settings.getCookieSameSiteSuppliers().get(0).getSameSite(otherCookie)).isNull();
        assertThat(settings.getCookieSameSiteSuppliers().get(1).getSameSite(xsrfCookie))
                .isEqualTo(Cookie.SameSite.NONE);
        assertThat(secureCookieSupplier.getSameSite(otherCookie)).isEqualTo(Cookie.SameSite.LAX);
        assertThat(settings.getWebListenerClassNames())
                .containsExactly("org.example.FirstListener", "org.example.SecondListener");
        assertThat(ContextPath.DEFAULT.toString()).isEmpty();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ContextPath.of("missing-leading-slash"));
    }

    @Test
    void configurableServletFactoryDefaultsDelegateToSettingsAndCustomizers() throws Exception {
        TestServletWebServerFactory factory = new TestServletWebServerFactory();
        factory.setPort(0);
        factory.setAddress(InetAddress.getLoopbackAddress());
        factory.setContextPath("/factory");
        factory.setDisplayName("factory-app");
        factory.setSession(new Session());
        factory.setRegisterDefaultServlet(true);
        factory.setMimeMappings(new MimeMappings(Map.of("mjs", "text/javascript")));
        factory.addMimeMappings(new MimeMappings(Map.of("wasm", "application/wasm")));
        factory.setInitializers(List.of(servletContext -> servletContext.setAttribute("one", Boolean.TRUE)));
        factory.addInitializers(servletContext -> servletContext.setAttribute("two", Boolean.TRUE));
        factory.setJsp(new Jsp());
        factory.setLocaleCharsetMappings(Map.of(Locale.KOREAN, StandardCharsets.UTF_8));
        factory.setInitParameters(Map.of("a", "b"));
        factory.setCookieSameSiteSuppliers(List.of(CookieSameSiteSupplier.ofLax()));
        factory.addCookieSameSiteSuppliers(CookieSameSiteSupplier.ofStrict());
        factory.addWebListeners("com.example.Listener");
        factory.setErrorPages(Set.of(new ErrorPage(RuntimeException.class, "/error")));
        factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/not-found"));
        factory.setSsl(Ssl.forBundle("factory-bundle"));
        TestSslBundles sslBundles = new TestSslBundles();
        factory.setSslBundles(sslBundles);
        Http2 http2 = new Http2();
        http2.setEnabled(true);
        factory.setHttp2(http2);
        Compression compression = new Compression();
        compression.setEnabled(true);
        factory.setCompression(compression);
        factory.setServerHeader("factory-server");
        factory.setShutdown(Shutdown.GRACEFUL);

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("customizer", new TestFactoryCustomizer());
        WebServerFactoryCustomizerBeanPostProcessor postProcessor = new WebServerFactoryCustomizerBeanPostProcessor();
        postProcessor.setBeanFactory(beanFactory);
        Object processed = postProcessor.postProcessBeforeInitialization(factory, "factory");

        assertThat(processed).isSameAs(factory);
        assertThat(factory.getPort()).isEqualTo(12345);
        assertThat(factory.getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
        assertThat(factory.getContextPath()).isEqualTo("/factory");
        assertThat(factory.getSettings().getDisplayName()).isEqualTo("factory-app");
        assertThat(factory.getSettings().isRegisterDefaultServlet()).isTrue();
        assertThat(factory.getSettings().getMimeMappings().get("wasm")).isEqualTo("application/wasm");
        assertThat(factory.getSettings().getInitializers()).hasSize(2);
        assertThat(factory.getSettings().getLocaleCharsetMappings())
                .containsEntry(Locale.KOREAN, StandardCharsets.UTF_8);
        assertThat(factory.getSettings().getInitParameters()).containsEntry("a", "b");
        assertThat(factory.getSettings().getCookieSameSiteSuppliers()).hasSize(2);
        assertThat(factory.getSettings().getWebListenerClassNames()).containsExactly("com.example.Listener");
        assertThat(factory.getErrorPages()).hasSize(2);
        assertThat(factory.getSsl().getBundle()).isEqualTo("factory-bundle");
        assertThat(factory.getSslBundles()).isSameAs(sslBundles);
        assertThat(factory.getHttp2().isEnabled()).isTrue();
        assertThat(factory.getCompression().getEnabled()).isTrue();
        assertThat(factory.getServerHeader()).isEqualTo("factory-server");
        assertThat(factory.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
    }

    @Test
    void webServerSslBundleResolvesNamedBundlesAndInlineConfiguration(@TempDir File tempDir) throws Exception {
        SslBundle namedBundle = SslBundle.of(SslStoreBundle.NONE);
        Ssl namedSsl = Ssl.forBundle("test-bundle");

        SslBundle resolvedNamedBundle = WebServerSslBundle.get(namedSsl,
                new SingleSslBundles("test-bundle", namedBundle));

        assertThat(resolvedNamedBundle).isSameAs(namedBundle);

        File keyStoreFile = new File(tempDir, "server.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        try (OutputStream output = new FileOutputStream(keyStoreFile)) {
            keyStore.store(output, "store-password".toCharArray());
        }

        Ssl inlineSsl = new Ssl();
        inlineSsl.setKeyAlias("server");
        inlineSsl.setKeyPassword("key-password");
        inlineSsl.setKeyStore(keyStoreFile.toURI().toString());
        inlineSsl.setKeyStorePassword("store-password");
        inlineSsl.setKeyStoreType("PKCS12");
        inlineSsl.setProtocol("TLSv1.3");
        inlineSsl.setCiphers(new String[] {"TLS_AES_128_GCM_SHA256" });
        inlineSsl.setEnabledProtocols(new String[] {"TLSv1.3" });

        SslBundle inlineBundle = WebServerSslBundle.get(inlineSsl);

        assertThat(inlineBundle.getKey().getAlias()).isEqualTo("server");
        assertThat(inlineBundle.getKey().getPassword()).isEqualTo("key-password");
        assertThat(inlineBundle.getProtocol()).isEqualTo("TLSv1.3");
        assertThat(inlineBundle.getOptions().getCiphers()).containsExactly("TLS_AES_128_GCM_SHA256");
        assertThat(inlineBundle.getOptions().getEnabledProtocols()).containsExactly("TLSv1.3");
        assertThat(inlineBundle.getStores().getKeyStore()).isNotNull();
        assertThat(inlineBundle.getStores().getKeyStore().getType()).isEqualToIgnoringCase("PKCS12");
        assertThat(inlineBundle.getStores().getKeyStorePassword()).isEqualTo("store-password");
        assertThat(inlineBundle.getStores().getTrustStore()).isNull();
    }

    @Test
    void servletWebServerApplicationContextStartsFactoryServerAndPublishesPort() {
        TestServletWebServerFactory factory = new TestServletWebServerFactory();
        RecordingWebServer webServer = new RecordingWebServer(49152);
        factory.webServer = webServer;
        AtomicReference<WebServerInitializedEvent> event = new AtomicReference<>();

        try (AnnotationConfigServletWebServerApplicationContext context =
                new AnnotationConfigServletWebServerApplicationContext()) {
            context.setServerNamespace("test");
            context.registerBean(ServletWebServerFactory.class, () -> factory);
            context.addApplicationListener(applicationEvent -> {
                if (applicationEvent instanceof WebServerInitializedEvent webServerEvent) {
                    event.set(webServerEvent);
                }
            });
            new ServerPortInfoApplicationContextInitializer().initialize(context);

            context.refresh();

            assertThat(context.getServerNamespace()).isEqualTo("test");
            assertThat(context.getWebServer()).isSameAs(webServer);
            assertThat(webServer.started).isTrue();
            assertThat(factory.initializers).hasSize(1);
            assertThat(event.get()).isNotNull();
            assertThat(event.get().getWebServer()).isSameAs(webServer);
            assertThat(event.get().getApplicationContext()).isSameAs(context);
            assertThat(context.getEnvironment().getProperty("local.test.port", Integer.class)).isEqualTo(49152);
        }

        assertThat(webServer.stopped).isTrue();
    }

    @Test
    void webServerDefaultsPortInUseExceptionAndMissingFactoryExceptionExposeState() {
        RecordingWebServer server = new RecordingWebServer(8080);
        AtomicReference<GracefulShutdownResult> result = new AtomicReference<>();

        server.shutDownGracefully(result::set);
        server.destroy();

        assertThat(result).hasValue(GracefulShutdownResult.IMMEDIATE);
        assertThat(server.stopped).isTrue();
        assertThat(new PortInUseException(8080).getPort()).isEqualTo(8080);
        assertThatExceptionOfType(PortInUseException.class)
                .isThrownBy(() -> PortInUseException.throwIfPortBindingException(
                        new BindException("Address in use"), () -> 8080))
                .satisfies(exception -> assertThat(exception.getPort()).isEqualTo(8080));
        AtomicReference<BindException> bindException = new AtomicReference<>();
        PortInUseException.ifPortBindingException(
                new IllegalStateException(new BindException("Address already in use")), bindException::set);
        assertThat(bindException.get()).hasMessageContaining("in use");

        MissingWebServerFactoryBeanException missing = new MissingWebServerFactoryBeanException(
                ServletWebServerApplicationContext.class, ServletWebServerFactory.class, WebApplicationType.SERVLET);
        assertThat(missing.getWebApplicationType()).isEqualTo(WebApplicationType.SERVLET);
        assertThat(missing.getBeanType()).isEqualTo(ServletWebServerFactory.class);
    }

    @Test
    void webServerPortFileWriterWritesNamespaceSpecificPortFile(@TempDir File tempDir) throws Exception {
        File portFile = new File(tempDir, "application.port");
        File managementPortFile = new File(tempDir, "application-management.port");
        RecordingWebServer server = new RecordingWebServer(49494);
        String previousPortFile = System.getProperty("PORTFILE");
        System.setProperty("PORTFILE", portFile.getAbsolutePath());
        WebServerPortFileWriter writer;
        try {
            writer = new WebServerPortFileWriter(new File(tempDir, "ignored.port"));
        } finally {
            if (previousPortFile != null) {
                System.setProperty("PORTFILE", previousPortFile);
            } else {
                System.clearProperty("PORTFILE");
            }
        }

        try (ServletWebServerApplicationContext context = new ServletWebServerApplicationContext()) {
            context.setServerNamespace("management");

            writer.onApplicationEvent(new ServletWebServerInitializedEvent(server, context));
        }

        assertThat(portFile).doesNotExist();
        assertThat(managementPortFile).isFile();
        assertThat(Files.readString(managementPortFile.toPath(), StandardCharsets.UTF_8)).isEqualTo("49494");
    }

    @Test
    void documentRootAndServletContextInitializersUseValidConfiguredLocations(@TempDir File tempDir) {
        DocumentRoot documentRoot = new DocumentRoot(LogFactory.getLog(getClass()));
        documentRoot.setDirectory(tempDir);
        ServletWebServerSettings settings = new ServletWebServerSettings();
        ServletContextInitializer first = servletContext -> servletContext.setInitParameter("first", "true");
        ServletContextInitializer second = servletContext -> servletContext.setInitParameter("second", "true");
        settings.addInitializers(first);

        Collection<ServletContextInitializer> initializers = new ArrayList<>();
        ServletContextInitializers.from(settings, second).forEach(initializers::add);

        assertThat(documentRoot.getValidDirectory()).isEqualTo(tempDir);
        assertThat(initializers).hasSize(4).contains(first, second);
        assertThat(settings.getStaticResourceUrls()).isNotNull();
    }

    static final class SingleSslBundles implements SslBundles {
        private final String name;
        private final SslBundle bundle;

        SingleSslBundles(String name, SslBundle bundle) {
            this.name = name;
            this.bundle = bundle;
        }

        @Override
        public SslBundle getBundle(String name) {
            if (!this.name.equals(name)) {
                throw new NoSuchSslBundleException(name, "No test SSL bundle is registered");
            }
            return this.bundle;
        }

        @Override
        public void addBundleUpdateHandler(String name, Consumer<SslBundle> updateHandler) {
            if (!this.name.equals(name)) {
                throw new NoSuchSslBundleException(name, "No test SSL bundle is registered");
            }
        }

        @Override
        public void addBundleRegisterHandler(BiConsumer<String, SslBundle> registerHandler) {
            registerHandler.accept(this.name, this.bundle);
        }

        @Override
        public List<String> getBundleNames() {
            return List.of(this.name);
        }
    }

    static final class TestFactoryCustomizer implements WebServerFactoryCustomizer<TestServletWebServerFactory> {
        @Override
        public void customize(TestServletWebServerFactory factory) {
            factory.setPort(12345);
        }
    }

    static final class TestSslBundles implements SslBundles {
        @Override
        public SslBundle getBundle(String name) {
            throw new NoSuchSslBundleException(name, "No test SSL bundle is registered");
        }

        @Override
        public void addBundleUpdateHandler(String name, Consumer<SslBundle> updateHandler) {
            throw new NoSuchSslBundleException(name, "No test SSL bundle is registered");
        }

        @Override
        public void addBundleRegisterHandler(BiConsumer<String, SslBundle> registerHandler) {
            if (registerHandler == null) {
                throw new IllegalArgumentException("Register handler must not be null");
            }
        }

        @Override
        public List<String> getBundleNames() {
            return List.of();
        }
    }

    static final class TestServletWebServerFactory extends AbstractConfigurableWebServerFactory
            implements ConfigurableServletWebServerFactory {
        private final ServletWebServerSettings settings = new ServletWebServerSettings();
        private final List<ServletContextInitializer> initializers = new ArrayList<>();
        private WebServer webServer = new RecordingWebServer(0);

        @Override
        public ServletWebServerSettings getSettings() {
            return this.settings;
        }

        @Override
        public WebServer getWebServer(ServletContextInitializer... initializers) {
            this.initializers.addAll(List.of(initializers));
            return this.webServer;
        }

        @Override
        public void addWebListeners(String... webListenerClassNames) {
            this.settings.addWebListenerClassNames(webListenerClassNames);
        }
    }

    static final class RecordingWebServer implements WebServer {
        private final int port;
        private boolean started;
        private boolean stopped;

        RecordingWebServer(int port) {
            this.port = port;
        }

        @Override
        public void start() {
            this.started = true;
            this.stopped = false;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        @Override
        public int getPort() {
            return this.port;
        }
    }
}
