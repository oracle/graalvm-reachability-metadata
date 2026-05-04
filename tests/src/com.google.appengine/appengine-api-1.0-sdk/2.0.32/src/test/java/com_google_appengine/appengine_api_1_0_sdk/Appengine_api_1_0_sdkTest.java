/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_appengine.appengine_api_1_0_sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.Category;
import com.google.appengine.api.datastore.Email;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.GeoPt;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Link;
import com.google.appengine.api.datastore.PhoneNumber;
import com.google.appengine.api.datastore.PostalAddress;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Rating;
import com.google.appengine.api.datastore.ShortBlob;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.Composite;
import com.google.appengine.api.images.CompositeTransform;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.FetchOptions.Builder;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.users.User;
import com.google.apphosting.api.ApiProxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Appengine_api_1_0_sdkTest {
    private static final byte[] PNG_1_BY_1 = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, (byte) 0xc4,
        (byte) 0x89, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte) 0x9c, 0x63, 0x60, 0x00, 0x00, 0x00,
        0x02, 0x00, 0x01, (byte) 0xe2, 0x21, (byte) 0xbc, 0x33,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44,
        (byte) 0xae, 0x42, 0x60, (byte) 0x82
    };

    private TestEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new TestEnvironment();
        ApiProxy.setEnvironmentForCurrentThread(environment);
    }

    @AfterEach
    void tearDown() {
        ApiProxy.setDelegate(null);
        ApiProxy.clearEnvironmentForCurrentThread();
    }

    @Test
    void apiProxyRoutesSynchronousAndAsynchronousCallsThroughDelegate() throws Exception {
        installEnvironmentForCurrentThread();
        RecordingDelegate delegate = new RecordingDelegate();
        ApiProxy.setDelegate(delegate);
        ApiProxy.ApiConfig config = new ApiProxy.ApiConfig();
        config.setDeadlineInSeconds(2.5);

        byte[] syncResponse = ApiProxy.makeSyncCall("demo", "sync", bytes("request"));
        Future<byte[]> asyncResponse = ApiProxy.makeAsyncCall("demo", "async", bytes("request"), config);

        assertThat(text(syncResponse)).isEqualTo("sync:demo/sync/request");
        assertThat(text(asyncResponse.get(1, TimeUnit.SECONDS))).isEqualTo("async:demo/async/request/2.5");
        assertThat(delegate.logEntries).isEmpty();
        assertThat(delegate.lastEnvironment).isSameAs(environment);
    }

    @Test
    void namespaceManagerStoresValidNamespacesOnCurrentRequest() {
        installEnvironmentForCurrentThread();
        NamespaceManager.validateNamespace("tenant-123._ok");
        NamespaceManager.set("tenant-123");

        assertThat(NamespaceManager.get()).isEqualTo("tenant-123");
        assertThatThrownBy(() -> NamespaceManager.validateNamespace("invalid namespace"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datastoreKeysEntitiesPropertiesAndQueriesRoundTrip() {
        installEnvironmentForCurrentThread();
        NamespaceManager.set("customer-a");
        Key accountKey = KeyFactory.createKey("Account", "acme");
        Key orderKey = KeyFactory.createKey(accountKey, "Order", 42L);
        String encodedKey = KeyFactory.keyToString(orderKey);

        assertThat(KeyFactory.stringToKey(encodedKey)).isEqualTo(orderKey);
        assertThat(orderKey.getParent()).isEqualTo(accountKey);
        assertThat(orderKey.getKind()).isEqualTo("Order");
        assertThat(orderKey.getId()).isEqualTo(42L);
        assertThat(orderKey.isComplete()).isTrue();
        assertThat(orderKey.getNamespace()).isEqualTo("customer-a");

        EmbeddedEntity shippingAddress = new EmbeddedEntity();
        shippingAddress.setProperty("city", "Zurich");
        shippingAddress.setUnindexedProperty("notes", new Text("Leave at reception"));

        Entity entity = new Entity(orderKey);
        entity.setIndexedProperty("status", "OPEN");
        entity.setUnindexedProperty("payload", new Blob(bytes("opaque")));
        entity.setProperty("address", shippingAddress);

        assertThat(entity.getKind()).isEqualTo("Order");
        assertThat(entity.getParent()).isEqualTo(accountKey);
        assertThat(entity.hasProperty("status")).isTrue();
        assertThat(entity.isUnindexedProperty("payload")).isTrue();
        assertThat(entity.getProperties()).containsKeys("status", "payload", "address");
        assertThat(entity.clone()).isEqualTo(entity);

        Query.FilterPredicate statusFilter =
                new Query.FilterPredicate("status", Query.FilterOperator.EQUAL, "OPEN");
        Query.FilterPredicate totalFilter = Query.FilterOperator.GREATER_THAN_OR_EQUAL.of("total", 100L);
        Query.CompositeFilter filter = new Query.CompositeFilter(
                Query.CompositeFilterOperator.AND, Arrays.asList(statusFilter, totalFilter));
        PropertyProjection statusProjection = new PropertyProjection("status", String.class);
        Query query = new Query("Order", accountKey)
                .setFilter(filter)
                .addSort("created", Query.SortDirection.DESCENDING)
                .addProjection(statusProjection)
                .setDistinct(true);

        assertThat(query.getAncestor()).isEqualTo(accountKey);
        assertThat(query.getFilter()).isEqualTo(filter);
        assertThat(filter.getSubFilters()).containsExactly(statusFilter, totalFilter);
        assertThat(query.getSortPredicates())
                .extracting(Query.SortPredicate::getPropertyName)
                .containsExactly("created");
        assertThat(query.getProjections()).containsExactly(statusProjection);
        assertThat(query.getSortPredicates())
                .extracting(Query.SortPredicate::getDirection)
                .containsExactly(Query.SortDirection.DESCENDING);
    }

    @Test
    void datastoreValueObjectsStoreAndCompareValues() {
        byte[] payload = bytes("abc");
        Blob blob = new Blob(payload);
        ShortBlob shortBlob = new ShortBlob(payload);
        payload[0] = 'z';

        assertThat(blob.getBytes()).containsExactly(bytes("zbc"));
        assertThat(shortBlob.getBytes()).containsExactly(bytes("abc"));
        assertThat(new Category("books")).isEqualByComparingTo(new Category("books"));
        assertThat(new Email("a@example.com")).isEqualByComparingTo(new Email("a@example.com"));
        assertThat(new Link("https://example.com").getValue()).isEqualTo("https://example.com");
        assertThat(new PhoneNumber("+1-555-0100").getNumber()).isEqualTo("+1-555-0100");
        assertThat(new PostalAddress("1 Main St").getAddress()).isEqualTo("1 Main St");
        assertThat(new Rating(99).getRating()).isEqualTo(99);
        GeoPt geoPoint = new GeoPt(47.37F, 8.54F);
        assertThat(geoPoint.getLatitude()).isEqualTo(47.37F);
        assertThat(geoPoint.getLongitude()).isEqualTo(8.54F);
        assertThat(new Text("large text").getValue()).isEqualTo("large text");
        assertThatThrownBy(() -> new Rating(Rating.MAX_VALUE + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datastoreFetchOptionsUseFluentBuilders() {
        FetchOptions options = FetchOptions.Builder.withLimit(25).offset(5).chunkSize(10).prefetchSize(20);

        assertThat(options.getLimit()).isEqualTo(25);
        assertThat(options.getOffset()).isEqualTo(5);
        assertThat(options.getChunkSize()).isEqualTo(10);
        assertThat(options.getPrefetchSize()).isEqualTo(20);
        assertThat(options).isEqualTo(
                FetchOptions.Builder.withDefaults().limit(25).offset(5).chunkSize(10).prefetchSize(20));
    }

    @Test
    void taskQueueOptionsBuildHttpAndPullQueueRequests() throws Exception {
        RetryOptions retryOptions = RetryOptions.Builder.withTaskRetryLimit(3)
                .taskAgeLimitSeconds(60)
                .minBackoffSeconds(1.5)
                .maxBackoffSeconds(10.0)
                .maxDoublings(2);
        TaskOptions taskOptions = TaskOptions.Builder.withMethod(TaskOptions.Method.POST)
                .taskName("process-order")
                .url("/tasks/process")
                .header("X-Trace", "abc")
                .param("order", "42")
                .param("raw", bytes("data"))
                .countdownMillis(500)
                .retryOptions(retryOptions)
                .tag("blue")
                .dispatchDeadline(Duration.ofSeconds(15));
        TaskOptions payloadTask = TaskOptions.Builder.withPayload("payload", StandardCharsets.UTF_8.name());
        LeaseOptions leaseOptions = LeaseOptions.Builder.withLeasePeriod(30, TimeUnit.SECONDS)
                .countLimit(5)
                .deadlineInSeconds(1.0)
                .groupByTag()
                .tag("blue");

        assertThat(taskOptions.getTaskName()).isEqualTo("process-order");
        assertThat(taskOptions.getMethod()).isEqualTo(TaskOptions.Method.POST);
        assertThat(taskOptions.getUrl()).isEqualTo("/tasks/process");
        assertThat(taskOptions.getHeaders()).containsKey("X-Trace");
        assertThat(taskOptions.getStringParams()).containsKey("order");
        assertThat(taskOptions.getByteArrayParams()).containsKey("raw");
        assertThat(taskOptions.getCountdownMillis()).isEqualTo(500L);
        assertThat(taskOptions.getRetryOptions()).isEqualTo(retryOptions);
        assertThat(taskOptions.getTag()).isEqualTo("blue");
        assertThat(taskOptions.getDispatchDeadline()).isEqualTo(Duration.ofSeconds(15));
        assertThat(payloadTask.getPayload()).containsExactly(bytes("payload"));
        assertThat(new LeaseOptions(leaseOptions)).isEqualTo(leaseOptions);
        assertThat(new RetryOptions(retryOptions)).isEqualTo(retryOptions);
    }

    @Test
    void urlFetchRequestsResponsesAndOptionsExposeHttpState() throws Exception {
        com.google.appengine.api.urlfetch.FetchOptions fetchOptions = Builder.withDeadline(3.0)
                .allowTruncate()
                .doNotFollowRedirects()
                .validateCertificate();
        HTTPRequest request = new HTTPRequest(new URL("https://example.com/api"), HTTPMethod.POST, fetchOptions);
        request.setPayload(bytes("request-body"));
        request.addHeader(new HTTPHeader("Accept", "application/json"));
        request.setHeader(new HTTPHeader("Content-Type", "text/plain"));
        HTTPResponse response = new HTTPResponse(
                201,
                bytes("created"),
                new URL("https://example.com/final"),
                Arrays.asList(new HTTPHeader("X-One", "1"), new HTTPHeader("X-One", "2")));

        assertThat(request.getMethod()).isEqualTo(HTTPMethod.POST);
        assertThat(request.getURL()).hasToString("https://example.com/api");
        assertThat(request.getPayload()).containsExactly(bytes("request-body"));
        assertThat(request.getHeaders()).extracting(HTTPHeader::getName).contains("Accept", "Content-Type");
        assertThat(request.getFetchOptions().getDeadline()).isEqualTo(3.0);
        assertThat(request.getFetchOptions().getAllowTruncate()).isTrue();
        assertThat(request.getFetchOptions().getFollowRedirects()).isFalse();
        assertThat(request.getFetchOptions().getValidateCertificate()).isTrue();
        assertThat(response.getResponseCode()).isEqualTo(201);
        assertThat(response.getContent()).containsExactly(bytes("created"));
        assertThat(response.getFinalUrl()).hasToString("https://example.com/final");
        assertThat(response.getHeadersUncombined()).hasSize(2);
        assertThat(response.getHeaders()).extracting(HTTPHeader::getName).contains("X-One");
    }

    @Test
    void searchDocumentsFieldsFacetsAndQueryOptionsAreComposable() {
        Date published = new Date(1_700_000_000_000L);
        Field title = Field.newBuilder().setName("title").setText("Native Image").setLocale(Locale.ENGLISH).build();
        Field score = Field.newBuilder().setName("score").setNumber(9.5).build();
        Field date = Field.newBuilder().setName("published").setDate(published).build();
        Field location = Field.newBuilder().setName("location").setGeoPoint(new GeoPoint(47.37, 8.54)).build();
        Field vector = Field.newBuilder().setName("embedding").setVector(Arrays.asList(1.0, 0.5, 0.25)).build();
        Facet language = Facet.withAtom("language", "java");
        Facet downloads = Facet.withNumber("downloads", 123.0);
        Document document = Document.newBuilder()
                .setId("doc-1")
                .setLocale(Locale.ENGLISH)
                .setRank(7)
                .addField(title)
                .addField(score)
                .addField(date)
                .addField(location)
                .addField(vector)
                .addFacet(language)
                .addFacet(downloads)
                .build();
        SortExpression sortExpression = SortExpression.newBuilder()
                .setExpression("score")
                .setDirection(SortExpression.SortDirection.DESCENDING)
                .setDefaultValueNumeric(0.0)
                .build();
        SortOptions sortOptions = SortOptions.newBuilder().setLimit(10).addSortExpression(sortExpression).build();
        QueryOptions queryOptions = QueryOptions.newBuilder()
                .setLimit(10)
                .setOffset(2)
                .setFieldsToReturn("title", "score")
                .setFieldsToSnippet("title")
                .setSortOptions(sortOptions)
                .build();

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getRank()).isEqualTo(7);
        assertThat(document.getFieldNames()).contains("title", "score", "published", "location", "embedding");
        assertThat(document.getFacetNames()).contains("language", "downloads");
        assertThat(document.getOnlyField("title").getText()).isEqualTo("Native Image");
        assertThat(document.getOnlyField("score").getNumber()).isEqualTo(9.5);
        assertThat(document.getOnlyField("published").getDate()).isEqualTo(published);
        assertThat(document.getOnlyField("location").getGeoPoint().getLatitude()).isEqualTo(47.37);
        assertThat(document.getOnlyField("embedding").getVector()).containsExactly(1.0, 0.5, 0.25);
        assertThat(document.getOnlyFacet("language")).isEqualTo(language);
        assertThat(queryOptions.getLimit()).isEqualTo(10);
        assertThat(queryOptions.getOffset()).isEqualTo(2);
        assertThat(queryOptions.getFieldsToReturn()).containsExactly("title", "score");
        assertThat(queryOptions.getFieldsToSnippet()).containsExactly("title");
        assertThat(queryOptions.getSortOptions().getLimit()).isEqualTo(10);
        assertThat(queryOptions.getSortOptions().getSortExpressions()).containsExactly(sortExpression);
    }

    @Test
    void appIdentityServiceParsesFullApplicationIds() {
        AppIdentityService appIdentityService = AppIdentityServiceFactory.getAppIdentityService();

        AppIdentityService.ParsedAppId partitioned = appIdentityService.parseFullAppId("s~example.com:orders");
        AppIdentityService.ParsedAppId domainOnly = appIdentityService.parseFullAppId("example.com:orders");
        AppIdentityService.ParsedAppId idOnly = appIdentityService.parseFullAppId("orders");

        assertThat(partitioned.getPartition()).isEqualTo("s");
        assertThat(partitioned.getDomain()).isEqualTo("example.com");
        assertThat(partitioned.getId()).isEqualTo("orders");
        assertThat(domainOnly.getPartition()).isEmpty();
        assertThat(domainOnly.getDomain()).isEqualTo("example.com");
        assertThat(domainOnly.getId()).isEqualTo("orders");
        assertThat(idOnly.getPartition()).isEmpty();
        assertThat(idOnly.getDomain()).isEmpty();
        assertThat(idOnly.getId()).isEqualTo("orders");
    }

    @Test
    void modulesServiceReadsCurrentRuntimeMetadataFromEnvironment() {
        environment = new TestEnvironment("payments", "blue.20240504", "instance-2");
        ApiProxy.setEnvironmentForCurrentThread(environment);
        ModulesService modulesService = ModulesServiceFactory.getModulesService();

        assertThat(modulesService.getCurrentModule()).isEqualTo("payments");
        assertThat(modulesService.getCurrentVersion()).isEqualTo("blue");
        assertThat(modulesService.getCurrentInstanceId()).isEqualTo("instance-2");

        environment.getAttributes().remove(BackendService.INSTANCE_ID_ENV_ATTRIBUTE);
        assertThatThrownBy(modulesService::getCurrentInstanceId).isInstanceOf(ModulesException.class);
    }

    @Test
    void mailUsersMemcacheBlobstoreCapabilitiesAndImagesExposeValueState() {
        MailService.Attachment attachment = new MailService.Attachment("report.txt", bytes("report"), "content-id");
        MailService.Header header = new MailService.Header("X-App", "test");
        MailService.Message message = new MailService.Message();
        message.setSender("sender@example.com");
        message.setReplyTo("reply@example.com");
        message.setTo("to@example.com");
        message.setCc("cc@example.com");
        message.setBcc("bcc@example.com");
        message.setSubject("Subject");
        message.setTextBody("plain");
        message.setHtmlBody("<p>html</p>");
        message.setAmpHtmlBody("<p>amp</p>");
        message.setAttachments(attachment);
        message.setHeaders(header);
        User user = new User("user@example.com", "example.com", "user-id");
        BlobKey blobKey = new BlobKey("blob-key");
        Expiration expiration = Expiration.byDeltaSeconds(30);
        Expiration millisecondExpiration = Expiration.byDeltaMillis(5_000);
        Capability customCapability = new Capability("pkg", "custom");
        Image image = ImagesServiceFactory.makeImage(PNG_1_BY_1);
        Image blobImage = ImagesServiceFactory.makeImageFromBlob(blobKey);
        Transform rotate = ImagesServiceFactory.makeRotate(90);
        Transform resize = ImagesServiceFactory.makeResize(32, 32, true);
        Composite composite = ImagesServiceFactory.makeComposite(image, 1, 2, 0.75F, Composite.Anchor.TOP_LEFT);
        CompositeTransform compositeTransform =
                ImagesServiceFactory.makeCompositeTransform(Arrays.asList(rotate, resize));

        assertThat(message.getSender()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("to@example.com");
        assertThat(message.getAttachments()).containsExactly(attachment);
        assertThat(attachment.getFileName()).isEqualTo("report.txt");
        assertThat(attachment.getData()).containsExactly(bytes("report"));
        assertThat(attachment.getContentID()).isEqualTo("content-id");
        assertThat(message.getHeaders()).containsExactly(header);
        assertThat(header.getName()).isEqualTo("X-App");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getAuthDomain()).isEqualTo("example.com");
        assertThat(user.getUserId()).isEqualTo("user-id");
        assertThat(blobKey.getKeyString()).isEqualTo("blob-key");
        assertThat(blobKey).isEqualByComparingTo(new BlobKey("blob-key"));
        assertThat(expiration.getMillisecondsValue() - System.currentTimeMillis()).isBetween(0L, 30_000L);
        assertThat(millisecondExpiration.getMillisecondsValue() - System.currentTimeMillis()).isBetween(0L, 5_000L);
        assertThat(customCapability.getPackageName()).isEqualTo("pkg");
        assertThat(customCapability.getName()).isEqualTo("custom");
        assertThat(new Capability("pkg", "custom")).isEqualTo(customCapability);
        assertThat(image.getImageData()).containsExactly(PNG_1_BY_1);
        assertThat(blobImage.getBlobKey()).isEqualTo(blobKey);
        assertThat(composite).isNotNull();
        assertThat(compositeTransform.concatenate(rotate).preConcatenate(resize)).isNotNull();
    }

    private void installEnvironmentForCurrentThread() {
        if (environment == null) {
            environment = new TestEnvironment();
        }
        ApiProxy.setEnvironmentForCurrentThread(environment);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static final class TestEnvironment implements ApiProxy.Environment {
        private final Map<String, Object> attributes = new HashMap<>();
        private final String moduleId;
        private final String versionId;

        private TestEnvironment() {
            this("default", "v1", null);
        }

        private TestEnvironment(String moduleId, String versionId, String instanceId) {
            this.moduleId = moduleId;
            this.versionId = versionId;
            if (instanceId != null) {
                attributes.put(BackendService.INSTANCE_ID_ENV_ATTRIBUTE, instanceId);
            }
        }

        @Override
        public String getAppId() {
            return "test-app";
        }

        @Override
        public String getModuleId() {
            return moduleId;
        }

        @Override
        public String getVersionId() {
            return versionId;
        }

        @Override
        public String getEmail() {
            return "user@example.com";
        }

        @Override
        public boolean isLoggedIn() {
            return true;
        }

        @Override
        public boolean isAdmin() {
            return false;
        }

        @Override
        public String getAuthDomain() {
            return "example.com";
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
            return TimeUnit.SECONDS.toMillis(30);
        }
    }

    private static final class RecordingDelegate implements ApiProxy.Delegate<ApiProxy.Environment> {
        private final List<ApiProxy.LogRecord> logEntries = new ArrayList<>();
        private ApiProxy.Environment lastEnvironment;

        @Override
        public byte[] makeSyncCall(
                ApiProxy.Environment environment, String packageName, String methodName, byte[] request) {
            lastEnvironment = environment;
            return bytes("sync:" + packageName + "/" + methodName + "/" + text(request));
        }

        @Override
        public Future<byte[]> makeAsyncCall(
                ApiProxy.Environment environment,
                String packageName,
                String methodName,
                byte[] request,
                ApiProxy.ApiConfig apiConfig) {
            lastEnvironment = environment;
            return CompletableFuture.completedFuture(bytes("async:" + packageName + "/" + methodName + "/"
                    + text(request) + "/" + apiConfig.getDeadlineInSeconds()));
        }

        @Override
        public void log(ApiProxy.Environment environment, ApiProxy.LogRecord record) {
            lastEnvironment = environment;
            logEntries.add(record);
        }

        @Override
        public void flushLogs(ApiProxy.Environment environment) {
            lastEnvironment = environment;
        }

        @Override
        public List<Thread> getRequestThreads(ApiProxy.Environment environment) {
            lastEnvironment = environment;
            return List.of();
        }
    }
}
