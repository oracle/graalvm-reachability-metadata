/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

class S3Test {

    @Test
    void configurationAndUtilitiesSupportPathStyleUrlsAndUriParsing() {
        S3Configuration configuration = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .checksumValidationEnabled(false)
            .multiRegionEnabled(true)
            .build();

        S3Configuration copiedConfiguration = configuration.toBuilder()
            .multiRegionEnabled(false)
            .build();

        assertThat(configuration.pathStyleAccessEnabled()).isTrue();
        assertThat(configuration.chunkedEncodingEnabled()).isFalse();
        assertThat(configuration.checksumValidationEnabled()).isFalse();
        assertThat(configuration.multiRegionEnabled()).isTrue();
        assertThat(copiedConfiguration.multiRegionEnabled()).isFalse();

        RecordingHttpClient httpClient = new RecordingHttpClient();
        try (S3Client client = createClient(
            httpClient,
            URI.create("https://s3.us-west-2.amazonaws.com"),
            builder -> builder.pathStyleAccessEnabled(true)
        )) {
            URL pathStyleUrl = client.utilities().getUrl(GetUrlRequest.builder()
                .bucket("docs-bucket")
                .key("reports/quarterly summary.txt")
                .build());

            assertThat(pathStyleUrl.toString())
                .isEqualTo("https://s3.us-west-2.amazonaws.com/docs-bucket/reports/quarterly%20summary.txt");

            S3Uri parsedPathStyleUri = client.utilities().parseUri(URI.create(
                "https://s3.us-west-2.amazonaws.com/docs-bucket/reports/quarterly%20summary.txt?versionId=v1&partNumber=7"));

            assertThat(parsedPathStyleUri.bucket()).hasValue("docs-bucket");
            assertThat(parsedPathStyleUri.key()).hasValue("reports/quarterly summary.txt");
            assertThat(parsedPathStyleUri.region()).hasValue(Region.US_WEST_2);
            assertThat(parsedPathStyleUri.isPathStyle()).isTrue();
            assertThat(parsedPathStyleUri.firstMatchingRawQueryParameter("versionId")).hasValue("v1");
            assertThat(parsedPathStyleUri.firstMatchingRawQueryParameter("partNumber")).hasValue("7");

            S3Uri parsedVirtualHostUri = client.utilities().parseUri(URI.create(
                "https://docs-bucket.s3.us-west-2.amazonaws.com/reports/archive.txt?x-id=GetObject"));

            assertThat(parsedVirtualHostUri.bucket()).hasValue("docs-bucket");
            assertThat(parsedVirtualHostUri.key()).hasValue("reports/archive.txt");
            assertThat(parsedVirtualHostUri.region()).hasValue(Region.US_WEST_2);
            assertThat(parsedVirtualHostUri.isPathStyle()).isFalse();
            assertThat(parsedVirtualHostUri.firstMatchingRawQueryParameter("x-id")).hasValue("GetObject");
        }
    }

    @Test
    void putObjectAndGetObjectAsBytesUseExpectedMarshallingAndParsing() {
        RecordingHttpClient httpClient = new RecordingHttpClient()
            .enqueue(MockHttpResponse.of(
                200,
                Map.of(
                    "ETag", List.of("\"put-etag\""),
                    "x-amz-version-id", List.of("put-version")
                ),
                new byte[0]
            ))
            .enqueue(MockHttpResponse.of(
                200,
                Map.of(
                    "Content-Type", List.of("text/plain"),
                    "Content-Length", List.of("18"),
                    "ETag", List.of("\"get-etag\""),
                    "x-amz-meta-origin", List.of("integration-test"),
                    "x-amz-version-id", List.of("get-version")
                ),
                "hello from aws sdk".getBytes(StandardCharsets.UTF_8)
            ));

        try (S3Client client = createClient(httpClient, URI.create("https://example.com"), builder -> {
        })) {
            PutObjectResponse putResponse = client.putObject(
                PutObjectRequest.builder()
                    .bucket("test-bucket")
                    .key("folder/sample.txt")
                    .contentType("text/plain")
                    .metadata(Map.of("origin", "integration-test", "purpose", "coverage"))
                    .tagging("suite=s3&mode=sync")
                    .build(),
                RequestBody.fromString("payload-body")
            );

            ResponseBytes<GetObjectResponse> getResponse = client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket("test-bucket")
                    .key("folder/sample.txt")
                    .range("bytes=0-4")
                    .responseContentType("text/plain")
                    .checksumMode(ChecksumMode.ENABLED)
                    .build()
            );

            assertThat(putResponse.eTag()).isEqualTo("\"put-etag\"");
            assertThat(putResponse.versionId()).isEqualTo("put-version");

            assertThat(getResponse.asUtf8String()).isEqualTo("hello from aws sdk");
            assertThat(getResponse.response().contentType()).isEqualTo("text/plain");
            assertThat(getResponse.response().contentLength()).isEqualTo(18L);
            assertThat(getResponse.response().eTag()).isEqualTo("\"get-etag\"");
            assertThat(getResponse.response().versionId()).isEqualTo("get-version");
            assertThat(getResponse.response().metadata()).containsEntry("origin", "integration-test");

            assertThat(httpClient.requests()).hasSize(2);

            RecordedRequest putRequest = httpClient.requests().get(0);
            assertThat(putRequest.method()).isEqualTo(SdkHttpMethod.PUT);
            assertThat(putRequest.encodedPath()).isEqualTo("/test-bucket/folder/sample.txt");
            assertThat(putRequest.headerValue("Content-Type")).hasValue("text/plain");
            assertThat(putRequest.headerValue("x-amz-meta-origin")).hasValue("integration-test");
            assertThat(putRequest.headerValue("x-amz-meta-purpose")).hasValue("coverage");
            assertThat(putRequest.headerValue("x-amz-tagging")).hasValue("suite=s3&mode=sync");
            assertThat(putRequest.bodyUtf8()).isEqualTo("payload-body");

            RecordedRequest getRequest = httpClient.requests().get(1);
            assertThat(getRequest.method()).isEqualTo(SdkHttpMethod.GET);
            assertThat(getRequest.encodedPath()).isEqualTo("/test-bucket/folder/sample.txt");
            assertThat(getRequest.headerValue("Range")).hasValue("bytes=0-4");
            assertThat(getRequest.headerValue("x-amz-checksum-mode")).hasValue("ENABLED");
            assertThat(getRequest.queryValue("response-content-type")).hasValue("text/plain");
        }
    }

    @Test
    void listObjectsV2PaginatorTraversesAllPages() {
        RecordingHttpClient httpClient = new RecordingHttpClient()
            .enqueue(MockHttpResponse.xml(200, """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>logs-bucket</Name>
                  <Prefix>logs/</Prefix>
                  <MaxKeys>1</MaxKeys>
                  <KeyCount>1</KeyCount>
                  <IsTruncated>true</IsTruncated>
                  <NextContinuationToken>token-2</NextContinuationToken>
                  <Contents>
                    <Key>logs/app.log</Key>
                    <LastModified>2024-03-10T12:00:00.000Z</LastModified>
                    <ETag>"etag-1"</ETag>
                    <Size>12</Size>
                    <StorageClass>STANDARD</StorageClass>
                  </Contents>
                  <CommonPrefixes>
                    <Prefix>logs/archive/</Prefix>
                  </CommonPrefixes>
                </ListBucketResult>
                """))
            .enqueue(MockHttpResponse.xml(200, """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>logs-bucket</Name>
                  <Prefix>logs/</Prefix>
                  <MaxKeys>1</MaxKeys>
                  <KeyCount>1</KeyCount>
                  <IsTruncated>false</IsTruncated>
                  <Contents>
                    <Key>logs/archive/app-2.log</Key>
                    <LastModified>2024-03-10T12:01:00.000Z</LastModified>
                    <ETag>"etag-2"</ETag>
                    <Size>18</Size>
                    <StorageClass>STANDARD</StorageClass>
                  </Contents>
                </ListBucketResult>
                """));

        try (S3Client client = createClient(httpClient, URI.create("https://example.com"), builder -> {
        })) {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket("logs-bucket")
                .prefix("logs/")
                .delimiter("/")
                .maxKeys(1)
                .build();

            List<ListObjectsV2Response> pages = client.listObjectsV2Paginator(request).stream().toList();
            List<String> keys = pages.stream()
                .flatMap(page -> page.contents().stream())
                .map(S3Object::key)
                .toList();
            List<String> commonPrefixes = pages.stream()
                .flatMap(page -> page.commonPrefixes().stream())
                .map(CommonPrefix::prefix)
                .toList();

            assertThat(pages).hasSize(2);
            assertThat(pages.get(0).nextContinuationToken()).isEqualTo("token-2");
            assertThat(pages.get(1).isTruncated()).isFalse();
            assertThat(keys).containsExactly("logs/app.log", "logs/archive/app-2.log");
            assertThat(commonPrefixes).containsExactly("logs/archive/");

            assertThat(httpClient.requests()).hasSize(2);

            RecordedRequest firstRequest = httpClient.requests().get(0);
            assertThat(firstRequest.method()).isEqualTo(SdkHttpMethod.GET);
            assertThat(firstRequest.encodedPath()).isEqualTo("/logs-bucket");
            assertThat(firstRequest.queryValue("list-type")).hasValue("2");
            assertThat(firstRequest.queryValue("prefix")).hasValue("logs/");
            assertThat(firstRequest.queryValue("delimiter")).hasValue("/");
            assertThat(firstRequest.queryValue("max-keys")).hasValue("1");
            assertThat(firstRequest.queryValue("continuation-token")).isEmpty();

            RecordedRequest secondRequest = httpClient.requests().get(1);
            assertThat(secondRequest.queryValue("continuation-token")).hasValue("token-2");
        }
    }

    @Test
    void deleteObjectsMarshalsBatchDeleteRequestAndParsesMixedResults() {
        RecordingHttpClient httpClient = new RecordingHttpClient()
            .enqueue(MockHttpResponse.xml(200, """
                <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Deleted>
                    <Key>docs/quarterly-report.txt</Key>
                    <VersionId>version-1</VersionId>
                    <DeleteMarker>true</DeleteMarker>
                    <DeleteMarkerVersionId>marker-1</DeleteMarkerVersionId>
                  </Deleted>
                  <Error>
                    <Key>docs/protected.txt</Key>
                    <VersionId>version-2</VersionId>
                    <Code>AccessDenied</Code>
                    <Message>Access Denied</Message>
                  </Error>
                </DeleteResult>
                """));

        try (S3Client client = createClient(httpClient, URI.create("https://example.com"), builder -> {
        })) {
            DeleteObjectsResponse deleteResponse = client.deleteObjects(request -> request
                .bucket("docs-bucket")
                .delete(Delete.builder()
                    .objects(List.of(
                        ObjectIdentifier.builder()
                            .key("docs/quarterly-report.txt")
                            .versionId("version-1")
                            .build(),
                        ObjectIdentifier.builder()
                            .key("docs/protected.txt")
                            .versionId("version-2")
                            .build()
                    ))
                    .quiet(true)
                    .build()));

            assertThat(deleteResponse.deleted()).hasSize(1);
            DeletedObject deletedObject = deleteResponse.deleted().get(0);
            assertThat(deletedObject.key()).isEqualTo("docs/quarterly-report.txt");
            assertThat(deletedObject.versionId()).isEqualTo("version-1");
            assertThat(deletedObject.deleteMarker()).isTrue();
            assertThat(deletedObject.deleteMarkerVersionId()).isEqualTo("marker-1");

            assertThat(deleteResponse.errors()).hasSize(1);
            S3Error error = deleteResponse.errors().get(0);
            assertThat(error.key()).isEqualTo("docs/protected.txt");
            assertThat(error.versionId()).isEqualTo("version-2");
            assertThat(error.code()).isEqualTo("AccessDenied");
            assertThat(error.message()).isEqualTo("Access Denied");

            assertThat(httpClient.requests()).hasSize(1);

            RecordedRequest deleteRequest = httpClient.requests().get(0);
            assertThat(deleteRequest.method()).isEqualTo(SdkHttpMethod.POST);
            assertThat(deleteRequest.encodedPath()).isEqualTo("/docs-bucket");
            assertThat(deleteRequest.queryParameters().containsKey("delete")).isTrue();
            assertThat(deleteRequest.headerValue("Content-MD5")).hasValue(md5Base64(deleteRequest.body()));
            assertThat(deleteRequest.bodyUtf8()).contains("<Delete xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">")
                .contains("<Quiet>true</Quiet>")
                .contains("<Key>docs/quarterly-report.txt</Key>")
                .contains("<VersionId>version-1</VersionId>")
                .contains("<Key>docs/protected.txt</Key>")
                .contains("<VersionId>version-2</VersionId>");
        }
    }

    @Test
    void multipartUploadOperationsMarshalXmlPayloadsAndParseResponses() {
        RecordingHttpClient httpClient = new RecordingHttpClient()
            .enqueue(MockHttpResponse.xml(200, """
                <InitiateMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Bucket>media-bucket</Bucket>
                  <Key>videos/demo.mp4</Key>
                  <UploadId>upload-123</UploadId>
                </InitiateMultipartUploadResult>
                """))
            .enqueue(MockHttpResponse.of(
                200,
                Map.of("ETag", List.of("\"part-etag\"")),
                new byte[0]
            ))
            .enqueue(MockHttpResponse.xml(200, """
                <CompleteMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Location>https://example.com/media-bucket/videos/demo.mp4</Location>
                  <Bucket>media-bucket</Bucket>
                  <Key>videos/demo.mp4</Key>
                  <ETag>"complete-etag"</ETag>
                </CompleteMultipartUploadResult>
                """));

        try (S3Client client = createClient(httpClient, URI.create("https://example.com"), builder -> {
        })) {
            CreateMultipartUploadResponse createResponse = client.createMultipartUpload(request -> request
                .bucket("media-bucket")
                .key("videos/demo.mp4")
                .metadata(Map.of("origin", "integration-test"))
                .tagging("stage=multipart&suite=s3"));

            UploadPartResponse uploadPartResponse = client.uploadPart(request -> request
                    .bucket("media-bucket")
                    .key("videos/demo.mp4")
                    .uploadId(createResponse.uploadId())
                    .partNumber(1),
                RequestBody.fromString("first-part"));

            String completedEtag = uploadPartResponse.eTag();
            client.completeMultipartUpload(request -> request
                .bucket("media-bucket")
                .key("videos/demo.mp4")
                .uploadId(createResponse.uploadId())
                .multipartUpload(CompletedMultipartUpload.builder()
                    .parts(CompletedPart.builder()
                        .partNumber(1)
                        .eTag(completedEtag)
                        .build())
                    .build()));

            assertThat(createResponse.bucket()).isEqualTo("media-bucket");
            assertThat(createResponse.key()).isEqualTo("videos/demo.mp4");
            assertThat(createResponse.uploadId()).isEqualTo("upload-123");
            assertThat(uploadPartResponse.eTag()).isEqualTo("\"part-etag\"");

            assertThat(httpClient.requests()).hasSize(3);

            RecordedRequest createRequest = httpClient.requests().get(0);
            assertThat(createRequest.method()).isEqualTo(SdkHttpMethod.POST);
            assertThat(createRequest.encodedPath()).isEqualTo("/media-bucket/videos/demo.mp4");
            assertThat(createRequest.queryParameters().containsKey("uploads")).isTrue();
            assertThat(createRequest.headerValue("x-amz-meta-origin")).hasValue("integration-test");
            assertThat(createRequest.headerValue("x-amz-tagging")).hasValue("stage=multipart&suite=s3");
            assertThat(createRequest.body()).isEmpty();

            RecordedRequest uploadRequest = httpClient.requests().get(1);
            assertThat(uploadRequest.method()).isEqualTo(SdkHttpMethod.PUT);
            assertThat(uploadRequest.queryValue("uploadId")).hasValue("upload-123");
            assertThat(uploadRequest.queryValue("partNumber")).hasValue("1");
            assertThat(uploadRequest.bodyUtf8()).isEqualTo("first-part");

            RecordedRequest completeRequest = httpClient.requests().get(2);
            assertThat(completeRequest.method()).isEqualTo(SdkHttpMethod.POST);
            assertThat(completeRequest.queryValue("uploadId")).hasValue("upload-123");
            assertThat(completeRequest.bodyUtf8()).contains("<CompleteMultipartUpload")
                .contains("<PartNumber>1</PartNumber>")
                .contains("<ETag>&quot;part-etag&quot;</ETag>");
        }
    }

    private static S3Client createClient(
        RecordingHttpClient httpClient,
        URI endpoint,
        Consumer<S3Configuration.Builder> configurationCustomizer
    ) {
        S3Configuration.Builder configurationBuilder = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .checksumValidationEnabled(false);
        configurationCustomizer.accept(configurationBuilder);

        return S3Client.builder()
            .region(Region.US_WEST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("access-key", "secret-key")))
            .endpointOverride(endpoint)
            .serviceConfiguration(configurationBuilder.build())
            .httpClient(httpClient)
            .build();
    }

    private record MockHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        private static MockHttpResponse of(int statusCode, Map<String, List<String>> headers, byte[] body) {
            return new MockHttpResponse(statusCode, copyMultiValueMap(headers), body.clone());
        }

        private static MockHttpResponse xml(int statusCode, String body) {
            return new MockHttpResponse(
                statusCode,
                Map.of("Content-Type", List.of("application/xml")),
                body.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private record RecordedRequest(
        SdkHttpMethod method,
        String host,
        String encodedPath,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> headers,
        byte[] body
    ) {
        private Optional<String> headerValue(String name) {
            return findFirstValue(headers, name);
        }

        private Optional<String> queryValue(String name) {
            return findFirstValue(queryParameters, name);
        }

        private String bodyUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }

        private static RecordedRequest from(SdkHttpRequest request, byte[] body) {
            return new RecordedRequest(
                request.method(),
                request.host(),
                request.encodedPath(),
                copyMultiValueMap(request.rawQueryParameters()),
                copyMultiValueMap(request.headers()),
                body.clone()
            );
        }
    }

    private static final class RecordingHttpClient implements SdkHttpClient {
        private final Deque<MockHttpResponse> queuedResponses = new ArrayDeque<>();
        private final List<RecordedRequest> requests = new ArrayList<>();

        private RecordingHttpClient enqueue(MockHttpResponse response) {
            queuedResponses.addLast(response);
            return this;
        }

        private List<RecordedRequest> requests() {
            return requests;
        }

        @Override
        public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
            return new ExecutableHttpRequest() {
                @Override
                public HttpExecuteResponse call() {
                    byte[] requestBody = request.contentStreamProvider()
                        .map(Content -> readAllBytes(Content.newStream()))
                        .orElseGet(() -> new byte[0]);
                    requests.add(RecordedRequest.from(request.httpRequest(), requestBody));

                    MockHttpResponse response = queuedResponses.pollFirst();
                    if (response == null) {
                        throw new IllegalStateException("No mock response queued for " + request.httpRequest().getUri());
                    }

                    SdkHttpFullResponse.Builder responseBuilder = SdkHttpFullResponse.builder()
                        .statusCode(response.statusCode())
                        .content(AbortableInputStream.create(new ByteArrayInputStream(response.body())));
                    response.headers().forEach(responseBuilder::putHeader);

                    return HttpExecuteResponse.builder()
                        .response(responseBuilder.build())
                        .responseBody(AbortableInputStream.create(new ByteArrayInputStream(response.body())))
                        .build();
                }

                @Override
                public void abort() {
                }
            };
        }

        @Override
        public String clientName() {
            return "recording-http-client";
        }

        @Override
        public void close() {
        }
    }

    private static Map<String, List<String>> copyMultiValueMap(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value == null ? null : new ArrayList<>(value)));
        return copy;
    }

    private static Optional<String> findFirstValue(Map<String, List<String>> values, String name) {
        return values.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .filter(value -> value != null && !value.isEmpty())
            .map(value -> value.get(0))
            .findFirst();
    }

    private static String md5Base64(byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 digest is unavailable", exception);
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) {
        try (InputStream closableInputStream = inputStream) {
            return closableInputStream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
