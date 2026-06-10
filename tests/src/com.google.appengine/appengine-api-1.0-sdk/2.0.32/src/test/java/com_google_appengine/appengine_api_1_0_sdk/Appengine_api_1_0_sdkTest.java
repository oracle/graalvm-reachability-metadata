/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_appengine.appengine_api_1_0_sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityServicePb;
import com.google.appengine.api.capabilities.CapabilityState;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.GeoPt;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.mail.MailServicePb;
import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.api.search.Cursor;
import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Facet;
import com.google.appengine.api.search.FacetOptions;
import com.google.appengine.api.search.FacetRange;
import com.google.appengine.api.search.FacetRefinement;
import com.google.appengine.api.search.FacetRequest;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.GeoPoint;
import com.google.appengine.api.search.QueryOptions;
import com.google.appengine.api.search.SortExpression;
import com.google.appengine.api.search.SortOptions;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskQueuePb;
import com.google.apphosting.api.ApiProxy;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public class Appengine_api_1_0_sdkTest {
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/"
                    + "p9sAAAAASUVORK5CYII=");

    @Test
    void datastoreEntitiesKeysQueriesAndFetchOptionsExposeConfiguredState() {
        ApiProxy.setEnvironmentForCurrentThread(new TestEnvironment());
        try {
            Key parent = KeyFactory.createKey("Account", 42L);
            Key child = KeyFactory.createKey(parent, "Order", "order-1");
            String encoded = KeyFactory.keyToString(child);

            Entity entity = new Entity(child);
            entity.setProperty("email", "customer@example.com");
            entity.setProperty("status", "paid");
            entity.setProperty("location", new GeoPt(45.25f, 19.85f));
            entity.setUnindexedProperty("notes", new Text("paid at counter"));
            EmbeddedEntity embedded = new EmbeddedEntity();
            embedded.setProperty("sku", "sku-1");
            embedded.setProperty("quantity", 2L);
            entity.setProperty("lineItem", embedded);

            Query.Filter statusFilter = new Query.FilterPredicate(
                    "status", Query.FilterOperator.EQUAL, "paid");
            Query.Filter totalFilter = new Query.FilterPredicate(
                    "total", Query.FilterOperator.GREATER_THAN_OR_EQUAL, 10L);
            Query.CompositeFilter filter = Query.CompositeFilterOperator.and(
                    statusFilter, totalFilter);
            Query query = new Query("Order", parent)
                    .setFilter(filter)
                    .addSort("created", Query.SortDirection.DESCENDING)
                    .addProjection(new PropertyProjection("email", String.class))
                    .setDistinct(true);
            FetchOptions fetchOptions = FetchOptions.Builder.withLimit(25)
                    .offset(5)
                    .chunkSize(10)
                    .prefetchSize(10);

            assertThat(KeyFactory.stringToKey(encoded)).isEqualTo(child);
            assertThat(entity.getKey()).isEqualTo(child);
            assertThat(entity.getProperty("email")).isEqualTo("customer@example.com");
            assertThat(entity.isUnindexedProperty("notes")).isTrue();
            EmbeddedEntity lineItem = (EmbeddedEntity) entity.getProperty("lineItem");
            assertThat(lineItem.getProperty("sku")).isEqualTo("sku-1");
            assertThat(query.getKind()).isEqualTo("Order");
            assertThat(query.getAncestor()).isEqualTo(parent);
            assertThat(query.getFilter()).isEqualTo(filter);
            assertThat(query.getSortPredicates()).hasSize(1);
            assertThat(query.getProjections())
                    .containsExactly(new PropertyProjection("email", String.class));
            assertThat(query.getDistinct()).isTrue();
            assertThat(fetchOptions.getLimit()).isEqualTo(25);
            assertThat(fetchOptions.getOffset()).isEqualTo(5);
            assertThat(fetchOptions.getChunkSize()).isEqualTo(10);
            assertThat(fetchOptions.getPrefetchSize()).isEqualTo(10);
        } finally {
            ApiProxy.clearEnvironmentForCurrentThread();
        }
    }

    @Test
    void searchDocumentsQueriesFacetsAndSortOptionsBuildRichRequests() {
        Date publishDate = new Date(1_700_000_000_000L);
        Document document = Document.newBuilder()
                .setId("doc-1")
                .setRank(7)
                .addField(Field.newBuilder().setName("title").setText("App Engine search"))
                .addField(Field.newBuilder().setName("category").setAtom("docs"))
                .addField(Field.newBuilder().setName("published").setDate(publishDate))
                .addField(Field.newBuilder().setName("rating").setNumber(4.5))
                .addField(Field.newBuilder().setName("place")
                        .setGeoPoint(new GeoPoint(45.25, 19.85)))
                .addFacet(Facet.withAtom("type", "guide"))
                .addFacet(Facet.withNumber("priority", 3.0))
                .build();
        SortOptions sortOptions = SortOptions.newBuilder()
                .addSortExpression(SortExpression.newBuilder()
                        .setExpression("rating")
                        .setDirection(SortExpression.SortDirection.DESCENDING)
                        .setDefaultValueNumeric(0.0))
                .setLimit(50)
                .build();
        QueryOptions options = QueryOptions.newBuilder()
                .setLimit(10)
                .setCursor(Cursor.newBuilder().build())
                .setSortOptions(sortOptions)
                .setFieldsToReturn("title", "rating")
                .setFieldsToSnippet("title")
                .build();
        com.google.appengine.api.search.Query query = com.google.appengine.api.search.Query
                .newBuilder()
                .setOptions(options)
                .setEnableFacetDiscovery(true)
                .setFacetOptions(FacetOptions.newBuilder()
                        .setDiscoveryLimit(5)
                        .setDiscoveryValueLimit(3))
                .addReturnFacet(FacetRequest.newBuilder()
                        .setName("type")
                        .addValueConstraint("guide")
                        .build())
                .addFacetRefinement(FacetRefinement.withRange(
                        "priority", FacetRange.withStartEnd(1.0, 5.0)))
                .build("title:search");

        assertThat(document.getId()).isEqualTo("doc-1");
        assertThat(document.getRank()).isEqualTo(7);
        assertThat(document.getFields()).hasSize(5);
        assertThat(document.getOnlyField("title").getText()).isEqualTo("App Engine search");
        assertThat(document.getOnlyField("category").getAtom()).isEqualTo("docs");
        assertThat(document.getOnlyField("published").getDate()).isEqualTo(publishDate);
        assertThat(document.getOnlyField("rating").getNumber()).isEqualTo(4.5);
        assertThat(document.getOnlyField("place").getGeoPoint().getLatitude()).isEqualTo(45.25);
        assertThat(document.getFacets()).hasSize(2);
        assertThat(query.getQueryString()).isEqualTo("title:search");
        assertThat(query.getEnableFacetDiscovery()).isTrue();
        assertThat(query.getOptions().getLimit()).isEqualTo(10);
        assertThat(query.getOptions().getFieldsToReturn()).containsExactly("title", "rating");
        assertThat(query.getOptions().getFieldsToSnippet()).containsExactly("title");
        assertThat(query.getReturnFacets()).hasSize(1);
        assertThat(query.getRefinements()).hasSize(1);
    }

    @Test
    void blobRangesImagesAndMailMessagesValidateValueObjects() {
        ByteRange bounded = ByteRange.parse("bytes=10-19");
        ByteRange suffix = ByteRange.parse("bytes=-25");
        ByteRange contentRange = ByteRange.parseContentRange("bytes 20-29/100");
        BlobKey blobKey = new BlobKey("blob-key-1");
        Date creation = new Date(123_456L);
        BlobInfo blobInfo = new BlobInfo(
                blobKey,
                "image/png",
                creation,
                "pixel.png",
                ONE_PIXEL_PNG.length,
                "md5",
                "/bucket/pixel.png");
        Image image = ImagesServiceFactory.makeImage(ONE_PIXEL_PNG);
        MailService.Attachment attachment = new MailService.Attachment(
                "pixel.png", ONE_PIXEL_PNG, "pixel-content-id");
        MailService.Header header = new MailService.Header("X-Test", "true");
        MailService.Message message = new MailService.Message(
                "sender@example.com", "to@example.com", "Subject", "Text");
        message.setReplyTo("reply@example.com");
        message.setHtmlBody("<b>Text</b>");
        message.setAmpHtmlBody("<amp-img></amp-img>");
        message.setAttachments(Collections.singletonList(attachment));
        message.setHeaders(Collections.singletonList(header));

        assertThat(bounded.getStart()).isEqualTo(10L);
        assertThat(bounded.getEnd()).isEqualTo(19L);
        assertThat(suffix.hasEnd()).isFalse();
        assertThat(suffix.getStart()).isEqualTo(-25L);
        assertThat(contentRange.toString()).isEqualTo("bytes=20-29");
        assertThat(blobInfo.getBlobKey()).isEqualTo(blobKey);
        assertThat(blobInfo.getContentType()).isEqualTo("image/png");
        assertThat(blobInfo.getFilename()).isEqualTo("pixel.png");
        assertThat(blobInfo.getGsObjectName()).isEqualTo("/bucket/pixel.png");
        assertThat(image.getWidth()).isEqualTo(1);
        assertThat(image.getHeight()).isEqualTo(1);
        assertThat(image.getFormat()).isEqualTo(Image.Format.PNG);
        assertThat(message.getTo()).containsExactly("to@example.com");
        assertThat(message.getAttachments()).containsExactly(attachment);
        assertThat(message.getHeaders()).containsExactly(header);
        assertThat(message.getReplyTo()).isEqualTo("reply@example.com");
        assertThatThrownBy(() -> ByteRange.parse("items=1-2"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported unit");
    }

    @Test
    void taskOptionsAndMemcacheSerializationRoundTripApplicationObjects() throws Exception {
        GreetingTask deferredTask = new GreetingTask("hello");
        TaskOptions taskOptions = TaskOptions.Builder.withTaskName("task-1")
                .countdownMillis(Duration.ofSeconds(5).toMillis())
                .param("recipient", "user@example.com")
                .header("X-App", "reachability")
                .payload(deferredTask);
        MemcacheValue memcacheValue = new MemcacheValue("answer", 42);
        MemcacheSerialization.ValueAndFlags serialized = MemcacheSerialization
                .serialize(memcacheValue);
        Object deserialized = MemcacheSerialization.deserialize(
                serialized.value, serialized.flags.ordinal());

        assertThat(taskOptions.getTaskName()).isEqualTo("task-1");
        assertThat(taskOptions.getMethod()).isEqualTo(TaskOptions.Method.POST);
        assertThat(taskOptions.getUrl()).isEqualTo(DeferredTaskContext.DEFAULT_DEFERRED_URL);
        assertThat(taskOptions.getHeaders())
                .containsEntry("X-App", Collections.singletonList("reachability"));
        assertThat(taskOptions.getHeaders()).containsEntry(
                "content-type",
                Collections.singletonList(DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE));
        assertThat(taskOptions.getStringParams())
                .containsEntry("recipient", Collections.singletonList("user@example.com"));
        assertThat(taskOptions.getPayload()).isNotEmpty();
        assertThat(deserialized).isEqualTo(memcacheValue);
        assertThat(new String(MemcacheSerialization.makePbKey("short-key"), StandardCharsets.UTF_8))
                .isEqualTo("\"short-key\"");
    }

    @Test
    void apiProxyBackedFactoriesMarshalRequestsThroughDelegate() throws Exception {
        ApiProxy.Delegate<?> previousDelegate = ApiProxy.getDelegate();
        RecordingDelegate delegate = new RecordingDelegate();
        ApiProxy.setEnvironmentForCurrentThread(new TestEnvironment());
        ApiProxy.setDelegate(delegate);
        try {
            MailService.Message message = new MailService.Message(
                    "sender@example.com", "to@example.com", "Subject", "Body");
            message.setCc(Collections.singletonList("copy@example.com"));
            byte[] note = "note".getBytes(StandardCharsets.UTF_8);
            message.setAttachments(Collections.singletonList(
                    new MailService.Attachment("note.txt", note)));
            MailServiceFactory.getMailService().send(message);
            MailServicePb.MailMessage mailRequest = MailServicePb.MailMessage
                    .parseFrom(delegate.lastRequest);

            CapabilityState datastoreWrite = CapabilitiesServiceFactory.getCapabilitiesService()
                    .getStatus(Capability.DATASTORE_WRITE);
            CapabilityServicePb.IsEnabledRequest capabilityRequest =
                    CapabilityServicePb.IsEnabledRequest.parseFrom(delegate.lastRequest);

            Queue queue = QueueFactory.getDefaultQueue();
            TaskHandle handle = queue.add(TaskOptions.Builder.withUrl("/worker")
                    .taskName("job-1")
                    .payload("work"));
            TaskQueuePb.TaskQueueBulkAddRequest taskRequest = TaskQueuePb.TaskQueueBulkAddRequest
                    .parseFrom(delegate.lastRequest);

            assertThat(delegate.calls)
                    .containsExactly(
                            "mail.Send",
                            "capability_service.IsEnabled",
                            "taskqueue.BulkAdd");
            assertThat(mailRequest.getSender()).isEqualTo("sender@example.com");
            assertThat(mailRequest.getToList()).containsExactly("to@example.com");
            assertThat(mailRequest.getCcList()).containsExactly("copy@example.com");
            assertThat(mailRequest.getAttachment(0).getFileName()).isEqualTo("note.txt");
            assertThat(datastoreWrite.getStatus()).isEqualTo(CapabilityStatus.ENABLED);
            assertThat(capabilityRequest.getPackage())
                    .isEqualTo(Capability.DATASTORE_WRITE.getPackageName());
            assertThat(capabilityRequest.getCapabilityList())
                    .containsExactly(Capability.DATASTORE_WRITE.getName());
            assertThat(handle.getName()).isEqualTo("job-1");
            assertThat(handle.getQueueName()).isEqualTo(Queue.DEFAULT_QUEUE);
            assertThat(taskRequest.getAddRequest(0).getQueueName().toStringUtf8())
                    .isEqualTo(Queue.DEFAULT_QUEUE);
            assertThat(taskRequest.getAddRequest(0).getUrl().toStringUtf8()).isEqualTo("/worker");
        } finally {
            ApiProxy.setDelegate(previousDelegate);
            ApiProxy.clearEnvironmentForCurrentThread();
        }
    }

    private static final class GreetingTask implements DeferredTask {
        private static final long serialVersionUID = 1L;

        private final String message;

        private GreetingTask(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            assertThat(message).isNotBlank();
        }
    }

    private static final class MemcacheValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        private MemcacheValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof MemcacheValue)) {
                return false;
            }
            MemcacheValue that = (MemcacheValue) object;
            return count == that.count && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + count;
        }
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
            return "v1.1";
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
            return "gmail.com";
        }

        @Override
        @SuppressWarnings("deprecation")
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

    private static final class RecordingDelegate implements ApiProxy.Delegate<TestEnvironment> {
        private final List<String> calls = new java.util.ArrayList<>();
        private byte[] lastRequest = new byte[0];

        @Override
        public byte[] makeSyncCall(
                TestEnvironment environment,
                String packageName,
                String methodName,
                byte[] request) {
            calls.add(packageName + "." + methodName);
            lastRequest = request.clone();
            if ("capability_service".equals(packageName)) {
                return CapabilityServicePb.IsEnabledResponse.newBuilder()
                        .setSummaryStatus(
                                CapabilityServicePb.IsEnabledResponse.SummaryStatus.ENABLED)
                        .build()
                        .toByteArray();
            }
            return new byte[0];
        }

        @Override
        public Future<byte[]> makeAsyncCall(
                TestEnvironment environment,
                String packageName,
                String methodName,
                byte[] request,
                ApiProxy.ApiConfig apiConfig) {
            calls.add(packageName + "." + methodName);
            lastRequest = request.clone();
            if ("taskqueue".equals(packageName)) {
                try {
                    TaskQueuePb.TaskQueueBulkAddRequest addRequest =
                            TaskQueuePb.TaskQueueBulkAddRequest.parseFrom(request);
                    TaskQueuePb.TaskQueueBulkAddResponse.Builder response =
                            TaskQueuePb.TaskQueueBulkAddResponse.newBuilder();
                    for (TaskQueuePb.TaskQueueAddRequest task : addRequest.getAddRequestList()) {
                        response.addTaskResult(TaskQueuePb.TaskQueueBulkAddResponse.TaskResult
                                .newBuilder()
                                .setResult(TaskQueuePb.TaskQueueServiceError.ErrorCode.OK)
                                .setChosenTaskName(task.getTaskName()));
                    }
                    return CompletableFuture.completedFuture(response.build().toByteArray());
                } catch (Exception ex) {
                    CompletableFuture<byte[]> failed = new CompletableFuture<>();
                    failed.completeExceptionally(ex);
                    return failed;
                }
            }
            return CompletableFuture.completedFuture(
                    makeSyncCall(environment, packageName, methodName, request));
        }

        @Override
        public void log(TestEnvironment environment, ApiProxy.LogRecord record) {
        }

        @Override
        public void flushLogs(TestEnvironment environment) {
        }

        @Override
        public List<Thread> getRequestThreads(TestEnvironment environment) {
            return Collections.singletonList(Thread.currentThread());
        }
    }
}
