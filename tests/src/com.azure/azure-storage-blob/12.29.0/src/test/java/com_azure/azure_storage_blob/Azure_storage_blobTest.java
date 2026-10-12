/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_blob;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobContainerAccessPolicies;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobServiceProperties;
import com.azure.storage.blob.models.BlobServiceStatistics;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockList;
import com.azure.storage.blob.models.BlockListType;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PageList;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.PublicAccessType;
import com.azure.storage.blob.models.StorageAccountInfo;
import com.azure.storage.blob.models.TaggedBlobItem;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.blob.specialized.BlobClientBase;
import com.azure.storage.blob.specialized.BlobLeaseClient;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.specialized.PageBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Timeout(60)
public class Azure_storage_blobTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final String ACCOUNT_NAME = "integrationaccount";
    private static final String ENDPOINT = "https://" + ACCOUNT_NAME + ".blob.core.windows.net";
    private static final String CONTAINER_NAME = "documents";
    private static final String BLOB_NAME = "reports/current report.txt";
    private static final String CONTENT = "native image blob data";
    private static final String LAST_MODIFIED = "Wed, 01 May 2024 12:30:00 GMT";
    private static final PublicAccessType CONTAINER_ACCESS = PublicAccessType.CONTAINER;
    private static final StorageSharedKeyCredential CREDENTIAL = new StorageSharedKeyCredential(
            ACCOUNT_NAME, Base64.getEncoder().encodeToString("integration-secret-key".getBytes(UTF_8)));

    @Test
    void createsAndListsContainersAndReadsAccountInformation() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.empty(201),
                MockResponse.xml(200, containerListXml()),
                MockResponse.empty(200)
                        .withHeader("x-ms-sku-name", "Standard_LRS")
                        .withHeader("x-ms-account-kind", "StorageV2")
                        .withHeader("x-ms-is-hns-enabled", "true"));
        BlobServiceClient serviceClient = newServiceClient(httpClient);

        Response<BlobContainerClient> created = serviceClient.createBlobContainerWithResponse(
                CONTAINER_NAME, Map.of("owner", "platform"), CONTAINER_ACCESS, Context.NONE);
        List<BlobContainerItem> containers = serviceClient.listBlobContainers().stream().toList();
        StorageAccountInfo accountInfo = serviceClient.getAccountInfo();

        assertThat(created.getStatusCode()).isEqualTo(201);
        assertThat(created.getValue().getBlobContainerName()).isEqualTo(CONTAINER_NAME);
        assertThat(created.getValue().getBlobContainerUrl()).isEqualTo(ENDPOINT + "/" + CONTAINER_NAME);
        assertThat(serviceClient.getAccountName()).isEqualTo(ACCOUNT_NAME);
        assertThat(serviceClient.getAccountUrl()).isEqualTo(ENDPOINT);

        assertThat(containers).singleElement().satisfies(container -> {
            assertThat(container.getName()).isEqualTo(CONTAINER_NAME);
            assertThat(container.getMetadata()).containsEntry("owner", "platform");
            assertThat(container.getProperties().getETag()).isEqualTo("\"container-etag\"");
            assertThat(container.getProperties().getDefaultEncryptionScope())
                    .isEqualTo("$account-encryption-key");
        });
        assertThat(accountInfo.getSkuName().toString()).isEqualTo("Standard_LRS");
        assertThat(accountInfo.getAccountKind().toString()).isEqualTo("StorageV2");
        assertThat(accountInfo.isHierarchicalNamespaceEnabled()).isTrue();

        assertThat(httpClient.requests()).hasSize(3);
        assertRequest(httpClient.requests().get(0), HttpMethod.PUT, "/" + CONTAINER_NAME, "restype=container");
        assertThat(httpClient.requests().get(0).getHeaders().getValue("x-ms-meta-owner"))
                .isEqualTo("platform");
        assertThat(httpClient.requests().get(0).getHeaders().getValue("x-ms-blob-public-access"))
                .isEqualTo("container");
        assertRequest(httpClient.requests().get(1), HttpMethod.GET, "", "comp=list");
        assertRequest(httpClient.requests().get(2), HttpMethod.GET, "", "comp=properties");
    }

    @Test
    void readsUpdatesAndInspectsServiceConfiguration() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.xml(200, servicePropertiesXml()),
                MockResponse.empty(202),
                MockResponse.xml(200, serviceStatisticsXml()),
                MockResponse.xml(200, userDelegationKeyXml()));
        BlobServiceClient serviceClient = newServiceClient(httpClient);

        BlobServiceProperties properties = serviceClient.getProperties();
        properties.getStaticWebsite().setIndexDocument("home.html");
        serviceClient.setProperties(properties);
        BlobServiceStatistics statistics = serviceClient.getStatistics();
        OffsetDateTime keyStart = OffsetDateTime.parse("2024-05-01T11:30:00Z");
        OffsetDateTime keyExpiry = OffsetDateTime.parse("2024-05-01T13:30:00Z");
        UserDelegationKey delegationKey = serviceClient.getUserDelegationKey(keyStart, keyExpiry);

        assertThat(properties.getLogging().getVersion()).isEqualTo("1.0");
        assertThat(properties.getLogging().isRead()).isTrue();
        assertThat(properties.getLogging().isWrite()).isFalse();
        assertThat(properties.getLogging().getRetentionPolicy().getDays()).isEqualTo(7);
        assertThat(properties.getHourMetrics().isEnabled()).isTrue();
        assertThat(properties.getHourMetrics().isIncludeApis()).isTrue();
        assertThat(properties.getCors()).singleElement().satisfies(rule -> {
            assertThat(rule.getAllowedOrigins()).isEqualTo("https://portal.example");
            assertThat(rule.getAllowedMethods()).isEqualTo("GET,PUT");
            assertThat(rule.getMaxAgeInSeconds()).isEqualTo(600);
        });
        assertThat(properties.getDefaultServiceVersion()).isEqualTo("2023-11-03");
        assertThat(properties.getDeleteRetentionPolicy().getDays()).isEqualTo(14);
        assertThat(properties.getStaticWebsite().getIndexDocument()).isEqualTo("home.html");
        assertThat(statistics.getGeoReplication().getStatus().toString()).isEqualTo("live");
        assertThat(statistics.getGeoReplication().getLastSyncTime())
                .isEqualTo(OffsetDateTime.parse("2024-05-01T12:15:00Z"));
        assertThat(delegationKey.getSignedObjectId()).isEqualTo("object-id");
        assertThat(delegationKey.getSignedTenantId()).isEqualTo("tenant-id");
        assertThat(delegationKey.getSignedStart()).isEqualTo(keyStart);
        assertThat(delegationKey.getSignedExpiry()).isEqualTo(keyExpiry);
        assertThat(delegationKey.getSignedService()).isEqualTo("b");
        assertThat(delegationKey.getValue()).isEqualTo("c2lnbmluZy1rZXk=");

        assertThat(httpClient.requests()).hasSize(4);
        assertRequest(httpClient.requests().get(0), HttpMethod.GET, "", "comp=properties");
        HttpRequest updateRequest = httpClient.requests().get(1);
        assertRequest(updateRequest, HttpMethod.PUT, "", "comp=properties");
        assertThat(updateRequest.getBodyAsBinaryData().toString())
                .contains("<IndexDocument>home.html</IndexDocument>")
                .contains("<AllowedOrigins>https://portal.example</AllowedOrigins>");
        assertRequest(httpClient.requests().get(2), HttpMethod.GET, "", "comp=stats");
        HttpRequest delegationRequest = httpClient.requests().get(3);
        assertRequest(delegationRequest, HttpMethod.POST, "", "restype=service");
        assertThat(decodedQuery(delegationRequest)).contains("comp=userdelegationkey");
        assertThat(delegationRequest.getBodyAsBinaryData().toString())
                .contains("<KeyInfo>")
                .contains("<Start>2024-05-01T11:30:00Z</Start>")
                .contains("<Expiry>2024-05-01T13:30:00Z</Expiry>");
    }

    @Test
    void readsContainerPropertiesAndListsBlobHierarchy() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.empty(200)
                        .withHeader("ETag", "\"container-etag\"")
                        .withHeader("Last-Modified", LAST_MODIFIED)
                        .withHeader("x-ms-lease-status", "unlocked")
                        .withHeader("x-ms-lease-state", "available")
                        .withHeader("x-ms-lease-duration", "infinite")
                        .withHeader("x-ms-blob-public-access", "container")
                        .withHeader("x-ms-has-immutability-policy", "false")
                        .withHeader("x-ms-has-legal-hold", "false")
                        .withHeader("x-ms-default-encryption-scope", "$account-encryption-key")
                        .withHeader("x-ms-deny-encryption-scope-override", "false")
                        .withHeader("x-ms-meta-owner", "platform"),
                MockResponse.xml(200, blobHierarchyXml()));
        BlobContainerClient containerClient = newServiceClient(httpClient).getBlobContainerClient(CONTAINER_NAME);

        assertThat(containerClient.getProperties().getMetadata()).containsEntry("owner", "platform");
        ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix("reports/")
                .setMaxResultsPerPage(10)
                .setDetails(new BlobListDetails()
                        .setRetrieveMetadata(true)
                        .setRetrieveTags(true)
                        .setRetrieveVersions(true));
        List<BlobItem> items = containerClient
                .listBlobsByHierarchy("/", options, IO_TIMEOUT)
                .stream()
                .toList();

        assertThat(items).hasSize(2);
        BlobItem blob = items.get(0);
        assertThat(blob.getName()).isEqualTo("reports/current report.txt");
        assertThat(blob.isPrefix()).isFalse();
        assertThat(blob.getVersionId()).isEqualTo("version-1");
        assertThat(blob.isCurrentVersion()).isTrue();
        assertThat(blob.getMetadata()).containsEntry("owner", "platform");
        assertThat(blob.getTags()).containsEntry("environment", "integration");
        assertThat(blob.getProperties().getContentLength()).isEqualTo(22L);
        assertThat(blob.getProperties().getContentType()).isEqualTo("text/plain");
        assertThat(blob.getProperties().getBlobType().toString()).isEqualTo("BlockBlob");
        assertThat(items.get(1).getName()).isEqualTo("reports/archive/");
        assertThat(items.get(1).isPrefix()).isTrue();

        assertThat(httpClient.requests()).hasSize(2);
        assertRequest(httpClient.requests().get(0), HttpMethod.GET, "/" + CONTAINER_NAME, "restype=container");
        HttpRequest listRequest = httpClient.requests().get(1);
        assertRequest(listRequest, HttpMethod.GET, "/" + CONTAINER_NAME, "comp=list");
        assertThat(decodedQuery(listRequest))
                .contains("prefix=reports/")
                .contains("delimiter=/")
                .contains("maxresults=10")
                .contains("include=metadata,tags,versions");
    }

    @Test
    void managesContainerAccessPoliciesAndFindsTaggedBlobs() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.xml(200, accessPolicyXml()).withHeader("x-ms-blob-public-access", "container"),
                MockResponse.empty(200),
                MockResponse.xml(200, taggedBlobsXml()));
        BlobServiceClient serviceClient = newServiceClient(httpClient);
        BlobContainerClient containerClient = serviceClient.getBlobContainerClient(CONTAINER_NAME);

        BlobContainerAccessPolicies policies = containerClient.getAccessPolicy();
        containerClient.setAccessPolicy(PublicAccessType.BLOB, policies.getIdentifiers());
        List<TaggedBlobItem> taggedBlobs = serviceClient
                .findBlobsByTags("owner = 'platform'")
                .stream()
                .toList();

        assertThat(policies.getBlobAccessType()).isEqualTo(CONTAINER_ACCESS);
        assertThat(policies.getIdentifiers()).singleElement().satisfies(identifier -> {
            assertThat(identifier.getId()).isEqualTo("read-policy");
            assertThat(identifier.getAccessPolicy().getStartsOn())
                    .isEqualTo(OffsetDateTime.parse("2024-05-01T00:00:00Z"));
            assertThat(identifier.getAccessPolicy().getExpiresOn())
                    .isEqualTo(OffsetDateTime.parse("2030-05-01T00:00:00Z"));
            assertThat(identifier.getAccessPolicy().getPermissions()).isEqualTo("rl");
        });
        assertThat(taggedBlobs).singleElement().satisfies(blob -> {
            assertThat(blob.getContainerName()).isEqualTo(CONTAINER_NAME);
            assertThat(blob.getName()).isEqualTo(BLOB_NAME);
            assertThat(blob.getTags()).containsEntry("owner", "platform").containsEntry("tier", "reports");
        });

        assertThat(httpClient.requests()).hasSize(3);
        assertRequest(httpClient.requests().get(0), HttpMethod.GET, "/" + CONTAINER_NAME, "comp=acl");
        HttpRequest setPolicyRequest = httpClient.requests().get(1);
        assertRequest(setPolicyRequest, HttpMethod.PUT, "/" + CONTAINER_NAME, "comp=acl");
        assertThat(setPolicyRequest.getHeaders().getValue("x-ms-blob-public-access")).isEqualTo("blob");
        assertThat(setPolicyRequest.getBodyAsBinaryData().toString())
                .contains("<Id>read-policy</Id>")
                .contains("<Permission>rl</Permission>");
        HttpRequest findRequest = httpClient.requests().get(2);
        assertRequest(findRequest, HttpMethod.GET, "", "comp=blobs");
        assertThat(decodedQuery(findRequest)).contains("where=owner = 'platform'");
    }

    @Test
    void performsBlobContentMetadataAndTagLifecycle() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                blobMutationResponse(201, "\"upload-etag\""),
                blobContentResponse(CONTENT),
                blobContentResponse(""),
                MockResponse.empty(200),
                MockResponse.empty(204),
                MockResponse.xml(200, tagsXml()),
                MockResponse.empty(202));
        BlobClient blobClient = newBlobClient(httpClient, BLOB_NAME);

        blobClient.upload(BinaryData.fromString(CONTENT), true);
        BinaryData downloaded = blobClient.downloadContent();
        BlobProperties properties = blobClient.getProperties();
        blobClient.setMetadata(Map.of("owner", "storage-team", "format", "text"));
        blobClient.setTags(Map.of("environment", "integration", "suite", "native"));
        Map<String, String> tags = blobClient.getTags();
        blobClient.delete();

        assertThat(downloaded.toString()).isEqualTo(CONTENT);
        assertThat(properties.getETag()).isEqualTo("blob-etag");
        assertThat(properties.getBlobSize()).isEqualTo(22L);
        assertThat(properties.getContentType()).isEqualTo("text/plain");
        assertThat(properties.getBlobType().toString()).isEqualTo("BlockBlob");
        assertThat(properties.isServerEncrypted()).isTrue();
        assertThat(properties.getMetadata()).containsEntry("owner", "platform");
        assertThat(properties.getTagCount()).isEqualTo(2L);
        assertThat(tags)
                .containsEntry("environment", "integration")
                .containsEntry("suite", "native");

        assertThat(httpClient.requests()).hasSize(7);
        HttpRequest uploadRequest = httpClient.requests().get(0);
        assertRequest(uploadRequest, HttpMethod.PUT, encodedBlobPath(), null);
        assertThat(uploadRequest.getHeaders().getValue("x-ms-blob-type")).isEqualTo("BlockBlob");
        assertThat(uploadRequest.getBodyAsBinaryData().toString()).isEqualTo(CONTENT);
        assertRequest(httpClient.requests().get(1), HttpMethod.GET, encodedBlobPath(), null);
        assertRequest(httpClient.requests().get(2), HttpMethod.HEAD, encodedBlobPath(), null);
        assertThat(httpClient.requests().get(3).getHeaders().getValue("x-ms-meta-owner"))
                .isEqualTo("storage-team");
        assertThat(httpClient.requests().get(3).getHeaders().getValue("x-ms-meta-format"))
                .isEqualTo("text");
        assertThat(decodedQuery(httpClient.requests().get(3))).contains("comp=metadata");
        assertThat(decodedQuery(httpClient.requests().get(4))).contains("comp=tags");
        assertThat(httpClient.requests().get(4).getBodyAsBinaryData().toString())
                .contains("<Key>environment</Key>")
                .contains("<Value>integration</Value>");
        assertThat(decodedQuery(httpClient.requests().get(5))).contains("comp=tags");
        assertRequest(httpClient.requests().get(6), HttpMethod.DELETE, encodedBlobPath(), null);
    }

    @Test
    void createsBlobSnapshotWithMetadataAndRequestConditions() {
        String snapshotId = "2024-05-01T12:30:00.0000000Z";
        RecordingHttpClient httpClient = new RecordingHttpClient(MockResponse.empty(201)
                .withHeader("ETag", "\"snapshot-etag\"")
                .withHeader("Last-Modified", LAST_MODIFIED)
                .withHeader("x-ms-snapshot", snapshotId)
                .withHeader("x-ms-request-server-encrypted", "true"));
        BlobClient blobClient = newBlobClient(httpClient, BLOB_NAME);
        BlobRequestConditions conditions = new BlobRequestConditions().setIfMatch("\"blob-etag\"");

        Response<BlobClientBase> response = blobClient.createSnapshotWithResponse(
                Map.of("owner", "archive-team"), conditions, IO_TIMEOUT, Context.NONE);
        BlobClientBase snapshotClient = response.getValue();

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(snapshotClient.isSnapshot()).isTrue();
        assertThat(snapshotClient.getSnapshotId()).isEqualTo(snapshotId);
        assertThat(snapshotClient.getContainerName()).isEqualTo(CONTAINER_NAME);
        assertThat(snapshotClient.getBlobName()).isEqualTo(BLOB_NAME);
        assertThat(httpClient.requests()).hasSize(1);
        HttpRequest request = httpClient.requests().get(0);
        assertRequest(request, HttpMethod.PUT, encodedBlobPath(), "comp=snapshot");
        assertThat(request.getHeaders().getValue("x-ms-meta-owner")).isEqualTo("archive-team");
        assertThat(request.getHeaders().getValue("If-Match")).isEqualTo("\"blob-etag\"");
    }

    @Test
    void copiesBlobContentFromUrl() {
        String sourceUrl = ENDPOINT + "/archive/source-report.txt?versionid=source-version";
        String copyId = "copy-operation-1";
        RecordingHttpClient httpClient = new RecordingHttpClient(MockResponse.empty(202)
                .withHeader("ETag", "\"copied-etag\"")
                .withHeader("Last-Modified", LAST_MODIFIED)
                .withHeader("x-ms-copy-id", copyId)
                .withHeader("x-ms-copy-status", "success")
                .withHeader("x-ms-version-id", "destination-version"));
        BlobClient blobClient = newBlobClient(httpClient, BLOB_NAME);

        assertThat(blobClient.copyFromUrl(sourceUrl)).isEqualTo(copyId);

        assertThat(httpClient.requests()).hasSize(1);
        HttpRequest request = httpClient.requests().get(0);
        assertRequest(request, HttpMethod.PUT, encodedBlobPath(), null);
        assertThat(request.getHeaders().getValue("x-ms-copy-source")).isEqualTo(sourceUrl);
        assertThat(request.getHeaders().getValue("x-ms-requires-sync")).isEqualTo("true");
    }

    @Test
    void stagesCommitsAndListsBlocks() {
        String blockId = Base64.getEncoder().encodeToString("block-0001".getBytes(UTF_8));
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.empty(201),
                blobMutationResponse(201, "\"committed-etag\""),
                MockResponse.xml(200, blockListXml(blockId)));
        BlockBlobClient blockBlobClient = newBlobClient(httpClient, "multipart.bin").getBlockBlobClient();

        blockBlobClient.stageBlock(blockId, BinaryData.fromString("first block"));
        assertThat(blockBlobClient.commitBlockList(List.of(blockId)).getETag())
                .isEqualTo("committed-etag");
        BlockList blocks = blockBlobClient.listBlocks(BlockListType.ALL);

        assertThat(blocks.getCommittedBlocks()).singleElement().satisfies(block -> {
            assertThat(block.getName()).isEqualTo(blockId);
            assertThat(block.getSizeLong()).isEqualTo(11L);
        });
        assertThat(blocks.getUncommittedBlocks()).isEmpty();
        assertThat(httpClient.requests()).hasSize(3);
        assertThat(decodedQuery(httpClient.requests().get(0))).contains("comp=block").contains("blockid=" + blockId);
        assertThat(httpClient.requests().get(0).getBodyAsBinaryData().toString()).isEqualTo("first block");
        assertThat(decodedQuery(httpClient.requests().get(1))).contains("comp=blocklist");
        assertThat(httpClient.requests().get(1).getBodyAsBinaryData().toString())
                .contains("<Latest>" + blockId + "</Latest>");
        assertThat(decodedQuery(httpClient.requests().get(2))).contains("comp=blocklist").contains("blocklisttype=all");
    }

    @Test
    void usesAppendAndPageBlobSpecializedClients() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                appendResponse(201, "\"append-create-etag\"", "0", "0"),
                appendResponse(201, "\"append-etag\"", "12", "1"),
                pageResponse(201, "\"page-create-etag\"", "0"),
                pageResponse(201, "\"page-write-etag\"", "1"),
                MockResponse.xml(200, pageListXml()));
        BlobContainerClient containerClient = newServiceClient(httpClient).getBlobContainerClient(CONTAINER_NAME);
        AppendBlobClient appendBlobClient = containerClient.getBlobClient("audit.log").getAppendBlobClient();
        PageBlobClient pageBlobClient = containerClient.getBlobClient("disk.vhd").getPageBlobClient();

        assertThat(appendBlobClient.create(true).getETag()).isEqualTo("append-create-etag");
        byte[] appendContent = "audit record".getBytes(UTF_8);
        assertThat(appendBlobClient
                        .appendBlock(new ByteArrayInputStream(appendContent), appendContent.length)
                        .getBlobCommittedBlockCount())
                .isEqualTo(1);

        assertThat(pageBlobClient.create(512, true).getETag()).isEqualTo("page-create-etag");
        byte[] page = new byte[512];
        page[0] = 42;
        PageRange range = new PageRange().setStart(0).setEnd(511);
        assertThat(pageBlobClient.uploadPages(range, new ByteArrayInputStream(page)).getBlobSequenceNumber())
                .isEqualTo(1L);
        PageList pageList = pageBlobClient.getPageRanges(new BlobRange(0, 512L));

        assertThat(pageList.getPageRange()).singleElement().satisfies(returnedRange -> {
            assertThat(returnedRange.getStart()).isZero();
            assertThat(returnedRange.getEnd()).isEqualTo(511L);
        });
        assertThat(pageList.getClearRange()).singleElement().satisfies(clearRange -> {
            assertThat(clearRange.getStart()).isEqualTo(1024L);
            assertThat(clearRange.getEnd()).isEqualTo(1535L);
        });

        assertThat(httpClient.requests()).hasSize(5);
        assertThat(httpClient.requests().get(0).getHeaders().getValue("x-ms-blob-type"))
                .isEqualTo("AppendBlob");
        assertThat(decodedQuery(httpClient.requests().get(1))).contains("comp=appendblock");
        assertThat(httpClient.requests().get(2).getHeaders().getValue("x-ms-blob-type"))
                .isEqualTo("PageBlob");
        assertThat(httpClient.requests().get(2).getHeaders().getValue("x-ms-blob-content-length"))
                .isEqualTo("512");
        assertThat(httpClient.requests().get(3).getHeaders().getValue("x-ms-page-write"))
                .isEqualTo("update");
        assertThat(httpClient.requests().get(3).getHeaders().getValue("x-ms-range"))
                .isEqualTo("bytes=0-511");
        assertThat(decodedQuery(httpClient.requests().get(4))).contains("comp=pagelist");
    }

    @Test
    void acquiresRenewsChangesAndReleasesBlobLease() {
        String proposedLeaseId = "11111111-1111-1111-1111-111111111111";
        String changedLeaseId = "22222222-2222-2222-2222-222222222222";
        RecordingHttpClient httpClient = new RecordingHttpClient(
                MockResponse.empty(201).withHeader("x-ms-lease-id", proposedLeaseId),
                MockResponse.empty(200).withHeader("x-ms-lease-id", proposedLeaseId),
                MockResponse.empty(200).withHeader("x-ms-lease-id", changedLeaseId),
                MockResponse.empty(200));
        BlobLeaseClient leaseClient = new BlobLeaseClientBuilder()
                .blobClient(newBlobClient(httpClient, BLOB_NAME))
                .leaseId(proposedLeaseId)
                .buildClient();

        assertThat(leaseClient.acquireLease(15)).isEqualTo(proposedLeaseId);
        assertThat(leaseClient.getLeaseId()).isEqualTo(proposedLeaseId);
        assertThat(leaseClient.renewLease()).isEqualTo(proposedLeaseId);
        assertThat(leaseClient.changeLease(changedLeaseId)).isEqualTo(changedLeaseId);
        assertThat(leaseClient.getLeaseId()).isEqualTo(changedLeaseId);
        leaseClient.releaseLease();

        assertThat(httpClient.requests()).hasSize(4);
        assertLeaseRequest(httpClient.requests().get(0), "acquire");
        assertThat(httpClient.requests().get(0).getHeaders().getValue("x-ms-lease-duration"))
                .isEqualTo("15");
        assertThat(httpClient.requests().get(0).getHeaders().getValue("x-ms-proposed-lease-id"))
                .isEqualTo(proposedLeaseId);
        assertLeaseRequest(httpClient.requests().get(1), "renew");
        assertThat(httpClient.requests().get(1).getHeaders().getValue("x-ms-lease-id"))
                .isEqualTo(proposedLeaseId);
        assertLeaseRequest(httpClient.requests().get(2), "change");
        assertThat(httpClient.requests().get(2).getHeaders().getValue("x-ms-lease-id"))
                .isEqualTo(proposedLeaseId);
        assertThat(httpClient.requests().get(2).getHeaders().getValue("x-ms-proposed-lease-id"))
                .isEqualTo(changedLeaseId);
        assertLeaseRequest(httpClient.requests().get(3), "release");
        assertThat(httpClient.requests().get(3).getHeaders().getValue("x-ms-lease-id"))
                .isEqualTo(changedLeaseId);
    }

    @Test
    void generatesSignedSasAndMapsStorageErrors() {
        RecordingHttpClient httpClient = new RecordingHttpClient(MockResponse.xml(404, errorXml()));
        BlobClient blobClient = newBlobClient(httpClient, BLOB_NAME);
        BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true).setWritePermission(true);
        BlobServiceSasSignatureValues values =
                new BlobServiceSasSignatureValues(OffsetDateTime.parse("2035-05-01T12:30:00Z"), permissions)
                        .setStartTime(OffsetDateTime.parse("2035-05-01T11:30:00Z"))
                        .setContentType("text/plain");

        String sas = URLDecoder.decode(blobClient.generateSas(values), UTF_8);

        assertThat(sas).contains("sp=rw").contains("sr=b").contains("sig=").contains("rsct=text/plain");
        assertThatThrownBy(blobClient::getProperties)
                .isInstanceOf(BlobStorageException.class)
                .hasMessageContaining("BlobNotFound")
                .hasMessageContaining("does not exist");
        assertThat(httpClient.requests()).hasSize(1);
        assertRequest(httpClient.requests().get(0), HttpMethod.HEAD, encodedBlobPath(), null);
    }

    private static BlobServiceClient newServiceClient(HttpClient httpClient) {
        return new BlobServiceClientBuilder()
                .endpoint(ENDPOINT)
                .credential(CREDENTIAL)
                .httpClient(httpClient)
                .buildClient();
    }

    private static BlobClient newBlobClient(HttpClient httpClient, String blobName) {
        return newServiceClient(httpClient)
                .getBlobContainerClient(CONTAINER_NAME)
                .getBlobClient(blobName);
    }

    private static void assertRequest(HttpRequest request, HttpMethod method, String path, String queryPart) {
        assertThat(request.getHttpMethod()).isEqualTo(method);
        assertThat(URLDecoder.decode(request.getUrl().getPath(), UTF_8)).isEqualTo(path);
        assertThat(request.getHeaders().getValue("Authorization")).startsWith("SharedKey " + ACCOUNT_NAME + ":");
        if (queryPart != null) {
            assertThat(decodedQuery(request)).contains(queryPart);
        }
    }

    private static String decodedQuery(HttpRequest request) {
        String query = request.getUrl().getQuery();
        return query == null ? "" : URLDecoder.decode(query, UTF_8);
    }

    private static void assertLeaseRequest(HttpRequest request, String action) {
        assertRequest(request, HttpMethod.PUT, encodedBlobPath(), "comp=lease");
        assertThat(request.getHeaders().getValue("x-ms-lease-action")).isEqualTo(action);
    }

    private static String encodedBlobPath() {
        return "/" + CONTAINER_NAME + "/" + BLOB_NAME;
    }

    private static MockResponse blobMutationResponse(int status, String etag) {
        return MockResponse.empty(status)
                .withHeader("ETag", etag)
                .withHeader("Last-Modified", LAST_MODIFIED)
                .withHeader("x-ms-request-server-encrypted", "true")
                .withHeader("x-ms-version-id", "version-1");
    }

    private static MockResponse blobContentResponse(String content) {
        return MockResponse.binary(200, "text/plain", content.getBytes(UTF_8))
                .withHeader("ETag", "\"blob-etag\"")
                .withHeader("Last-Modified", LAST_MODIFIED)
                .withHeader("x-ms-creation-time", "Wed, 01 May 2024 12:00:00 GMT")
                .withHeader("x-ms-blob-type", "BlockBlob")
                .withHeader("x-ms-server-encrypted", "true")
                .withHeader("x-ms-access-tier", "Hot")
                .withHeader("x-ms-meta-owner", "platform")
                .withHeader("x-ms-tag-count", "2")
                .withHeader("x-ms-version-id", "version-1")
                .withHeader("x-ms-is-current-version", "true")
                .withHeader("Content-Length", "22");
    }

    private static MockResponse appendResponse(int status, String etag, String offset, String blockCount) {
        return blobMutationResponse(status, etag)
                .withHeader("x-ms-blob-append-offset", offset)
                .withHeader("x-ms-blob-committed-block-count", blockCount);
    }

    private static MockResponse pageResponse(int status, String etag, String sequenceNumber) {
        return blobMutationResponse(status, etag).withHeader("x-ms-blob-sequence-number", sequenceNumber);
    }

    private static String servicePropertiesXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <StorageServiceProperties>
                  <Logging>
                    <Version>1.0</Version><Delete>true</Delete><Read>true</Read><Write>false</Write>
                    <RetentionPolicy><Enabled>true</Enabled><Days>7</Days></RetentionPolicy>
                  </Logging>
                  <HourMetrics>
                    <Version>1.0</Version><Enabled>true</Enabled><IncludeAPIs>true</IncludeAPIs>
                    <RetentionPolicy><Enabled>true</Enabled><Days>7</Days></RetentionPolicy>
                  </HourMetrics>
                  <MinuteMetrics>
                    <Version>1.0</Version><Enabled>false</Enabled><IncludeAPIs>false</IncludeAPIs>
                    <RetentionPolicy><Enabled>false</Enabled></RetentionPolicy>
                  </MinuteMetrics>
                  <Cors><CorsRule>
                    <AllowedOrigins>https://portal.example</AllowedOrigins>
                    <AllowedMethods>GET,PUT</AllowedMethods><AllowedHeaders>*</AllowedHeaders>
                    <ExposedHeaders>x-ms-request-id</ExposedHeaders><MaxAgeInSeconds>600</MaxAgeInSeconds>
                  </CorsRule></Cors>
                  <DefaultServiceVersion>2023-11-03</DefaultServiceVersion>
                  <DeleteRetentionPolicy><Enabled>true</Enabled><Days>14</Days></DeleteRetentionPolicy>
                  <StaticWebsite>
                    <Enabled>true</Enabled><IndexDocument>index.html</IndexDocument>
                    <ErrorDocument404Path>404.html</ErrorDocument404Path>
                  </StaticWebsite>
                </StorageServiceProperties>
                """;
    }

    private static String serviceStatisticsXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <StorageServiceStats>
                  <GeoReplication>
                    <Status>live</Status><LastSyncTime>Wed, 01 May 2024 12:15:00 GMT</LastSyncTime>
                  </GeoReplication>
                </StorageServiceStats>
                """;
    }

    private static String userDelegationKeyXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <UserDelegationKey>
                  <SignedOid>object-id</SignedOid><SignedTid>tenant-id</SignedTid>
                  <SignedStart>2024-05-01T11:30:00Z</SignedStart>
                  <SignedExpiry>2024-05-01T13:30:00Z</SignedExpiry>
                  <SignedService>b</SignedService><SignedVersion>2023-11-03</SignedVersion>
                  <Value>c2lnbmluZy1rZXk=</Value>
                </UserDelegationKey>
                """;
    }

    private static String containerListXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <EnumerationResults ServiceEndpoint="https://integrationaccount.blob.core.windows.net/">
                  <Prefix></Prefix><Marker></Marker><MaxResults>5000</MaxResults>
                  <Containers>
                    <Container>
                      <Name>documents</Name>
                      <Properties>
                        <Last-Modified>Wed, 01 May 2024 12:30:00 GMT</Last-Modified>
                        <Etag>"container-etag"</Etag>
                        <LeaseStatus>unlocked</LeaseStatus><LeaseState>available</LeaseState>
                        <DefaultEncryptionScope>$account-encryption-key</DefaultEncryptionScope>
                        <DenyEncryptionScopeOverride>false</DenyEncryptionScopeOverride>
                      </Properties>
                      <Metadata><owner>platform</owner></Metadata>
                    </Container>
                  </Containers>
                  <NextMarker></NextMarker>
                </EnumerationResults>
                """;
    }

    private static String blobHierarchyXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <EnumerationResults ServiceEndpoint="https://integrationaccount.blob.core.windows.net/"
                                    ContainerName="documents">
                  <Prefix>reports/</Prefix><Marker></Marker><MaxResults>10</MaxResults><Delimiter>/</Delimiter>
                  <Blobs>
                    <Blob>
                      <Name>reports/current report.txt</Name>
                      <VersionId>version-1</VersionId><IsCurrentVersion>true</IsCurrentVersion>
                      <Properties>
                        <Creation-Time>Wed, 01 May 2024 12:00:00 GMT</Creation-Time>
                        <Last-Modified>Wed, 01 May 2024 12:30:00 GMT</Last-Modified>
                        <Etag>"blob-etag"</Etag><Content-Length>22</Content-Length>
                        <Content-Type>text/plain</Content-Type><BlobType>BlockBlob</BlobType>
                        <AccessTier>Hot</AccessTier><ServerEncrypted>true</ServerEncrypted><TagCount>1</TagCount>
                      </Properties>
                      <Metadata><owner>platform</owner></Metadata>
                      <Tags><TagSet><Tag><Key>environment</Key><Value>integration</Value></Tag></TagSet></Tags>
                    </Blob>
                    <BlobPrefix><Name>reports/archive/</Name></BlobPrefix>
                  </Blobs>
                  <NextMarker></NextMarker>
                </EnumerationResults>
                """;
    }

    private static String accessPolicyXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <SignedIdentifiers>
                  <SignedIdentifier>
                    <Id>read-policy</Id>
                    <AccessPolicy>
                      <Start>2024-05-01T00:00:00Z</Start><Expiry>2030-05-01T00:00:00Z</Expiry>
                      <Permission>rl</Permission>
                    </AccessPolicy>
                  </SignedIdentifier>
                </SignedIdentifiers>
                """;
    }

    private static String taggedBlobsXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <EnumerationResults ServiceEndpoint="https://integrationaccount.blob.core.windows.net/">
                  <Where>owner = 'platform'</Where>
                  <Blobs><Blob>
                    <Name>reports/current report.txt</Name><ContainerName>documents</ContainerName>
                    <Tags><TagSet>
                      <Tag><Key>owner</Key><Value>platform</Value></Tag>
                      <Tag><Key>tier</Key><Value>reports</Value></Tag>
                    </TagSet></Tags>
                  </Blob></Blobs>
                  <NextMarker></NextMarker>
                </EnumerationResults>
                """;
    }

    private static String tagsXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <Tags><TagSet>
                  <Tag><Key>environment</Key><Value>integration</Value></Tag>
                  <Tag><Key>suite</Key><Value>native</Value></Tag>
                </TagSet></Tags>
                """;
    }

    private static String blockListXml(String blockId) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <BlockList>
                  <CommittedBlocks><Block><Name>%s</Name><Size>11</Size></Block></CommittedBlocks>
                  <UncommittedBlocks></UncommittedBlocks>
                </BlockList>
                """.formatted(blockId);
    }

    private static String pageListXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <PageList>
                  <PageRange><Start>0</Start><End>511</End></PageRange>
                  <ClearRange><Start>1024</Start><End>1535</End></ClearRange>
                </PageList>
                """;
    }

    private static String errorXml() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <Error>
                  <Code>BlobNotFound</Code>
                  <Message>The specified blob does not exist.</Message>
                  <RequestId>request-404</RequestId>
                  <Time>2024-05-01T12:30:00Z</Time>
                </Error>
                """;
    }

    private record MockResponse(int statusCode, HttpHeaders headers, byte[] body) {
        private static MockResponse empty(int statusCode) {
            return binary(statusCode, "application/octet-stream", new byte[0]);
        }

        private static MockResponse xml(int statusCode, String body) {
            return binary(statusCode, "application/xml", body.getBytes(UTF_8));
        }

        private static MockResponse binary(int statusCode, String contentType, byte[] body) {
            byte[] copiedBody = body.clone();
            HttpHeaders headers = new HttpHeaders()
                    .set("Content-Type", contentType)
                    .set("Content-Length", Integer.toString(copiedBody.length))
                    .set("x-ms-request-id", "integration-request");
            return new MockResponse(statusCode, headers, copiedBody);
        }

        private MockResponse withHeader(String name, String value) {
            headers.set(name, value);
            return this;
        }
    }

    private static final class RecordingHttpClient implements HttpClient {
        private final Deque<MockResponse> responses = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        private RecordingHttpClient(MockResponse... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            requests.add(request.copy());
            MockResponse response = responses.pollFirst();
            if (response == null) {
                return Mono.error(new IllegalStateException("No response configured for " + request.getUrl()));
            }
            return Mono.just(new MockHttpResponse(request, response));
        }

        private List<HttpRequest> requests() {
            return requests;
        }
    }

    private static final class MockHttpResponse extends HttpResponse {
        private final int statusCode;
        private final HttpHeaders headers;
        private final byte[] body;

        private MockHttpResponse(HttpRequest request, MockResponse response) {
            super(request);
            this.statusCode = response.statusCode();
            this.headers = response.headers();
            this.body = response.body().clone();
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getHeaderValue(String name) {
            return headers.getValue(name);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Flux<ByteBuffer> getBody() {
            return Flux.defer(() -> Flux.just(ByteBuffer.wrap(body)));
        }

        @Override
        public BinaryData getBodyAsBinaryData() {
            return BinaryData.fromBytes(body);
        }

        @Override
        public Mono<byte[]> getBodyAsByteArray() {
            return Mono.just(body.clone());
        }

        @Override
        public Mono<String> getBodyAsString() {
            return Mono.just(new String(body, UTF_8));
        }

        @Override
        public Mono<String> getBodyAsString(Charset charset) {
            return Mono.just(new String(body, charset));
        }
    }
}
