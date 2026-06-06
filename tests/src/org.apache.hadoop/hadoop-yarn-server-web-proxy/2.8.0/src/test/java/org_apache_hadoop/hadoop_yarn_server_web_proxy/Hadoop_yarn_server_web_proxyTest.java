/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_yarn_server_web_proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.FilterContainer;
import org.apache.hadoop.yarn.api.ApplicationClientProtocol;
import org.apache.hadoop.yarn.api.protocolrecords.CancelDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.CancelDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.FailApplicationAttemptRequest;
import org.apache.hadoop.yarn.api.protocolrecords.FailApplicationAttemptResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationAttemptReportRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationAttemptReportResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationAttemptsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationAttemptsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationReportResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterMetricsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterMetricsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodeLabelsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodeLabelsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetClusterNodesResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerReportRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainerReportResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainersRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetContainersResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetLabelsToNodesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetLabelsToNodesResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewReservationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewReservationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetNodesToLabelsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetNodesToLabelsResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueInfoResponse;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueUserAclsInfoRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetQueueUserAclsInfoResponse;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.KillApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.MoveApplicationAcrossQueuesRequest;
import org.apache.hadoop.yarn.api.protocolrecords.MoveApplicationAcrossQueuesResponse;
import org.apache.hadoop.yarn.api.protocolrecords.RenewDelegationTokenRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RenewDelegationTokenResponse;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationDeleteRequest;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationDeleteResponse;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationListRequest;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationListResponse;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationSubmissionRequest;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationSubmissionResponse;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationUpdateRequest;
import org.apache.hadoop.yarn.api.protocolrecords.ReservationUpdateResponse;
import org.apache.hadoop.yarn.api.protocolrecords.SignalContainerRequest;
import org.apache.hadoop.yarn.api.protocolrecords.SignalContainerResponse;
import org.apache.hadoop.yarn.api.protocolrecords.SubmitApplicationRequest;
import org.apache.hadoop.yarn.api.protocolrecords.SubmitApplicationResponse;
import org.apache.hadoop.yarn.api.protocolrecords.UpdateApplicationPriorityRequest;
import org.apache.hadoop.yarn.api.protocolrecords.UpdateApplicationPriorityResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.ApplicationNotFoundException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.server.webproxy.AppReportFetcher;
import org.apache.hadoop.yarn.server.webproxy.ProxyUriUtils;
import org.apache.hadoop.yarn.server.webproxy.ProxyUtils;
import org.apache.hadoop.yarn.server.webproxy.WebAppProxy;
import org.apache.hadoop.yarn.server.webproxy.WebAppProxyServer;
import org.apache.hadoop.yarn.server.webproxy.WebAppProxyServlet;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmFilterInitializer;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmIpFilter;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmIpPrincipal;
import org.apache.hadoop.yarn.server.webproxy.amfilter.AmIpServletRequestWrapper;
import org.apache.hadoop.yarn.util.TrackingUriPlugin;
import org.junit.jupiter.api.Test;

public class Hadoop_yarn_server_web_proxyTest {
    private static final ApplicationId APPLICATION_ID = ApplicationId.newInstance(123456789L, 7);

    @Test
    void proxyUriUtilsBuildsProxyPathsQueriesAndUris() throws Exception {
        assertThat(ProxyUriUtils.PROXY_SERVLET_NAME).isEqualTo("proxy");
        assertThat(ProxyUriUtils.PROXY_BASE).isEqualTo("/proxy/");
        assertThat(ProxyUriUtils.PROXY_PATH_SPEC).isEqualTo("/proxy/*");
        assertThat(ProxyUriUtils.PROXY_APPROVAL_PARAM).isEqualTo("proxyapproved");

        assertThat(ProxyUriUtils.getPath(APPLICATION_ID))
                .isEqualTo("/proxy/application_123456789_0007");
        assertThat(ProxyUriUtils.getPath(APPLICATION_ID, "metrics/logs"))
                .isEqualTo("/proxy/application_123456789_0007/metrics/logs");
        assertThat(ProxyUriUtils.getPath(APPLICATION_ID, null))
                .isEqualTo("/proxy/application_123456789_0007");
        assertThatThrownBy(() -> ProxyUriUtils.getPath(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Application id cannot be null");

        assertThat(ProxyUriUtils.getPathAndQuery(APPLICATION_ID, "ui", "a=1", true))
                .isEqualTo("/proxy/application_123456789_0007/ui?a=1&proxyapproved=true");
        assertThat(ProxyUriUtils.getPathAndQuery(APPLICATION_ID, "ui", "?a=1", true))
                .isEqualTo("/proxy/application_123456789_0007/ui?a=1&proxyapproved=true");
        assertThat(ProxyUriUtils.getPathAndQuery(APPLICATION_ID, "ui", "", true))
                .isEqualTo("/proxy/application_123456789_0007/ui?proxyapproved=true");
        assertThat(ProxyUriUtils.getPathAndQuery(APPLICATION_ID, "ui", "a=1", false))
                .isEqualTo("/proxy/application_123456789_0007/ui?a=1");

        URI originalUri = new URI("http://application.example:8042/ui/path?x=1#section");
        URI proxyUri = new URI("https://proxy.example:8443/ignored");
        assertThat(ProxyUriUtils.getProxyUri(originalUri, proxyUri, APPLICATION_ID))
                .isEqualTo(new URI("https://proxy.example:8443/proxy/application_123456789_0007/ui/path?x=1#section"));
        assertThat(ProxyUriUtils.getProxyUri(null, proxyUri, APPLICATION_ID))
                .isEqualTo(new URI("https://proxy.example:8443/proxy/application_123456789_0007/"));
    }

    @Test
    void proxyUriUtilsResolvesApplicationMasterAndPluginUris() throws Exception {
        assertThat(ProxyUriUtils.getUriFromAMUrl("http://", "application.example:8042/ui"))
                .isEqualTo(new URI("http://application.example:8042/ui"));
        assertThat(ProxyUriUtils.getUriFromAMUrl("http://", "https://application.example/ui"))
                .isEqualTo(new URI("https://application.example/ui"));
        assertThat(ProxyUriUtils.getSchemeFromUrl(null)).isEmpty();
        assertThat(ProxyUriUtils.getSchemeFromUrl("application.example:8042/ui")).isEmpty();
        assertThat(ProxyUriUtils.getSchemeFromUrl("https://application.example/ui")).isEqualTo("https");

        URI trackingUri = new URI("https://history.example/app/application_123456789_0007");
        List<TrackingUriPlugin> plugins = new ArrayList<>();
        plugins.add(new FixedTrackingUriPlugin(null));
        plugins.add(new FixedTrackingUriPlugin(trackingUri));

        assertThat(ProxyUriUtils.getUriFromTrackingPlugins(APPLICATION_ID, plugins)).isEqualTo(trackingUri);
        assertThat(ProxyUriUtils.getUriFromTrackingPlugins(APPLICATION_ID,
                Collections.singletonList(new FixedTrackingUriPlugin(null))))
                .isNull();
    }

    @Test
    void proxyUtilsWritesRedirectAndNotFoundResponses() throws Exception {
        TestHttpServletRequest request = new TestHttpServletRequest()
                .withMethod("POST")
                .withRequestUri("/proxy/application_123456789_0007/form");
        RecordingHttpServletResponse redirectResponse = new RecordingHttpServletResponse();

        ProxyUtils.sendRedirect(request, redirectResponse, "https://target.example/app?x=1");

        assertThat(redirectResponse.status).isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(redirectResponse.headers).containsEntry(ProxyUtils.LOCATION, "https://target.example/app?x=1");
        assertThat(redirectResponse.contentType).startsWith("text/html");
        assertThat(redirectResponse.body()).contains("Moved", "Content has moved", "https://target.example/app?x=1");
        assertThat(redirectResponse.writerClosed).isTrue();

        RecordingHttpServletResponse notFoundResponse = new RecordingHttpServletResponse();
        ProxyUtils.notFound(notFoundResponse, "missing application");
        assertThat(notFoundResponse.status).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(notFoundResponse.contentType).startsWith("text/html");
        assertThat(notFoundResponse.body()).contains("missing application");
    }

    @Test
    void proxyUtilsRejectsNonHttpRequests() {
        assertThatThrownBy(() -> ProxyUtils.rejectNonHttpRequests(new BasicServletRequest()))
                .isInstanceOf(ServletException.class)
                .hasMessage(ProxyUtils.E_HTTP_HTTPS_ONLY);
    }

    @Test
    void amIpFilterRedirectsRequestsFromOutsideConfiguredProxyHosts() throws Exception {
        AmIpFilter filter = new AmIpFilter();
        filter.init(new SimpleFilterConfig(filterParameters()));
        TestHttpServletRequest request = new TestHttpServletRequest()
                .withRemoteAddr("203.0.113.10")
                .withRequestUri("/application/ui")
                .withMethod("GET");
        RecordingHttpServletResponse response = new RecordingHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.status).isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(response.headers)
                .containsEntry(ProxyUtils.LOCATION, "http://proxy.example:8088/proxy/application/ui");
        assertThat(response.body()).contains("Content has moved", "http://proxy.example:8088/proxy/application/ui");
        filter.destroy();
    }

    @Test
    void amIpFilterSupportsLegacySingleProxyConfiguration() throws Exception {
        AmIpFilter filter = new AmIpFilter();
        filter.init(new SimpleFilterConfig(legacyFilterParameters()));
        TestHttpServletRequest request = new TestHttpServletRequest()
                .withRemoteAddr("203.0.113.10")
                .withRequestUri("/application/ui")
                .withMethod("GET");
        RecordingHttpServletResponse response = new RecordingHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.called).isFalse();
        assertThat(response.status).isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(response.headers)
                .containsEntry(ProxyUtils.LOCATION, "http://legacy-proxy.example:8088/proxy/application/ui");
        assertThat(response.body()).contains("Content has moved", "http://legacy-proxy.example:8088/proxy/application/ui");
        filter.destroy();
    }

    @Test
    void amIpFilterWrapsProxyRequestsWithUserPrincipalFromCookie() throws Exception {
        AmIpFilter filter = new AmIpFilter();
        filter.init(new SimpleFilterConfig(filterParameters()));
        TestHttpServletRequest request = new TestHttpServletRequest()
                .withRemoteAddr("127.0.0.1")
                .withRequestUri("/application/ui")
                .withCookies(new Cookie(WebAppProxyServlet.PROXY_USER_COOKIE_NAME, "alice"));
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, new RecordingHttpServletResponse(), chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.request).isInstanceOf(AmIpServletRequestWrapper.class);
        assertThat(((HttpServletRequest) chain.request).getRemoteUser()).isEqualTo("alice");
        assertThat(((HttpServletRequest) chain.request).getUserPrincipal().getName()).isEqualTo("alice");
        assertThat(((HttpServletRequest) chain.request).isUserInRole("admin")).isFalse();
        filter.destroy();
    }

    @Test
    void amIpFilterPassesProxyRequestsThroughWhenUserCookieIsMissing() throws Exception {
        AmIpFilter filter = new AmIpFilter();
        filter.init(new SimpleFilterConfig(filterParameters()));
        TestHttpServletRequest request = new TestHttpServletRequest()
                .withRemoteAddr("127.0.0.1")
                .withRequestUri("/application/ui");
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, new RecordingHttpServletResponse(), chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.request).isSameAs(request);
        filter.destroy();
    }

    @Test
    void amIpPrincipalAndRequestWrapperExposeAuthenticatedUser() {
        AmIpPrincipal principal = new AmIpPrincipal("bob");
        AmIpServletRequestWrapper wrapper = new AmIpServletRequestWrapper(new TestHttpServletRequest(), principal);

        assertThat(principal.getName()).isEqualTo("bob");
        assertThat(wrapper.getUserPrincipal()).isSameAs(principal);
        assertThat(wrapper.getRemoteUser()).isEqualTo("bob");
        assertThat(wrapper.isUserInRole("operator")).isFalse();
    }

    @Test
    void webProxyServerResolvesConfiguredBindAddress() {
        Configuration configuration = new Configuration(false);
        configuration.set(YarnConfiguration.PROXY_ADDRESS, "0.0.0.0:18080");

        InetSocketAddress bindAddress = WebAppProxyServer.getBindAddress(configuration);

        assertThat(WebAppProxy.FETCHER_ATTRIBUTE).isEqualTo("AppUrlFetcher");
        assertThat(WebAppProxy.IS_SECURITY_ENABLED_ATTRIBUTE).isEqualTo("IsSecurityEnabled");
        assertThat(WebAppProxy.PROXY_HOST_ATTRIBUTE).isEqualTo("proxyHost");
        assertThat(WebAppProxyServer.SHUTDOWN_HOOK_PRIORITY).isEqualTo(30);
        assertThat(bindAddress.getHostString()).isEqualTo("0.0.0.0");
        assertThat(bindAddress.getPort()).isEqualTo(18080);
    }

    @Test
    void appReportFetcherRequestsApplicationReportsFromConfiguredClient() throws Exception {
        RecordingApplicationClientProtocol applicationsManager = new RecordingApplicationClientProtocol();
        AppReportFetcher fetcher = new AppReportFetcher(new Configuration(false), applicationsManager);

        Object fetchedReport = fetcher.getApplicationReport(APPLICATION_ID);

        assertThat(fetchedReport).isNotNull();
        assertThat(applicationsManager.requestCount).isEqualTo(1);
        assertThat(applicationsManager.requestedApplicationId).isEqualTo(APPLICATION_ID);
    }

    @Test
    void appReportFetcherPropagatesMissingApplicationWhenHistoryServiceIsDisabled() {
        ApplicationNotFoundException missingApplication = new ApplicationNotFoundException("missing application");
        RecordingApplicationClientProtocol applicationsManager = new RecordingApplicationClientProtocol();
        applicationsManager.applicationNotFoundException = missingApplication;
        AppReportFetcher fetcher = new AppReportFetcher(new Configuration(false), applicationsManager);

        assertThatThrownBy(() -> fetcher.getApplicationReport(APPLICATION_ID))
                .isSameAs(missingApplication);
        assertThat(applicationsManager.requestCount).isEqualTo(1);
        assertThat(applicationsManager.requestedApplicationId).isEqualTo(APPLICATION_ID);
    }

    @Test
    void amFilterInitializerAddsProxyHostAndUriParametersToContainer() {
        Configuration configuration = new Configuration(false);
        configuration.set(YarnConfiguration.PROXY_ADDRESS, "proxyhost.example:1234");
        CapturingFilterContainer container = new CapturingFilterContainer();

        new TestAmFilterInitializer("/proxy/application_123456789_0007").initFilter(container, configuration);

        assertThat(container.filterName).isEqualTo("AM_PROXY_FILTER");
        assertThat(container.filterClass).isEqualTo(AmIpFilter.class.getCanonicalName());
        assertThat(container.parameters).containsEntry(AmIpFilter.PROXY_HOSTS, "proxyhost.example");
        assertThat(container.parameters).containsEntry(AmIpFilter.PROXY_URI_BASES,
                "http://proxyhost.example:1234/proxy/application_123456789_0007");
        assertThat(container.globalFilterAdded).isFalse();
    }

    private static Map<String, String> filterParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(AmIpFilter.PROXY_HOSTS, "127.0.0.1");
        parameters.put(AmIpFilter.PROXY_URI_BASES, "http://proxy.example:8088/proxy");
        return parameters;
    }

    private static Map<String, String> legacyFilterParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(AmIpFilter.PROXY_HOST, "127.0.0.1");
        parameters.put(AmIpFilter.PROXY_URI_BASE, "http://legacy-proxy.example:8088/proxy");
        return parameters;
    }

    private static final class FixedTrackingUriPlugin extends TrackingUriPlugin {
        private final URI trackingUri;

        private FixedTrackingUriPlugin(URI trackingUri) {
            this.trackingUri = trackingUri;
        }

        @Override
        public URI getTrackingUri(ApplicationId id) throws URISyntaxException {
            return trackingUri;
        }
    }

    private static final class TestAmFilterInitializer extends AmFilterInitializer {
        private final String applicationWebProxyBase;

        private TestAmFilterInitializer(String applicationWebProxyBase) {
            this.applicationWebProxyBase = applicationWebProxyBase;
        }

        @Override
        protected String getApplicationWebProxyBase() {
            return applicationWebProxyBase;
        }
    }

    private static final class CapturingFilterContainer implements FilterContainer {
        private String filterName;
        private String filterClass;
        private Map<String, String> parameters;
        private boolean globalFilterAdded;

        @Override
        public void addFilter(String name, String classname, Map<String, String> parameters) {
            this.filterName = name;
            this.filterClass = classname;
            this.parameters = new HashMap<>(parameters);
        }

        @Override
        public void addGlobalFilter(String name, String classname, Map<String, String> parameters) {
            this.globalFilterAdded = true;
        }
    }

    private static final class RecordingApplicationClientProtocol implements ApplicationClientProtocol {
        private int requestCount;
        private ApplicationId requestedApplicationId;
        private ApplicationNotFoundException applicationNotFoundException;

        @Override
        public GetApplicationReportResponse getApplicationReport(GetApplicationReportRequest request)
                throws YarnException, IOException {
            requestCount++;
            requestedApplicationId = request.getApplicationId();
            if (applicationNotFoundException != null) {
                throw applicationNotFoundException;
            }
            return new FixedApplicationReportResponse();
        }

        @Override
        public GetNewApplicationResponse getNewApplication(GetNewApplicationRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public SubmitApplicationResponse submitApplication(SubmitApplicationRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public KillApplicationResponse forceKillApplication(KillApplicationRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public FailApplicationAttemptResponse failApplicationAttempt(FailApplicationAttemptRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetClusterMetricsResponse getClusterMetrics(GetClusterMetricsRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetClusterNodesResponse getClusterNodes(GetClusterNodesRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetQueueInfoResponse getQueueInfo(GetQueueInfoRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetQueueUserAclsInfoResponse getQueueUserAcls(GetQueueUserAclsInfoRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public MoveApplicationAcrossQueuesResponse moveApplicationAcrossQueues(
                MoveApplicationAcrossQueuesRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetNewReservationResponse getNewReservation(GetNewReservationRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public ReservationSubmissionResponse submitReservation(ReservationSubmissionRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public ReservationUpdateResponse updateReservation(ReservationUpdateRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public ReservationDeleteResponse deleteReservation(ReservationDeleteRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public ReservationListResponse listReservations(ReservationListRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetNodesToLabelsResponse getNodeToLabels(GetNodesToLabelsRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetLabelsToNodesResponse getLabelsToNodes(GetLabelsToNodesRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetClusterNodeLabelsResponse getClusterNodeLabels(GetClusterNodeLabelsRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetApplicationsResponse getApplications(GetApplicationsRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetApplicationAttemptReportResponse getApplicationAttemptReport(
                GetApplicationAttemptReportRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetApplicationAttemptsResponse getApplicationAttempts(GetApplicationAttemptsRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetContainerReportResponse getContainerReport(GetContainerReportRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetContainersResponse getContainers(GetContainersRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public GetDelegationTokenResponse getDelegationToken(GetDelegationTokenRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public RenewDelegationTokenResponse renewDelegationToken(RenewDelegationTokenRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public CancelDelegationTokenResponse cancelDelegationToken(CancelDelegationTokenRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public SignalContainerResponse signalToContainer(SignalContainerRequest request) {
            throw unusedProtocolMethod();
        }

        @Override
        public UpdateApplicationPriorityResponse updateApplicationPriority(UpdateApplicationPriorityRequest request) {
            throw unusedProtocolMethod();
        }

        private UnsupportedOperationException unusedProtocolMethod() {
            return new UnsupportedOperationException("This protocol method is not used by AppReportFetcher tests");
        }
    }

    private static final class FixedApplicationReportResponse extends GetApplicationReportResponse {
        private ApplicationReport applicationReport;

        @Override
        public ApplicationReport getApplicationReport() {
            return applicationReport;
        }

        @Override
        public void setApplicationReport(ApplicationReport applicationReport) {
            this.applicationReport = applicationReport;
        }
    }

    private static final class SimpleFilterConfig implements FilterConfig {
        private final Map<String, String> parameters;

        private SimpleFilterConfig(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String getFilterName() {
            return "test-filter";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return parameters.get(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }
    }

    private static final class RecordingFilterChain implements FilterChain {
        private boolean called;
        private ServletRequest request;
        private ServletResponse response;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            this.called = true;
            this.request = request;
            this.response = response;
        }
    }

    private static class BasicServletRequest implements ServletRequest {
        private String remoteAddr = "127.0.0.1";
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new EmptyServletInputStream();
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(String name) {
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.emptyMap();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 80;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            return remoteAddr;
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(Collections.singleton(Locale.ENGLISH));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getRealPath(String path) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 80;
        }
    }

    private static final class TestHttpServletRequest extends BasicServletRequest implements HttpServletRequest {
        private String method = "GET";
        private String requestUri = "/";
        private String remoteAddr = "127.0.0.1";
        private String remoteUser;
        private String queryString;
        private String pathInfo = "/";
        private Cookie[] cookies;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private TestHttpServletRequest withMethod(String method) {
            this.method = method;
            return this;
        }

        private TestHttpServletRequest withRequestUri(String requestUri) {
            this.requestUri = requestUri;
            return this;
        }

        private TestHttpServletRequest withRemoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
            return this;
        }

        private TestHttpServletRequest withCookies(Cookie... cookies) {
            this.cookies = cookies;
            return this;
        }

        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public Cookie[] getCookies() {
            return cookies;
        }

        @Override
        public long getDateHeader(String name) {
            return -1L;
        }

        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = headers.get(name);
            if (value == null) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(Collections.singleton(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public int getIntHeader(String name) {
            return -1;
        }

        @Override
        public String getMethod() {
            return method;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getQueryString() {
            return queryString;
        }

        @Override
        public String getRemoteUser() {
            return remoteUser;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new java.lang.StringBuffer("http://localhost" + requestUri);
        }

        @Override
        public String getServletPath() {
            return "";
        }

        @Override
        public HttpSession getSession(boolean create) {
            return null;
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }
    }

    private static final class RecordingHttpServletResponse implements HttpServletResponse {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final List<Cookie> cookies = new ArrayList<>();
        private final StringWriter stringWriter = new StringWriter();
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private int status;
        private String contentType;
        private boolean writerClosed;

        private String body() {
            return stringWriter.toString();
        }

        @Override
        public void addCookie(Cookie cookie) {
            cookies.add(cookie);
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String encodeUrl(String url) {
            return url;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String encodeRedirectUrl(String url) {
            return url;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            this.status = SC_FOUND;
            setHeader(ProxyUtils.LOCATION, location);
        }

        @Override
        public void setDateHeader(String name, long date) {
            setHeader(name, Long.toString(date));
        }

        @Override
        public void addDateHeader(String name, long date) {
            addHeader(name, Long.toString(date));
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            setHeader(name, Integer.toString(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            addHeader(name, Integer.toString(value));
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) {
            this.status = sc;
        }

        public int getStatus() {
            return status;
        }

        public String getHeader(String name) {
            return headers.get(name);
        }

        public Collection<String> getHeaders(String name) {
            String value = headers.get(name);
            return value == null ? Collections.emptyList() : Collections.singletonList(value);
        }

        public Collection<String> getHeaderNames() {
            return headers.keySet();
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {
                @Override
                public void write(int value) {
                    outputStream.write(value);
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            return new PrintWriter(stringWriter) {
                @Override
                public void close() {
                    super.close();
                    writerClosed = true;
                }
            };
        }

        @Override
        public void setCharacterEncoding(String charset) {
        }

        @Override
        public void setContentLength(int len) {
        }

        @Override
        public void setContentType(String type) {
            this.contentType = type;
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() throws IOException {
        }

        @Override
        public void resetBuffer() {
            stringWriter.getBuffer().setLength(0);
        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void reset() {
            status = 0;
            headers.clear();
            resetBuffer();
        }

        @Override
        public void setLocale(Locale loc) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }
    }

    private static final class EmptyServletInputStream extends ServletInputStream {
        @Override
        public int read() throws IOException {
            return -1;
        }
    }
}
