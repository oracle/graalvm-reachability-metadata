/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_appengine.appengine_api_1_0_sdk;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.blobstore.FileInfo;
import com.google.appengine.api.blobstore.RangeFormatException;
import com.google.appengine.api.blobstore.UnsupportedRangeFormatException;
import com.google.appengine.api.blobstore.UploadOptions;
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
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Rating;
import com.google.appengine.api.datastore.ShortBlob;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.CompositeTransform;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.mail.BounceNotification;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.FacetOptions;
import com.google.appengine.api.search.FacetRange;
import com.google.appengine.api.search.FacetRefinement;
import com.google.appengine.api.search.FacetRequest;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.FieldExpression;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.MatchScorer;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.RescoringMatchScorer;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.taskqueue.LeaseOptions;
import com.google.appengine.api.taskqueue.RetryOptions;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.urlfetch.FetchOptions.Builder;
import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.users.User;
import com.google.apphosting.api.ApiProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Appengine_api_1_0_sdkTest {
    @Test
    void datastoreKeysEntitiesAndQueriesPreserveStructureAndProperties() {
        NamespaceManager.validateNamespace("tenant-a");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> NamespaceManager.validateNamespace("tenant!"));

        ApiProxy.Environment previousEnvironment = ApiProxy.getCurrentEnvironment();
        ApiProxy.setEnvironmentForCurrentThread(new TestEnvironment());
        try {
            NamespaceManager.set("tenant-a");
            Key accountKey = KeyFactory.createKey("Account", "acme");
            Key invoiceKey = accountKey.getChild("Invoice", 7L);
            Key decodedKey = KeyFactory.stringToKey(KeyFactory.keyToString(invoiceKey));

            assertThat(decodedKey).isEqualTo(invoiceKey);
            assertThat(invoiceKey.getParent()).isEqualTo(accountKey);
            assertThat(invoiceKey.isComplete()).isTrue();
            assertThat(invoiceKey.getKind()).isEqualTo("Invoice");
            assertThat(invoiceKey.getId()).isEqualTo(7L);
            assertThat(invoiceKey.getNamespace()).isEqualTo("tenant-a");

            EmbeddedEntity shippingAddress = new EmbeddedEntity();
            shippingAddress.setProperty("street", new PostalAddress("1600 Amphitheatre Parkway"));
            shippingAddress.setProperty("phone", new PhoneNumber("+1-650-253-0000"));

            Entity invoice = new Entity(invoiceKey);
            invoice.setIndexedProperty("email", new Email("buyer@example.com"));
            invoice.setIndexedProperty("category", new Category("software"));
            invoice.setIndexedProperty("rating", new Rating(95));
            invoice.setIndexedProperty("website", new Link("https://example.com/invoice/7"));
            invoice.setIndexedProperty("location", new GeoPt(37.422F, -122.084F));
            invoice.setUnindexedProperty("notes", new Text("Paid by wire transfer"));
            invoice.setProperty("shippingAddress", shippingAddress);

            assertThat(invoice.getKind()).isEqualTo("Invoice");
            assertThat(invoice.getParent()).isEqualTo(accountKey);
            assertThat(invoice.getProperty("notes")).isEqualTo(new Text("Paid by wire transfer"));
            assertThat(invoice.isUnindexedProperty("notes")).isTrue();
            assertThat(invoice.isUnindexedProperty("email")).isFalse();
            assertThat(invoice.getProperties()).containsKeys(
                    "email", "category", "rating", "website", "location", "notes", "shippingAddress");

            Entity copy = invoice.clone();
            assertThat(copy).isEqualTo(invoice);
            copy.removeProperty("notes");
            assertThat(copy.hasProperty("notes")).isFalse();
            assertThat(invoice.hasProperty("notes")).isTrue();

            Query.Filter filter = Query.CompositeFilterOperator.and(
                    Query.FilterOperator.EQUAL.of("category", new Category("software")),
                    Query.FilterOperator.GREATER_THAN_OR_EQUAL.of("rating", new Rating(90)));
            Query query = new Query("Invoice", accountKey)
                    .setFilter(filter)
                    .addSort("rating", Query.SortDirection.DESCENDING)
                    .addSort("email")
                    .addSort("__key__")
                    .setDistinct(true)
                    .setKeysOnly();
            Query reversed = query.reverse();

            assertThat(query.getAncestor()).isEqualTo(accountKey);
            assertThat(query.getFilter()).isEqualTo(filter);
            assertThat(query.getSortPredicates()).hasSize(3);
            assertThat(query.getSortPredicates().get(0).getDirection()).isEqualTo(Query.SortDirection.DESCENDING);
            assertThat(reversed.getSortPredicates().get(0).getDirection()).isEqualTo(Query.SortDirection.DESCENDING);
            assertThat(query.getDistinct()).isTrue();
            assertThat(query.isKeysOnly()).isTrue();

            FetchOptions fetchOptions = FetchOptions.Builder.withLimit(25)
                    .offset(5)
                    .chunkSize(10)
                    .prefetchSize(20);
            assertThat(fetchOptions.getLimit()).isEqualTo(25);
            assertThat(fetchOptions.getOffset()).isEqualTo(5);
            assertThat(fetchOptions.getChunkSize()).isEqualTo(10);
            assertThat(fetchOptions.getPrefetchSize()).isEqualTo(20);
        } finally {
            if (previousEnvironment == null) {
                ApiProxy.clearEnvironmentForCurrentThread();
            } else {
                ApiProxy.setEnvironmentForCurrentThread(previousEnvironment);
            }
        }
    }

    @Test
    void datastoreValueObjectsCompareAndValidateBounds() {
        byte[] blobBytes = new byte[] {1, 2, 3};
        Blob blob = new Blob(blobBytes);
        blobBytes[0] = 9;
        assertThat(blob.getBytes()).isSameAs(blobBytes);
        assertThat(blob.getBytes()).containsExactly(new byte[] {9, 2, 3});
        assertThat(blob).isEqualTo(new Blob(new byte[] {9, 2, 3}));

        byte[] shortBlobBytes = new byte[] {4, 5, 6};
        ShortBlob shortBlob = new ShortBlob(shortBlobBytes);
        shortBlobBytes[1] = 9;
        assertThat(shortBlob.getBytes()).containsExactly(new byte[] {4, 5, 6});
        assertThat(shortBlob.compareTo(new ShortBlob(new byte[] {4, 5, 7}))).isLessThan(0);

        GeoPt googleplex = new GeoPt(37.422F, -122.084F);
        assertThat(googleplex.getLatitude()).isEqualTo(37.422F);
        assertThat(googleplex.getLongitude()).isEqualTo(-122.084F);
        assertThat(new Email("a@example.com").compareTo(new Email("b@example.com"))).isLessThan(0);
        assertThat(new Category("alpha")).isEqualTo(new Category("alpha"));
        assertThat(new PhoneNumber("123")).isEqualTo(new PhoneNumber("123"));
        assertThat(new PostalAddress("Main Street")).isEqualTo(new PostalAddress("Main Street"));
        assertThat(new Link("https://example.com").getValue()).isEqualTo("https://example.com");
        assertThat(new Text("long text").getValue()).isEqualTo("long text");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Rating(-1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Rating(101));
    }

    @Test
    void taskqueueOptionsComposeHttpPullAndRetryConfiguration() throws Exception {
        RetryOptions retryOptions = RetryOptions.Builder.withTaskRetryLimit(4)
                .taskAgeLimitSeconds(600)
                .minBackoffSeconds(1.5)
                .maxBackoffSeconds(30.0)
                .maxDoublings(3);
        RetryOptions retryCopy = new RetryOptions(retryOptions);
        assertThat(retryCopy).isEqualTo(retryOptions);
        assertThat(retryCopy.hashCode()).isEqualTo(retryOptions.hashCode());
        assertThat(retryOptions.toString()).isNotBlank();

        TaskOptions parameterizedTask = TaskOptions.Builder.withTaskName("process-invoice")
                .url("/tasks/process-invoice")
                .method(TaskOptions.Method.POST)
                .header("X-Test", "true")
                .param("invoice", "7")
                .param("raw", new byte[] {8, 9})
                .countdownMillis(1_000L)
                .retryOptions(retryOptions)
                .dispatchDeadline(Duration.ofSeconds(30))
                .tag("billing");

        assertThat(parameterizedTask.getTaskName()).isEqualTo("process-invoice");
        assertThat(parameterizedTask.getUrl()).isEqualTo("/tasks/process-invoice");
        assertThat(parameterizedTask.getMethod()).isEqualTo(TaskOptions.Method.POST);
        assertThat(parameterizedTask.getHeaders()).containsEntry("X-Test", List.of("true"));
        assertThat(parameterizedTask.getStringParams()).containsEntry("invoice", List.of("7"));
        assertThat(parameterizedTask.getByteArrayParams().get("raw").get(0)).containsExactly(new byte[] {8, 9});
        assertThat(parameterizedTask.getCountdownMillis()).isEqualTo(1_000L);
        assertThat(parameterizedTask.getRetryOptions()).isEqualTo(retryOptions);
        assertThat(parameterizedTask.getDispatchDeadline()).isEqualTo(Duration.ofSeconds(30));
        assertThat(parameterizedTask.getTag()).isEqualTo("billing");

        TaskOptions payloadTask = TaskOptions.Builder.withPayload("payload", StandardCharsets.UTF_8.name())
                .taskName("payload-task")
                .tag("blue");
        TaskHandle handle = new TaskHandle(payloadTask, "default");
        assertThat(handle.getName()).isEqualTo("payload-task");
        assertThat(handle.getQueueName()).isEqualTo("default");
        assertThat(handle.getPayload()).containsExactly("payload".getBytes(StandardCharsets.UTF_8));
        assertThat(handle.getTag()).isEqualTo("blue");

        TaskHandle pullHandle = new TaskHandle("pull-task", "pull-queue", 123L);
        assertThat(pullHandle.getName()).isEqualTo("pull-task");
        assertThat(pullHandle.getQueueName()).isEqualTo("pull-queue");
        assertThat(pullHandle.getEtaMillis()).isEqualTo(123L);

        LeaseOptions leaseOptions = LeaseOptions.Builder.withLeasePeriod(30, TimeUnit.SECONDS)
                .countLimit(5)
                .deadlineInSeconds(2.5)
                .groupByTag()
                .tag("billing");
        assertThat(new LeaseOptions(leaseOptions)).isEqualTo(leaseOptions);
    }

    @Test
    void mailMessagesBounceNotificationsAndUrlFetchRequestsExposeConfiguredData() throws Exception {
        MailService.Attachment attachment = new MailService.Attachment(
                "invoice.txt", "amount=42".getBytes(StandardCharsets.UTF_8), "invoice-7");
        MailService.Header header = new MailService.Header("X-Correlation-Id", "corr-1");
        MailService.Message message = new MailService.Message(
                "sender@example.com", "buyer@example.com", "Invoice", "Plain text body");
        message.setReplyTo("reply@example.com");
        message.setCc("finance@example.com", "audit@example.com");
        message.setBcc(List.of("archive@example.com"));
        message.setHtmlBody("<p>Plain text body</p>");
        message.setAmpHtmlBody("<html amp4email></html>");
        message.setAttachments(attachment);
        message.setHeaders(header);

        assertThat(message.getSender()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("buyer@example.com");
        assertThat(message.getCc()).containsExactly("finance@example.com", "audit@example.com");
        assertThat(message.getBcc()).containsExactly("archive@example.com");
        assertThat(message.getAttachments()).containsExactly(attachment);
        assertThat(message.getHeaders()).containsExactly(header);
        assertThat(attachment.getFileName()).isEqualTo("invoice.txt");
        assertThat(attachment.getData()).containsExactly("amount=42".getBytes(StandardCharsets.UTF_8));
        assertThat(attachment.getContentID()).isEqualTo("invoice-7");
        assertThat(header.getName()).isEqualTo("X-Correlation-Id");
        assertThat(header.getValue()).isEqualTo("corr-1");

        BounceNotification.Details original = new BounceNotification.DetailsBuilder()
                .withFrom("sender@example.com")
                .withTo("missing@example.com")
                .withSubject("Invoice")
                .withText("Original message")
                .build();
        BounceNotification.Details notification = new BounceNotification.DetailsBuilder()
                .withFrom("mailer-daemon@example.com")
                .withTo("sender@example.com")
                .withText("User unknown")
                .build();
        BounceNotification bounce = new BounceNotification.BounceNotificationBuilder()
                .withOriginal(original)
                .withNotification(notification)
                .build();
        assertThat(bounce.getOriginal().getSubject()).isEqualTo("Invoice");
        assertThat(bounce.getNotification().getText()).isEqualTo("User unknown");
        assertThat(bounce.getRawMessage()).isNull();

        com.google.appengine.api.urlfetch.FetchOptions fetchOptions = Builder.withDeadline(5.0)
                .doNotFollowRedirects()
                .allowTruncate()
                .validateCertificate();
        HTTPRequest request = new HTTPRequest(
                new URL("https://example.com/api"), HTTPMethod.POST, fetchOptions);
        request.setPayload("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader(new HTTPHeader("Accept", "application/json"));
        request.setHeader(new HTTPHeader("Content-Type", "application/json"));

        assertThat(request.getMethod()).isEqualTo(HTTPMethod.POST);
        assertThat(request.getURL()).hasToString("https://example.com/api");
        assertThat(request.getPayload()).containsExactly((byte) '{', (byte) '}');
        assertThat(request.getFetchOptions().getDeadline()).isEqualTo(5.0);
        assertThat(request.getFetchOptions().getFollowRedirects()).isFalse();
        assertThat(request.getFetchOptions().getAllowTruncate()).isTrue();
        assertThat(request.getFetchOptions().getValidateCertificate()).isTrue();
        assertThat(request.getHeaders()).extracting(HTTPHeader::getName)
                .containsExactly("Accept", "Content-Type");

        HTTPResponse response = new HTTPResponse(
                202,
                "accepted".getBytes(StandardCharsets.UTF_8),
                new URL("https://example.com/result"),
                List.of(new HTTPHeader("X-One", "1"), new HTTPHeader("X-Two", "2")));
        assertThat(response.getResponseCode()).isEqualTo(202);
        assertThat(response.getContent()).containsExactly("accepted".getBytes(StandardCharsets.UTF_8));
        assertThat(response.getFinalUrl()).hasToString("https://example.com/result");
        assertThat(response.getHeadersUncombined()).extracting(HTTPHeader::getName)
                .containsExactly("X-One", "X-Two");
    }

    @Test
    void searchDocumentsQueriesFacetsAndSortsRetainBuilderConfiguration() {
        Date publishDate = new Date(1_700_000_000_000L);
        Field title = Field.newBuilder().setName("title").setText("Native Image guide").build();
        Field body = Field.newBuilder().setName("body").setHTML("<p>Reachability metadata</p>").build();
        Field tag = Field.newBuilder().setName("tag").setAtom("graalvm").build();
        Field date = Field.newBuilder().setName("published").setDate(publishDate).build();
        Field score = Field.newBuilder().setName("score").setNumber(9.5).build();
        Field point = Field.newBuilder()
                .setName("location")
                .setGeoPoint(new GeoPoint(37.422, -122.084))
                .build();
        Field prefix = Field.newBuilder().setName("prefix").setUntokenizedPrefix("native").build();
        Field vector = Field.newBuilder()
                .setName("embedding")
                .setVector(List.of(0.1, 0.2, 0.3))
                .setLocale(Locale.US)
                .build();
        Facet language = Facet.withAtom("language", "java");
        Facet pages = Facet.withNumber("pages", 12.0);

        Document document = Document.newBuilder()
                .setId("doc-1")
                .setLocale(Locale.US)
                .setRank(42)
                .addField(title)
                .addField(body)
                .addField(tag)
                .addField(date)
                .addField(score)
                .addField(point)
                .addField(prefix)
                .addField(vector)
                .addFacet(language)
                .addFacet(pages)
                .build();

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getLocale()).isEqualTo(Locale.US);
        assertThat(document.getRank()).isEqualTo(42);
        assertThat(document.getFieldNames()).contains("title", "body", "tag", "published", "score");
        assertThat(document.getFacetNames()).containsExactlyInAnyOrder("language", "pages");
        assertThat(document.getOnlyField("title").getText()).isEqualTo("Native Image guide");
        assertThat(document.getOnlyField("body").getHTML()).isEqualTo("<p>Reachability metadata</p>");
        assertThat(document.getOnlyField("tag").getType()).isEqualTo(Field.FieldType.ATOM);
        assertThat(document.getOnlyField("published").getDate()).isEqualTo(publishDate);
        assertThat(document.getOnlyField("score").getNumber()).isEqualTo(9.5);
        assertThat(document.getOnlyField("location").getGeoPoint().getLatitude()).isEqualTo(37.422);
        assertThat(document.getOnlyField("embedding").getVector()).containsExactly(0.1, 0.2, 0.3);
        assertThat(document.getOnlyFacet("language")).isEqualTo(language);
        assertThat(document.getFacetCount("pages")).isEqualTo(1);
        assertThat(toList(document.getFields("title"))).containsExactly(title);

        SortExpression sortExpression = SortExpression.newBuilder()
                .setExpression("score")
                .setDirection(SortExpression.SortDirection.DESCENDING)
                .setDefaultValueNumeric(0.0)
                .build();
        SortOptions sortOptions = SortOptions.newBuilder()
                .setLimit(100)
                .setMatchScorer(MatchScorer.newBuilder())
                .addSortExpression(sortExpression)
                .build();
        FieldExpression snippet = FieldExpression.newBuilder()
                .setName("summary")
                .setExpression("snippet(body, html)")
                .build();
        Cursor cursor = Cursor.newBuilder().setPerResult(true).build("true:cursor-token");
        QueryOptions offsetOnlyOptions = QueryOptions.newBuilder().setOffset(2).build();
        QueryOptions queryOptions = QueryOptions.newBuilder()
                .setLimit(10)
                .setCursor(cursor)
                .setNumberFoundAccuracy(1_000)
                .setFieldsToReturn("title", "score")
                .setFieldsToSnippet("body")
                .addExpressionToReturn(snippet)
                .setSortOptions(sortOptions)
                .build();

        assertThat(queryOptions.getLimit()).isEqualTo(10);
        assertThat(offsetOnlyOptions.getOffset()).isEqualTo(2);
        assertThat(queryOptions.getCursor().toWebSafeString()).isEqualTo("true:cursor-token");
        assertThat(queryOptions.getCursor().isPerResult()).isTrue();
        assertThat(queryOptions.hasNumberFoundAccuracy()).isTrue();
        assertThat(queryOptions.getFieldsToReturn()).containsExactly("title", "score");
        assertThat(queryOptions.getFieldsToSnippet()).containsExactly("body");
        assertThat(queryOptions.getExpressionsToReturn()).extracting(FieldExpression::getName)
                .containsExactly("summary");
        assertThat(queryOptions.getSortOptions().getSortExpressions()).extracting(SortExpression::getExpression)
                .containsExactly("score");
        assertThat(queryOptions.getSortOptions().getMatchScorer()).isNotNull();

        FacetRange highPageCount = FacetRange.withStartEnd(10.0, 20.0);
        FacetRequest facetRequest = FacetRequest.newBuilder()
                .setName("pages")
                .setValueLimit(5)
                .addRange(highPageCount)
                .build();
        FacetRequest valueFacetRequest = FacetRequest.newBuilder()
                .setName("language")
                .addValueConstraint("java")
                .build();
        FacetOptions facetOptions = FacetOptions.newBuilder()
                .setDiscoveryLimit(10)
                .setDiscoveryValueLimit(20)
                .setDepth(1_000)
                .build();
        FacetRefinement refinement = FacetRefinement.withRange("pages", highPageCount);
        com.google.appengine.api.search.Query searchQuery = com.google.appengine.api.search.Query.newBuilder()
                .setOptions(queryOptions)
                .setFacetOptions(facetOptions)
                .setEnableFacetDiscovery(true)
                .addReturnFacet(facetRequest)
                .addFacetRefinement(refinement)
                .build("title:native");

        assertThat(searchQuery.getQueryString()).isEqualTo("title:native");
        assertThat(searchQuery.getOptions().getLimit()).isEqualTo(10);
        assertThat(searchQuery.getFacetOptions().getDiscoveryLimit()).isEqualTo(10);
        assertThat(searchQuery.getEnableFacetDiscovery()).isTrue();
        assertThat(searchQuery.getReturnFacets()).extracting(FacetRequest::getName).containsExactly("pages");
        assertThat(searchQuery.getRefinements()).extracting(FacetRefinement::toTokenString)
                .containsExactly(refinement.toTokenString());
        assertThat(valueFacetRequest.getValueConstraints()).containsExactly("java");
        assertThat(FacetRefinement.fromTokenString(refinement.toTokenString()).getRange().toString())
                .isEqualTo(highPageCount.toString());
        assertThat(RescoringMatchScorer.newBuilder().build()).isNotNull();
    }

    @Test
    void blobstoreRangesUploadOptionsAndMetadataExposeConfiguredData() {
        ByteRange boundedRange = ByteRange.parse("bytes=5-12");
        assertThat(boundedRange.hasEnd()).isTrue();
        assertThat(boundedRange.getStart()).isEqualTo(5L);
        assertThat(boundedRange.getEnd()).isEqualTo(12L);
        assertThat(boundedRange).isEqualTo(new ByteRange(5L, 12L));
        assertThat(boundedRange.toString()).isEqualTo("bytes=5-12");

        ByteRange openEndedRange = ByteRange.parse("bytes=20-");
        assertThat(openEndedRange.hasEnd()).isFalse();
        assertThat(openEndedRange.getStart()).isEqualTo(20L);
        assertThat(openEndedRange.toString()).isEqualTo("bytes=20-");
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(openEndedRange::getEnd);

        ByteRange suffixRange = ByteRange.parse("bytes=-8");
        assertThat(suffixRange.hasEnd()).isFalse();
        assertThat(suffixRange.getStart()).isEqualTo(-8L);
        assertThat(suffixRange.toString()).isEqualTo("bytes=-8");

        ByteRange contentRange = ByteRange.parseContentRange("bytes 4-9/100");
        assertThat(contentRange).isEqualTo(new ByteRange(4L, 9L));
        assertThatExceptionOfType(RangeFormatException.class).isThrownBy(() -> ByteRange.parse("bytes=9-4"));
        assertThatExceptionOfType(UnsupportedRangeFormatException.class).isThrownBy(() -> ByteRange.parse("items=1-2"));

        UploadOptions uploadOptions = UploadOptions.Builder.withDefaults()
                .maxUploadSizeBytes(4_096L)
                .maxUploadSizeBytesPerBlob(2_048L)
                .googleStorageBucketName("uploads-bucket");
        assertThat(uploadOptions.hasMaxUploadSizeBytes()).isTrue();
        assertThat(uploadOptions.getMaxUploadSizeBytes()).isEqualTo(4_096L);
        assertThat(uploadOptions.hasMaxUploadSizeBytesPerBlob()).isTrue();
        assertThat(uploadOptions.getMaxUploadSizeBytesPerBlob()).isEqualTo(2_048L);
        assertThat(uploadOptions.hasGoogleStorageBucketName()).isTrue();
        assertThat(uploadOptions.getGoogleStorageBucketName()).isEqualTo("uploads-bucket");
        assertThat(uploadOptions).isEqualTo(UploadOptions.Builder.withMaxUploadSizeBytes(4_096L)
                .maxUploadSizeBytesPerBlob(2_048L)
                .googleStorageBucketName("uploads-bucket"));
        assertThat(uploadOptions.toString()).contains("uploads-bucket");

        UploadOptions defaultOptions = UploadOptions.Builder.withDefaults();
        assertThat(defaultOptions.hasMaxUploadSizeBytes()).isFalse();
        assertThat(defaultOptions.hasMaxUploadSizeBytesPerBlob()).isFalse();
        assertThat(defaultOptions.hasGoogleStorageBucketName()).isFalse();
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(defaultOptions::getMaxUploadSizeBytes);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UploadOptions.Builder.withMaxUploadSizeBytes(0L));

        Date creation = new Date(1_700_000_000_000L);
        BlobKey uploadedBlobKey = new BlobKey("uploaded-blob");
        BlobInfo blobInfo = new BlobInfo(
                uploadedBlobKey, "text/plain", creation, "invoice.txt", 42L, "md5-hash", "/gs/uploads/invoice.txt");
        assertThat(blobInfo.getBlobKey()).isEqualTo(uploadedBlobKey);
        assertThat(blobInfo.getContentType()).isEqualTo("text/plain");
        assertThat(blobInfo.getCreation()).isEqualTo(creation);
        assertThat(blobInfo.getFilename()).isEqualTo("invoice.txt");
        assertThat(blobInfo.getSize()).isEqualTo(42L);
        assertThat(blobInfo.getMd5Hash()).isEqualTo("md5-hash");
        assertThat(blobInfo.getGsObjectName()).isEqualTo("/gs/uploads/invoice.txt");
        assertThat(blobInfo).isEqualTo(new BlobInfo(
                uploadedBlobKey, "text/plain", creation, "invoice.txt", 42L, "md5-hash", "/gs/uploads/invoice.txt"));

        FileInfo fileInfo = new FileInfo(
                "text/plain", creation, "invoice.txt", 42L, "md5-hash", "/gs/uploads/invoice.txt");
        assertThat(fileInfo.getContentType()).isEqualTo("text/plain");
        assertThat(fileInfo.getCreation()).isEqualTo(creation);
        assertThat(fileInfo.getFilename()).isEqualTo("invoice.txt");
        assertThat(fileInfo.getSize()).isEqualTo(42L);
        assertThat(fileInfo.getMd5Hash()).isEqualTo("md5-hash");
        assertThat(fileInfo.getGsObjectName()).isEqualTo("/gs/uploads/invoice.txt");
        assertThat(fileInfo).isEqualTo(new FileInfo(
                "text/plain", creation, "invoice.txt", 42L, "md5-hash", "/gs/uploads/invoice.txt"));
    }

    @Test
    void supportingPublicValueTypesAndFactoriesCreateUsableObjects() {
        User user = new User("user@example.com", "example.com", "user-id-1", "https://issuer.example/id");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getAuthDomain()).isEqualTo("example.com");
        assertThat(user.getUserId()).isEqualTo("user-id-1");
        assertThat(user.getFederatedIdentity()).isEqualTo("https://issuer.example/id");
        assertThat(user.compareTo(new User("z@example.com", "example.com"))).isLessThan(0);

        PublicCertificate certificate = new PublicCertificate(
                "cert-1", "-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----");
        assertThat(certificate.getCertificateName()).isEqualTo("cert-1");
        assertThat(certificate.getX509CertificateInPemFormat()).contains("BEGIN CERTIFICATE");

        BlobKey blobKey = new BlobKey("blob-key-1");
        assertThat(blobKey.getKeyString()).isEqualTo("blob-key-1");
        assertThat(blobKey).isEqualTo(new BlobKey("blob-key-1"));
        assertThat(blobKey.compareTo(new BlobKey("blob-key-2"))).isLessThan(0);

        Capability datastoreWrite = new Capability("datastore_v3", "write");
        assertThat(datastoreWrite.getPackageName()).isEqualTo("datastore_v3");
        assertThat(datastoreWrite.getName()).isEqualTo("write");
        assertThat(datastoreWrite).isEqualTo(new Capability("datastore_v3", "write"));
        assertThat(datastoreWrite).isNotEqualTo(new Capability("memcache"));

        long expectedMinimumDeltaMillis = System.currentTimeMillis() + 60_000L;
        Expiration delta = Expiration.byDeltaSeconds(60);
        Expiration date = Expiration.onDate(new Date(1_700_000_000_000L));
        assertThat(delta.getMillisecondsValue())
                .isBetween(expectedMinimumDeltaMillis, System.currentTimeMillis() + 60_000L);
        assertThat(date.getMillisecondsValue()).isEqualTo(1_700_000_000_000L);
        assertThat(ErrorHandlers.getStrict()).isNotNull();
        assertThat(ErrorHandlers.getConsistentLogAndContinue(Level.INFO)).isNotNull();
        assertThat(ErrorHandlers.getDefault()).isNotNull();

        byte[] pngImage = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
        Image byteImage = ImagesServiceFactory.makeImage(pngImage);
        assertThat(byteImage.getImageData()).containsExactly(pngImage);
        Image blobImage = ImagesServiceFactory.makeImageFromBlob(blobKey);
        assertThat(blobImage.getBlobKey()).isEqualTo(blobKey);
        assertThat(ImagesServiceFactory.makeResize(32, 16)).isNotNull();
        assertThat(ImagesServiceFactory.makeCrop(0.0, 0.0, 1.0, 1.0)).isNotNull();
        CompositeTransform transform = ImagesServiceFactory.makeCompositeTransform()
                .concatenate(ImagesServiceFactory.makeHorizontalFlip())
                .preConcatenate(ImagesServiceFactory.makeRotate(90));
        assertThat(transform).isNotNull();
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        List<T> values = new ArrayList<>();
        for (T value : iterable) {
            values.add(value);
        }
        return values;
    }

    private static final class TestEnvironment implements ApiProxy.Environment {
        private final Map<String, Object> attributes = new HashMap<>();

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
            return "v1";
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
            return 60_000L;
        }
    }
}
