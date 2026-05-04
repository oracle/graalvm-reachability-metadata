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
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.blobstore.RangeFormatException;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.Category;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Email;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entities;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.GeoPt;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.Link;
import com.google.appengine.api.datastore.PhoneNumber;
import com.google.appengine.api.datastore.PostalAddress;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Rating;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.quota.QuotaServiceFactory;
import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.FieldExpression;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.SearchServiceConfig;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Appengine_api_1_0_sdkTest {
    private final TestEnvironment environment = new TestEnvironment();

    @BeforeEach
    void setUpEnvironment() {
        installEnvironment();
    }

    @AfterEach
    void clearEnvironment() {
        ApiProxy.clearEnvironmentForCurrentThread();
        ApiProxy.setDelegate(null);
    }

    private void installEnvironment() {
        ApiProxy.setEnvironmentForCurrentThread(environment);
    }

    @Test
    void serviceFactoriesCreateClientImplementations() {
        installEnvironment();

        assertThat(UserServiceFactory.getUserService()).isNotNull();
        assertThat(CapabilitiesServiceFactory.getCapabilitiesService()).isNotNull();
        assertThat(MemcacheServiceFactory.getMemcacheService()).isNotNull();
        assertThat(MemcacheServiceFactory.getMemcacheService("tenant-a")).isNotNull();
        assertThat(MemcacheServiceFactory.getAsyncMemcacheService()).isNotNull();
        assertThat(QueueFactory.getDefaultQueue()).isNotNull();
        assertThat(QueueFactory.getQueue("critical")).isNotNull();
        assertThat(MailServiceFactory.getMailService()).isNotNull();
        assertThat(BlobstoreServiceFactory.getBlobstoreService()).isNotNull();
        assertThat(ImagesServiceFactory.getImagesService()).isNotNull();
        assertThat(URLFetchServiceFactory.getURLFetchService()).isNotNull();
        assertThat(DatastoreServiceFactory.getDatastoreService()).isNotNull();
        assertThat(DatastoreServiceFactory.getAsyncDatastoreService()).isNotNull();
        SearchServiceConfig searchServiceConfig = SearchServiceConfig.newBuilder()
                .setNamespace("tenant-a")
                .build();

        assertThat(SearchServiceFactory.getSearchService()).isNotNull();
        assertThat(SearchServiceFactory.getSearchService(searchServiceConfig)).isNotNull();
        assertThat(AppIdentityServiceFactory.getAppIdentityService()).isNotNull();
        assertThat(BackendServiceFactory.getBackendService()).isNotNull();
        assertThat(ModulesServiceFactory.getModulesService()).isNotNull();
        assertThat(QuotaServiceFactory.getQuotaService()).isNotNull();
        assertThat(OAuthServiceFactory.getOAuthService()).isNotNull();
        assertThat(LogServiceFactory.getLogService()).isNotNull();
    }

    @Test
    void namespaceAndUsersUseCurrentApiProxyEnvironment() {
        installEnvironment();
        NamespaceManager.set("customer-a");
        NamespaceManager.validateNamespace("customer-b");

        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();

        assertThat(NamespaceManager.get()).isEqualTo("customer-a");
        assertThat(NamespaceManager.getGoogleAppsNamespace()).isEmpty();
        assertThat(userService.isUserLoggedIn()).isTrue();
        assertThat(userService.isUserAdmin()).isTrue();
        assertThat(currentUser.getEmail()).isEqualTo("admin@example.com");
        assertThat(currentUser.getAuthDomain()).isEqualTo("example.com");
        assertThatThrownBy(() -> NamespaceManager.validateNamespace("bad/name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datastoreKeysEntitiesPropertiesAndQueriesAreComposable() {
        installEnvironment();
        Key accountKey = KeyFactory.createKey("Account", "acme");
        Key invoiceKey = KeyFactory.createKey(accountKey, "Invoice", 101L);
        String encodedKey = KeyFactory.keyToString(invoiceKey);
        Entity entity = new Entity(invoiceKey);
        EmbeddedEntity embeddedEntity = new EmbeddedEntity();
        Blob blob = new Blob(new byte[] {1, 2, 3});
        Text notes = new Text("long notes");
        GeoPt geoPt = new GeoPt(47.6F, -122.3F);

        embeddedEntity.setProperty("status", "paid");
        embeddedEntity.setProperty("rating", new Rating(95));
        entity.setIndexedProperty("amount", 42L);
        entity.setUnindexedProperty("notes", notes);
        entity.setProperty("attachment", blob);
        entity.setProperty("address", new PostalAddress("123 Cloud Street"));
        entity.setProperty("category", new Category("enterprise"));
        entity.setProperty("email", new Email("billing@example.com"));
        entity.setProperty("phone", new PhoneNumber("+1-555-0100"));
        entity.setProperty("website", new Link("https://example.com"));
        entity.setProperty("location", geoPt);
        entity.setProperty("details", embeddedEntity);

        Filter amountFilter = new FilterPredicate("amount", FilterOperator.GREATER_THAN_OR_EQUAL, 40L);
        Filter statusFilter = new FilterPredicate("details.status", FilterOperator.EQUAL, "paid");
        Query query = new Query("Invoice", accountKey)
                .setFilter(new CompositeFilter(CompositeFilterOperator.AND, Arrays.asList(amountFilter, statusFilter)))
                .addSort("amount", SortDirection.DESCENDING)
                .addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.DESCENDING)
                .addProjection(new PropertyProjection("amount", Long.class))
                .setDistinct(true);
        Query reversed = query.reverse();

        assertThat(KeyFactory.stringToKey(encodedKey)).isEqualTo(invoiceKey);
        assertThat(entity.getKey()).isEqualTo(invoiceKey);
        assertThat(entity.getParent()).isEqualTo(accountKey);
        assertThat(entity.getKind()).isEqualTo("Invoice");
        assertThat(entity.getProperty("amount")).isEqualTo(42L);
        assertThat(entity.isUnindexedProperty("notes")).isTrue();
        assertThat(((Blob) entity.getProperty("attachment")).getBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(((Text) entity.getProperty("notes")).getValue()).isEqualTo("long notes");
        assertThat(((GeoPt) entity.getProperty("location")).getLatitude()).isEqualTo(47.6F);
        assertThat(((EmbeddedEntity) entity.getProperty("details")).getProperty("status")).isEqualTo("paid");
        assertThat(query.getAncestor()).isEqualTo(accountKey);
        assertThat(query.getFilter()).isInstanceOf(CompositeFilter.class);
        assertThat(query.getSortPredicates()).hasSize(2);
        assertThat(query.getProjections()).hasSize(1);
        assertThat(query.getDistinct()).isTrue();
        assertThat(reversed.getSortPredicates().get(0).getDirection()).isEqualTo(SortDirection.ASCENDING);
    }

    @Test
    void datastoreMetadataKeysAndKeyRangesAreComposable() {
        installEnvironment();
        Key accountKey = KeyFactory.createKey("Account", "acme");
        Key firstInvoiceKey = KeyFactory.createKey(accountKey, "Invoice", 101L);
        Key secondInvoiceKey = KeyFactory.createKey(accountKey, "Invoice", 102L);
        Key thirdInvoiceKey = KeyFactory.createKey(accountKey, "Invoice", 103L);
        KeyRange keyRange = new KeyRange(accountKey, "Invoice", 101L, 103L);
        Key kindKey = Entities.createKindKey("Invoice");
        Key propertyKey = Entities.createPropertyKey("Invoice", "amount");
        Key namespaceKey = Entities.createNamespaceKey("customer-a");
        Key defaultNamespaceKey = Entities.createNamespaceKey("");
        Key entityGroupKey = Entities.createEntityGroupKey(firstInvoiceKey);
        Entity entityGroupMetadata = new Entity(entityGroupKey);

        entityGroupMetadata.setProperty("__version__", 7L);

        assertThat(keyRange.getStart()).isEqualTo(firstInvoiceKey);
        assertThat(keyRange.getEnd()).isEqualTo(thirdInvoiceKey);
        assertThat(keyRange.getSize()).isEqualTo(3L);
        assertThat(keyRange).containsExactly(firstInvoiceKey, secondInvoiceKey, thirdInvoiceKey);
        assertThat(kindKey.getKind()).isEqualTo(Entities.KIND_METADATA_KIND);
        assertThat(kindKey.getName()).isEqualTo("Invoice");
        assertThat(propertyKey.getParent()).isEqualTo(kindKey);
        assertThat(propertyKey.getKind()).isEqualTo(Entities.PROPERTY_METADATA_KIND);
        assertThat(propertyKey.getName()).isEqualTo("amount");
        assertThat(Entities.getNamespaceFromNamespaceKey(namespaceKey)).isEqualTo("customer-a");
        assertThat(Entities.getNamespaceFromNamespaceKey(defaultNamespaceKey)).isEmpty();
        assertThat(entityGroupKey.getParent()).isEqualTo(accountKey);
        assertThat(entityGroupKey.getKind()).isEqualTo(Entities.ENTITY_GROUP_METADATA_KIND);
        assertThat(entityGroupKey.getId()).isEqualTo(Entities.ENTITY_GROUP_METADATA_ID);
        assertThat(Entities.getVersionProperty(entityGroupMetadata)).isEqualTo(7L);
    }

    @Test
    void taskQueueOptionsSupportHeadersParamsRetriesLeasesAndDeadlines() throws Exception {
        RetryOptions retryOptions = RetryOptions.Builder.withTaskRetryLimit(3)
                .taskAgeLimitSeconds(120)
                .minBackoffSeconds(1.5)
                .maxBackoffSeconds(10.0)
                .maxDoublings(4);
        TaskOptions taskOptions = TaskOptions.Builder.withUrl("/tasks/process")
                .taskName("process-42")
                .method(TaskOptions.Method.POST)
                .payload("payload", StandardCharsets.UTF_8.name())
                .header("X-App", "metadata-test")
                .param("account", "acme")
                .param("raw", new byte[] {9, 8, 7})
                .countdownMillis(250)
                .retryOptions(retryOptions)
                .tag("batch-a")
                .dispatchDeadline(Duration.ofSeconds(15));
        LeaseOptions leaseOptions = LeaseOptions.Builder.withLeasePeriod(30, TimeUnit.SECONDS)
                .countLimit(5)
                .deadlineInSeconds(2.0)
                .groupByTag()
                .tag("batch-a");

        assertThat(taskOptions.getUrl()).isEqualTo("/tasks/process");
        assertThat(taskOptions.getTaskName()).isEqualTo("process-42");
        assertThat(taskOptions.getMethod()).isEqualTo(TaskOptions.Method.POST);
        assertThat(new String(taskOptions.getPayload(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(taskOptions.getHeaders()).containsKey("X-App");
        assertThat(taskOptions.getStringParams()).containsKey("account");
        assertThat(taskOptions.getByteArrayParams()).containsKey("raw");
        assertThat(taskOptions.getCountdownMillis()).isEqualTo(250L);
        assertThat(taskOptions.getRetryOptions()).isEqualTo(retryOptions);
        assertThat(taskOptions.getTag()).isEqualTo("batch-a");
        assertThat(taskOptions.getDispatchDeadline()).isEqualTo(Duration.ofSeconds(15));
        assertThat(new LeaseOptions(leaseOptions)).isEqualTo(leaseOptions);
    }

    @Test
    void blobstoreByteRangesAndUploadOptionsRetainConfiguration() {
        ByteRange closedRange = ByteRange.parse("bytes=10-19");
        ByteRange openRange = new ByteRange(128);
        ByteRange suffixRange = ByteRange.parse("bytes=-50");
        ByteRange contentRange = ByteRange.parseContentRange("bytes 20-29/200");
        UploadOptions uploadOptions = UploadOptions.Builder.withMaxUploadSizeBytes(1_048_576L)
                .maxUploadSizeBytesPerBlob(262_144L)
                .googleStorageBucketName("metadata-test-bucket");

        assertThat(closedRange.getStart()).isEqualTo(10L);
        assertThat(closedRange.getEnd()).isEqualTo(19L);
        assertThat(closedRange).hasToString("bytes=10-19");
        assertThat(openRange.getStart()).isEqualTo(128L);
        assertThat(openRange.hasEnd()).isFalse();
        assertThat(openRange).hasToString("bytes=128-");
        assertThat(suffixRange.getStart()).isEqualTo(-50L);
        assertThat(suffixRange.hasEnd()).isFalse();
        assertThat(contentRange).isEqualTo(new ByteRange(20, 29));
        assertThat(uploadOptions.hasMaxUploadSizeBytes()).isTrue();
        assertThat(uploadOptions.getMaxUploadSizeBytes()).isEqualTo(1_048_576L);
        assertThat(uploadOptions.hasMaxUploadSizeBytesPerBlob()).isTrue();
        assertThat(uploadOptions.getMaxUploadSizeBytesPerBlob()).isEqualTo(262_144L);
        assertThat(uploadOptions.hasGoogleStorageBucketName()).isTrue();
        assertThat(uploadOptions.getGoogleStorageBucketName()).isEqualTo("metadata-test-bucket");
        assertThatThrownBy(() -> ByteRange.parse("items=10-19"))
                .isInstanceOf(RangeFormatException.class);
    }

    @Test
    void urlFetchMailBlobstoreAndImagesValueObjectsRetainConfiguration() throws MalformedURLException {
        FetchOptions fetchOptions = FetchOptions.Builder.withDeadline(4.5)
                .doNotFollowRedirects()
                .allowTruncate()
                .validateCertificate();
        HTTPRequest request = new HTTPRequest(new URL("https://example.com/api"), HTTPMethod.POST, fetchOptions);
        HTTPHeader acceptHeader = new HTTPHeader("Accept", "application/json");
        BlobKey blobKey = new BlobKey("blob-key-1");
        Image inMemoryImage = ImagesServiceFactory.makeImage(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Image blobImage = ImagesServiceFactory.makeImageFromBlob(blobKey);
        MailService.Attachment attachment = new MailService.Attachment(
                "hello.txt", "hello".getBytes(StandardCharsets.UTF_8));
        MailService.Message message = new MailService.Message(
                "sender@example.com", "recipient@example.com", "Subject", "Plain text");

        request.setPayload("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader(acceptHeader);
        message.setReplyTo("reply@example.com");
        message.setCc("cc@example.com");
        message.setBcc("bcc@example.com");
        message.setHtmlBody("<p>Hello</p>");
        message.setAmpHtmlBody("<p>Hello AMP</p>");
        message.setAttachments(attachment);

        assertThat(request.getMethod()).isEqualTo(HTTPMethod.POST);
        assertThat(request.getURL()).hasToString("https://example.com/api");
        assertThat(request.getPayload()).containsExactly((byte) '{', (byte) '}');
        assertThat(request.getHeaders()).hasSize(1);
        assertThat(request.getHeaders().get(0).getName()).isEqualTo(acceptHeader.getName());
        assertThat(request.getHeaders().get(0).getValue()).isEqualTo(acceptHeader.getValue());
        assertThat(request.getFetchOptions().getDeadline()).isEqualTo(4.5);
        assertThat(request.getFetchOptions().getFollowRedirects()).isFalse();
        assertThat(request.getFetchOptions().getAllowTruncate()).isTrue();
        assertThat(request.getFetchOptions().getValidateCertificate()).isTrue();
        assertThat(blobKey.getKeyString()).isEqualTo("blob-key-1");
        assertThat(inMemoryImage.getImageData()).containsExactly((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(blobImage.getBlobKey()).isEqualTo(blobKey);
        assertThat(message.getSender()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getCc()).containsExactly("cc@example.com");
        assertThat(message.getBcc()).containsExactly("bcc@example.com");
        assertThat(message.getReplyTo()).isEqualTo("reply@example.com");
        assertThat(message.getHtmlBody()).isEqualTo("<p>Hello</p>");
        assertThat(message.getAmpHtmlBody()).isEqualTo("<p>Hello AMP</p>");
        assertThat(message.getAttachments()).containsExactly(attachment);
        assertThat(ImagesServiceFactory.makeResize(32, 32)).isNotNull();
    }

    @Test
    void searchDocumentsQueriesFacetsSortingAndCursorsAreComposable() {
        Date publicationDate = new Date(1_700_000_000_000L);
        Field title = Field.newBuilder()
                .setName("title")
                .setText("Native image metadata")
                .setLocale(Locale.ENGLISH)
                .build();
        Field score = Field.newBuilder().setName("score").setNumber(9.5).build();
        Field location = Field.newBuilder()
                .setName("location")
                .setGeoPoint(new GeoPoint(47.6, -122.3))
                .build();
        Facet language = Facet.withAtom("language", "java");
        Document document = Document.newBuilder()
                .setId("doc-1")
                .setLocale(Locale.ENGLISH)
                .setRank(7)
                .addField(title)
                .addField(score)
                .addField(Field.newBuilder().setName("published").setDate(publicationDate))
                .addField(location)
                .addFacet(language)
                .build();
        SortExpression sortExpression = SortExpression.newBuilder()
                .setExpression("score")
                .setDirection(SortExpression.SortDirection.DESCENDING)
                .setDefaultValueNumeric(0.0)
                .build();
        SortOptions sortOptions = SortOptions.newBuilder().setLimit(10).addSortExpression(sortExpression).build();
        Cursor cursor = Cursor.newBuilder().build("true:cursor-token");
        FieldExpression snippet = FieldExpression.newBuilder()
                .setName("summary")
                .setExpression("snippet(body)")
                .build();
        QueryOptions queryOptions = QueryOptions.newBuilder()
                .setLimit(20)
                .setCursor(cursor)
                .setSortOptions(sortOptions)
                .setFieldsToReturn("title", "score")
                .setFieldsToSnippet("body")
                .addExpressionToReturn(snippet)
                .build();
        QueryOptions offsetQueryOptions = QueryOptions.newBuilder()
                .setLimit(5)
                .setOffset(3)
                .build();

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(document.getRank()).isEqualTo(7);
        assertThat(document.getFieldNames()).contains("title", "score", "location", "published");
        assertThat(document.getFacetNames()).containsExactly("language");
        assertThat(document.getOnlyField("title").getText()).isEqualTo("Native image metadata");
        assertThat(document.getOnlyField("score").getNumber()).isEqualTo(9.5);
        assertThat(document.getOnlyField("location").getGeoPoint().getLongitude()).isEqualTo(-122.3);
        assertThat(document.getOnlyFacet("language")).isEqualTo(language);
        assertThat(queryOptions.getLimit()).isEqualTo(20);
        assertThat(offsetQueryOptions.getOffset()).isEqualTo(3);
        assertThat(queryOptions.getCursor().toWebSafeString()).contains("cursor-token");
        assertThat(queryOptions.getCursor().isPerResult()).isTrue();
        assertThat(queryOptions.getFieldsToReturn()).containsExactly("title", "score");
        assertThat(queryOptions.getFieldsToSnippet()).containsExactly("body");
        assertThat(queryOptions.getExpressionsToReturn()).containsExactly(snippet);
        assertThat(queryOptions.getSortOptions().getSortExpressions()).containsExactly(sortExpression);
    }

    private static final class TestEnvironment implements ApiProxy.Environment {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public String getAppId() {
            return "s~metadata-test";
        }

        @Override
        public String getModuleId() {
            return "default";
        }

        @Override
        public String getVersionId() {
            return "v1";
        }

        @Override
        public String getEmail() {
            return "admin@example.com";
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
            return "example.com";
        }

        @Override
        public String getRequestNamespace() {
            return "request-namespace";
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
}
