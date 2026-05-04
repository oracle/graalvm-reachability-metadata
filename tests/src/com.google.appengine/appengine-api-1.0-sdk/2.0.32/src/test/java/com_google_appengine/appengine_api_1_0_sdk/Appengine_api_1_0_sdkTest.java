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
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.FileInfo;
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
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Rating;
import com.google.appengine.api.datastore.ShortBlob;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.CompositeTransform;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.OutputSettings;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.images.Transform;
import com.google.appengine.api.mail.MailService.Attachment;
import com.google.appengine.api.mail.MailService.Header;
import com.google.appengine.api.mail.MailService.Message;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.FacetOptions;
import com.google.appengine.api.search.FacetRange;
import com.google.appengine.api.search.FacetRefinement;
import com.google.appengine.api.search.FacetRequest;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.FieldExpression;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.FetchOptions.Builder;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Appengine_api_1_0_sdkTest {
    @BeforeEach
    void registerAppEngineEnvironment() {
        ensureAppEngineEnvironment();
    }

    @AfterEach
    void clearAppEngineEnvironment() {
        ApiProxy.clearEnvironmentForCurrentThread();
    }

    @Test
    void namespaceAndDatastoreKeysRoundTripHierarchicalKeys() {
        ensureAppEngineEnvironment();
        String previousNamespace = NamespaceManager.get();
        try {
            NamespaceManager.validateNamespace("tenant-a");
            NamespaceManager.set("tenant-a");
            assertThat(NamespaceManager.get()).isEqualTo("tenant-a");

            Key accountKey = new KeyFactory.Builder("Account", "acct-1")
                    .addChild("Invoice", 42L)
                    .addChild("LineItem", "line-1")
                    .getKey();

            String encodedKey = KeyFactory.keyToString(accountKey);
            Key decodedKey = KeyFactory.stringToKey(encodedKey);

            assertThat(decodedKey).isEqualTo(accountKey);
            assertThat(decodedKey.getKind()).isEqualTo("LineItem");
            assertThat(decodedKey.getName()).isEqualTo("line-1");
            assertThat(decodedKey.getParent().getKind()).isEqualTo("Invoice");
            assertThat(decodedKey.getParent().getId()).isEqualTo(42L);
            assertThat(new KeyFactory.Builder(accountKey).getString()).isEqualTo(encodedKey);
        } finally {
            NamespaceManager.set(previousNamespace);
        }
    }

    @Test
    void datastoreEntityStoresTypedPropertiesAndClonesIndependently() {
        ensureAppEngineEnvironment();
        Key accountKey = KeyFactory.createKey("Account", "acct-1");
        Entity entity = new Entity("Invoice", "2024-0001", accountKey);
        EmbeddedEntity address = new EmbeddedEntity();
        address.setProperty("city", "Belgrade");
        address.setUnindexedProperty("notes", new Text("Leave at reception"));

        Blob blob = new Blob(new byte[] {1, 2, 3});
        ShortBlob shortBlob = new ShortBlob(new byte[] {4, 5, 6});
        User user = new User("ada@example.com", "example.com", "user-123");
        entity.setProperty("status", "OPEN");
        entity.setIndexedProperty("amount", 149L);
        entity.setUnindexedProperty("description", new Text("native-image coverage invoice"));
        entity.setProperty("blob", blob);
        entity.setProperty("shortBlob", shortBlob);
        entity.setProperty("url", new Link("https://example.com/invoices/2024-0001"));
        entity.setProperty("email", new Email("billing@example.com"));
        entity.setProperty("category", new Category("finance"));
        entity.setProperty("phone", new PhoneNumber("+381-11-123-456"));
        entity.setProperty("address", new PostalAddress("Main Street 1"));
        entity.setProperty("rating", new Rating(97));
        entity.setProperty("location", new GeoPt(44.8125F, 20.4612F));
        entity.setProperty("owner", user);
        entity.setProperty("embedded", address);

        Entity clone = entity.clone();
        clone.setProperty("status", "PAID");

        assertThat(entity.getKind()).isEqualTo("Invoice");
        assertThat(entity.getParent()).isEqualTo(accountKey);
        assertThat(entity.getProperty("status")).isEqualTo("OPEN");
        assertThat(clone.getProperty("status")).isEqualTo("PAID");
        assertThat(entity.isUnindexedProperty("description")).isTrue();
        assertThat(entity.isUnindexedProperty("amount")).isFalse();
        assertThat(((Blob) entity.getProperty("blob")).getBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(((ShortBlob) entity.getProperty("shortBlob")).getBytes())
                .containsExactly((byte) 4, (byte) 5, (byte) 6);
        assertThat(((User) entity.getProperty("owner")).getUserId()).isEqualTo("user-123");
        assertThat(((EmbeddedEntity) entity.getProperty("embedded")).getProperty("city")).isEqualTo("Belgrade");
        assertThat(entity.getProperties()).containsKeys("status", "amount", "description", "embedded");
    }

    @Test
    void datastoreQueryComposesFiltersSortsProjectionsAndFetchOptions() {
        ensureAppEngineEnvironment();
        Key ancestor = KeyFactory.createKey("Account", "acct-1");
        Filter amountFilter = new FilterPredicate("amount", FilterOperator.GREATER_THAN_OR_EQUAL, 100L);
        Filter statusFilter = new FilterPredicate("status", FilterOperator.EQUAL, "OPEN");
        CompositeFilter filter = CompositeFilterOperator.and(amountFilter, statusFilter);
        PropertyProjection amountProjection = new PropertyProjection("amount", Long.class);

        Query query = new Query("Invoice", ancestor)
                .setFilter(filter)
                .addSort("amount", SortDirection.DESCENDING)
                .addSort("createdAt", SortDirection.ASCENDING)
                .addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.ASCENDING)
                .addProjection(amountProjection)
                .setDistinct(true);
        Query reversed = query.reverse();
        FetchOptions fetchOptions = FetchOptions.Builder.withLimit(25).offset(5).chunkSize(10).prefetchSize(15);

        assertThat(query.getKind()).isEqualTo("Invoice");
        assertThat(query.getAncestor()).isEqualTo(ancestor);
        assertThat(query.getFilter()).isEqualTo(filter);
        assertThat(filter.getSubFilters()).containsExactly(amountFilter, statusFilter);
        assertThat(query.getSortPredicates()).hasSize(3);
        assertThat(reversed.getSortPredicates().get(0).getDirection()).isEqualTo(SortDirection.ASCENDING);
        assertThat(reversed.getSortPredicates().get(2).getDirection()).isEqualTo(SortDirection.ASCENDING);
        assertThat(query.getProjections()).containsExactly(amountProjection);
        assertThat(fetchOptions.getLimit()).isEqualTo(25);
        assertThat(fetchOptions.getOffset()).isEqualTo(5);
        assertThat(fetchOptions.getChunkSize()).isEqualTo(10);
        assertThat(fetchOptions.getPrefetchSize()).isEqualTo(15);
    }

    @Test
    void searchDocumentBuildsAllFieldTypesAndFacets() {
        Date publishDate = new Date(1_700_000_000_000L);
        Field title = Field.newBuilder()
                .setName("title")
                .setText("GraalVM Native Image")
                .setLocale(Locale.ENGLISH)
                .build();
        Field html = Field.newBuilder().setName("summary").setHTML("<p>Fast startup</p>").build();
        Field atom = Field.newBuilder().setName("status").setAtom("published").build();
        Field date = Field.newBuilder().setName("publishedAt").setDate(publishDate).build();
        Field rating = Field.newBuilder().setName("rating").setNumber(4.75D).build();
        Field place = Field.newBuilder().setName("place").setGeoPoint(new GeoPoint(44.8125D, 20.4612D)).build();
        Field prefix = Field.newBuilder().setName("code").setUntokenizedPrefix("APP").build();
        Field vector = Field.newBuilder().setName("embedding").setVector(Arrays.asList(0.1D, 0.2D, 0.3D)).build();

        Document document = Document.newBuilder()
                .setId("doc-1")
                .setRank(7)
                .setLocale(Locale.ENGLISH)
                .addField(title)
                .addField(html)
                .addField(atom)
                .addField(date)
                .addField(rating)
                .addField(place)
                .addField(prefix)
                .addField(vector)
                .addFacet(Facet.withAtom("category", "runtime"))
                .addFacet(Facet.withNumber("score", 9.5D))
                .build();

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getRank()).isEqualTo(7);
        assertThat(document.getLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(document.getFieldNames())
                .contains("title", "summary", "status", "publishedAt", "rating", "place", "code", "embedding");
        assertThat(document.getFacetNames()).contains("category", "score");
        assertThat(document.getOnlyField("title").getText()).isEqualTo("GraalVM Native Image");
        assertThat(document.getOnlyField("summary").getHTML()).isEqualTo("<p>Fast startup</p>");
        assertThat(document.getOnlyField("publishedAt").getDate()).isEqualTo(publishDate);
        assertThat(document.getOnlyField("rating").getNumber()).isEqualTo(4.75D);
        assertThat(document.getOnlyField("place").getGeoPoint().getLongitude()).isEqualTo(20.4612D);
        assertThat(document.getOnlyField("embedding").getVector()).containsExactly(0.1D, 0.2D, 0.3D);
        assertThat(document.getOnlyFacet("category").getAtom()).isEqualTo("runtime");
        assertThat(document.getOnlyFacet("score").getNumber()).isEqualTo(9.5D);
    }

    @Test
    void searchQueryOptionsComposeSortingExpressionsFacetsAndRefinements() {
        SortExpression sortExpression = SortExpression.newBuilder()
                .setExpression("rating")
                .setDirection(SortExpression.SortDirection.DESCENDING)
                .setDefaultValueNumeric(0D)
                .build();
        FieldExpression snippet = FieldExpression.newBuilder()
                .setName("snippet")
                .setExpression("snippet(summary, 'startup')")
                .build();
        SortOptions sortOptions = SortOptions.newBuilder()
                .setLimit(50)
                .addSortExpression(sortExpression)
                .build();
        QueryOptions options = QueryOptions.newBuilder()
                .setLimit(10)
                .setOffset(2)
                .setNumberFoundAccuracy(100)
                .setFieldsToReturn("title", "rating")
                .setFieldsToSnippet("summary")
                .addExpressionToReturn(snippet)
                .setSortOptions(sortOptions)
                .build();
        FacetRange highScoreRange = FacetRange.withStartEnd(8D, 10D);
        FacetRequest facetRequest = FacetRequest.newBuilder()
                .setName("score")
                .setValueLimit(5)
                .addRange(highScoreRange)
                .build();
        FacetRefinement refinement = FacetRefinement.withRange("score", highScoreRange);
        FacetOptions facetOptions = FacetOptions.newBuilder()
                .setDiscoveryLimit(20)
                .setDiscoveryValueLimit(10)
                .setDepth(3)
                .build();
        com.google.appengine.api.search.Query query = com.google.appengine.api.search.Query.newBuilder()
                .setOptions(options)
                .setFacetOptions(facetOptions)
                .setEnableFacetDiscovery(true)
                .addReturnFacet(facetRequest)
                .addFacetRefinement(refinement)
                .build("title:startup");

        assertThat(query.getQueryString()).isEqualTo("title:startup");
        assertThat(query.getEnableFacetDiscovery()).isTrue();
        assertThat(query.getOptions().getLimit()).isEqualTo(10);
        assertThat(query.getOptions().getOffset()).isEqualTo(2);
        assertThat(query.getOptions().getNumberFoundAccuracy()).isEqualTo(100);
        assertThat(query.getOptions().getFieldsToReturn()).containsExactly("title", "rating");
        assertThat(query.getOptions().getFieldsToSnippet()).containsExactly("summary");
        assertThat(query.getOptions().getExpressionsToReturn()).containsExactly(snippet);
        assertThat(query.getOptions().getSortOptions().getSortExpressions()).containsExactly(sortExpression);
        assertThat(query.getFacetOptions().getDepth()).isEqualTo(3);
        assertThat(query.getReturnFacets()).containsExactly(facetRequest);
        assertThat(query.getRefinements().get(0).toTokenString()).isNotBlank();
        assertThat(FacetRefinement.fromTokenString(query.getRefinements().get(0).toTokenString()).getName())
                .isEqualTo("score");
    }

    @Test
    void taskOptionsConfigurePayloadParametersHeadersRetriesAndDeadline() throws Exception {
        RetryOptions retryOptions = RetryOptions.Builder.withTaskRetryLimit(3)
                .taskAgeLimitSeconds(3600)
                .minBackoffSeconds(1.5D)
                .maxBackoffSeconds(30D)
                .maxDoublings(4);
        TaskOptions payloadOptions = TaskOptions.Builder.withUrl("/tasks/process")
                .taskName("process-invoice")
                .method(TaskOptions.Method.POST)
                .payload("payload", StandardCharsets.UTF_8.name())
                .header("Content-Type", "text/plain")
                .header("X-Test", "native")
                .countdownMillis(250L)
                .retryOptions(retryOptions)
                .tag("invoices")
                .dispatchDeadline(Duration.ofSeconds(30));
        TaskOptions parameterOptions = TaskOptions.Builder.withUrl("/tasks/process")
                .method(TaskOptions.Method.POST)
                .param("invoice", "2024-0001")
                .param("raw", new byte[] {9, 8, 7});

        assertThat(payloadOptions.getUrl()).isEqualTo("/tasks/process");
        assertThat(payloadOptions.getTaskName()).isEqualTo("process-invoice");
        assertThat(payloadOptions.getMethod()).isEqualTo(TaskOptions.Method.POST);
        assertThat(new String(payloadOptions.getPayload(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(payloadOptions.getHeaders().get("Content-Type")).containsExactly("text/plain");
        assertThat(payloadOptions.getHeaders().get("X-Test")).containsExactly("native");
        assertThat(payloadOptions.getCountdownMillis()).isEqualTo(250L);
        assertThat(payloadOptions.getRetryOptions()).isEqualTo(retryOptions);
        assertThat(payloadOptions.getTag()).isEqualTo("invoices");
        assertThat(payloadOptions.getDispatchDeadline()).isEqualTo(Duration.ofSeconds(30));
        assertThat(parameterOptions.getStringParams()).containsKey("invoice");
        assertThat(parameterOptions.getStringParams().get("invoice")).containsExactly("2024-0001");
        assertThat(parameterOptions.getByteArrayParams().get("raw").get(0))
                .containsExactly((byte) 9, (byte) 8, (byte) 7);

        TaskOptions copied = new TaskOptions(parameterOptions).removeParam("invoice").clearParams();
        assertThat(new TaskOptions(payloadOptions).removeHeader("X-Test").getHeaders()).doesNotContainKey("X-Test");
        assertThat(copied.getStringParams()).isEmpty();
        assertThat(copied.getByteArrayParams()).isEmpty();
    }

    @Test
    void mailMessageHoldsRecipientsBodiesHeadersAndAttachments() {
        Attachment attachment = new Attachment("report.txt", "contents".getBytes(StandardCharsets.UTF_8), "cid-report");
        Header header = new Header("X-Correlation-Id", "corr-1");
        Message message = new Message("sender@example.com", "recipient@example.com", "Subject", "Plain text");
        message.setReplyTo("reply@example.com");
        message.setCc("copy@example.com");
        message.setBcc("hidden@example.com");
        message.setHtmlBody("<p>Plain text</p>");
        message.setAmpHtmlBody("<html amp4email><body>AMP</body></html>");
        message.setAttachments(attachment);
        message.setHeaders(header);

        assertThat(message.getSender()).isEqualTo("sender@example.com");
        assertThat(message.getReplyTo()).isEqualTo("reply@example.com");
        assertThat(message.getTo()).containsExactly("recipient@example.com");
        assertThat(message.getCc()).containsExactly("copy@example.com");
        assertThat(message.getBcc()).containsExactly("hidden@example.com");
        assertThat(message.getSubject()).isEqualTo("Subject");
        assertThat(message.getTextBody()).isEqualTo("Plain text");
        assertThat(message.getHtmlBody()).isEqualTo("<p>Plain text</p>");
        assertThat(message.getAmpHtmlBody()).contains("amp4email");
        assertThat(message.getAttachments()).containsExactly(attachment);
        assertThat(attachment.getFileName()).isEqualTo("report.txt");
        assertThat(attachment.getData()).containsExactly("contents".getBytes(StandardCharsets.UTF_8));
        assertThat(attachment.getContentID()).isEqualTo("cid-report");
        assertThat(message.getHeaders()).containsExactly(header);
        assertThat(header.getName()).isEqualTo("X-Correlation-Id");
        assertThat(header.getValue()).isEqualTo("corr-1");
    }

    @Test
    void usersServiceReadsCurrentUserStateFromEnvironment() {
        ensureAppEngineEnvironment();
        UserService userService = UserServiceFactory.getUserService();
        User currentUser = userService.getCurrentUser();

        assertThat(userService.isUserLoggedIn()).isTrue();
        assertThat(userService.isUserAdmin()).isFalse();
        assertThat(currentUser).isNotNull();
        assertThat(currentUser.getEmail()).isEqualTo("tester@example.com");
        assertThat(currentUser.getAuthDomain()).isEqualTo("example.com");
        assertThat(currentUser.getUserId()).isNull();
    }

    @Test
    void urlFetchRequestResponseAndOptionsAreConfigurableWithoutNetworkAccess() throws Exception {
        URL url = new URL("https://example.com/api");
        com.google.appengine.api.urlfetch.FetchOptions options = Builder.withDeadline(5.0D)
                .allowTruncate()
                .doNotFollowRedirects()
                .validateCertificate();
        HTTPRequest request = new HTTPRequest(url, HTTPMethod.POST, options);
        request.setPayload("request-body".getBytes(StandardCharsets.UTF_8));
        request.addHeader(new HTTPHeader("Accept", "application/json"));
        request.setHeader(new HTTPHeader("Content-Type", "text/plain"));

        List<HTTPHeader> responseHeaders = Arrays.asList(
                new HTTPHeader("Content-Type", "application/json"),
                new HTTPHeader("X-Request-Id", "req-1"));
        HTTPResponse response = new HTTPResponse(
                202, "accepted".getBytes(StandardCharsets.UTF_8), url, responseHeaders);

        assertThat(request.getURL()).isEqualTo(url);
        assertThat(request.getMethod()).isEqualTo(HTTPMethod.POST);
        assertThat(request.getFetchOptions().getDeadline()).isEqualTo(5.0D);
        assertThat(request.getFetchOptions().getAllowTruncate()).isTrue();
        assertThat(request.getFetchOptions().getFollowRedirects()).isFalse();
        assertThat(request.getFetchOptions().getValidateCertificate()).isTrue();
        assertThat(new String(request.getPayload(), StandardCharsets.UTF_8)).isEqualTo("request-body");
        assertThat(headerNames(request.getHeaders())).contains("Accept", "Content-Type");
        assertThat(response.getResponseCode()).isEqualTo(202);
        assertThat(new String(response.getContent(), StandardCharsets.UTF_8)).isEqualTo("accepted");
        assertThat(response.getFinalUrl()).isEqualTo(url);
        assertThat(response.getHeadersUncombined()).containsExactlyElementsOf(responseHeaders);
    }

    @Test
    void blobstoreImagesAndMemcacheValueObjectsExposeConfiguredState() {
        Date created = new Date(1_700_000_123_000L);
        BlobKey blobKey = new BlobKey("blob-123");
        BlobInfo blobInfo = new BlobInfo(blobKey, "image/png", created, "image.png", 128L, "md5", "/bucket/image.png");
        FileInfo fileInfo = new FileInfo("image/png", created, "image.png", 128L, "md5", "/bucket/image.png");
        Image image = ImagesServiceFactory.makeImage(new byte[] {(byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'});
        Transform resize = ImagesServiceFactory.makeResize(64, 32, true);
        Transform crop = ImagesServiceFactory.makeCrop(0.1D, 0.2D, 0.8D, 0.9D);
        CompositeTransform compositeTransform = ImagesServiceFactory.makeCompositeTransform(
                Arrays.asList(resize, crop));
        OutputSettings outputSettings = new OutputSettings(ImagesService.OutputEncoding.PNG);
        outputSettings.setQuality(85);
        ServingUrlOptions servingUrlOptions = ServingUrlOptions.Builder.withBlobKey(blobKey)
                .secureUrl(true)
                .crop(true)
                .imageSize(512);
        long beforeExpirationSeconds = System.currentTimeMillis() / 1000L;
        Expiration expiration = Expiration.byDeltaSeconds(30);
        long afterExpirationSeconds = System.currentTimeMillis() / 1000L;

        assertThat(blobKey.getKeyString()).isEqualTo("blob-123");
        assertThat(blobInfo.getBlobKey()).isEqualTo(blobKey);
        assertThat(blobInfo.getFilename()).isEqualTo("image.png");
        assertThat(blobInfo.getContentType()).isEqualTo("image/png");
        assertThat(blobInfo.getSize()).isEqualTo(128L);
        assertThat(blobInfo.getMd5Hash()).isEqualTo("md5");
        assertThat(blobInfo.getGsObjectName()).isEqualTo("/bucket/image.png");
        assertThat(fileInfo.getContentType()).isEqualTo("image/png");
        assertThat(fileInfo.getCreation()).isEqualTo(created);
        assertThat(fileInfo.getFilename()).isEqualTo("image.png");
        assertThat(fileInfo.getGsObjectName()).isEqualTo("/bucket/image.png");
        assertThat(image.getImageData()).containsExactly((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(compositeTransform).isNotNull();
        assertThat(outputSettings.getOutputEncoding()).isEqualTo(ImagesService.OutputEncoding.PNG);
        assertThat(outputSettings.hasQuality()).isTrue();
        assertThat(outputSettings.getQuality()).isEqualTo(85);
        assertThat(servingUrlOptions)
                .isEqualTo(ServingUrlOptions.Builder.withBlobKey(blobKey).secureUrl(true).crop(true).imageSize(512));
        assertThat(expiration.getSecondsValue())
                .isBetween((int) beforeExpirationSeconds + 30, (int) afterExpirationSeconds + 30);
        assertThat(Expiration.byDeltaMillis(30_000).getMillisecondsValue()).isGreaterThanOrEqualTo(30_000L);
        assertThatThrownBy(() -> new Rating(Rating.MAX_VALUE + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Collection<String> headerNames(List<HTTPHeader> headers) {
        return headers.stream().map(HTTPHeader::getName).toList();
    }

    private static void ensureAppEngineEnvironment() {
        if (ApiProxy.getCurrentEnvironment() == null) {
            ApiProxy.setEnvironmentForCurrentThread(new TestEnvironment());
        }
    }

    @SuppressWarnings("deprecation")
    private static final class TestEnvironment implements ApiProxy.Environment {
        private final Map<String, Object> attributes = new HashMap<>();

        @Override
        public String getAppId() {
            return "dev~test-app";
        }

        @Override
        public String getModuleId() {
            return "default";
        }

        @Override
        public String getVersionId() {
            return "test-version";
        }

        @Override
        public String getEmail() {
            return "tester@example.com";
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
            return 60_000L;
        }
    }
}
