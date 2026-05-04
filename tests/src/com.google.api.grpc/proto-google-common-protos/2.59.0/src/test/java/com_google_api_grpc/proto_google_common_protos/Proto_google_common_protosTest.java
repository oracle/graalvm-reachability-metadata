/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api_grpc.proto_google_common_protos;

import com.google.api.AuthProvider;
import com.google.api.AuthRequirement;
import com.google.api.Authentication;
import com.google.api.AuthenticationRule;
import com.google.api.CustomHttpPattern;
import com.google.api.Distribution;
import com.google.api.Documentation;
import com.google.api.Http;
import com.google.api.HttpBody;
import com.google.api.HttpRule;
import com.google.api.JwtLocation;
import com.google.api.LabelDescriptor;
import com.google.api.LaunchStage;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResourceDescriptor;
import com.google.api.OAuthRequirements;
import com.google.api.ResourceDescriptor;
import com.google.api.Service;
import com.google.apps.card.v1.Card;
import com.google.apps.card.v1.TextParagraph;
import com.google.apps.card.v1.Widget;
import com.google.cloud.audit.AuditLog;
import com.google.cloud.audit.AuthenticationInfo;
import com.google.cloud.audit.AuthorizationInfo;
import com.google.cloud.audit.RequestMetadata;
import com.google.cloud.audit.ResourceLocation;
import com.google.cloud.audit.ServiceAccountDelegationInfo;
import com.google.cloud.location.ListLocationsResponse;
import com.google.cloud.location.Location;
import com.google.longrunning.Operation;
import com.google.longrunning.WaitOperationRequest;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Timestamp;
import com.google.rpc.BadRequest;
import com.google.rpc.Code;
import com.google.rpc.ErrorInfo;
import com.google.rpc.Help;
import com.google.rpc.RetryInfo;
import com.google.rpc.Status;
import com.google.rpc.context.AttributeContext;
import com.google.shopping.type.CustomAttribute;
import com.google.shopping.type.Price;
import com.google.shopping.type.Weight;
import com.google.type.Color;
import com.google.type.Date;
import com.google.type.DateTime;
import com.google.type.DayOfWeek;
import com.google.type.LatLng;
import com.google.type.Money;
import com.google.type.PhoneNumber;
import com.google.type.PostalAddress;
import com.google.type.TimeOfDay;
import com.google.type.TimeZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Proto_google_common_protosTest {
    @Test
    void buildsServiceConfigurationWithHttpResourceAndMonitoringDescriptors() {
        HttpRule primaryRule = HttpRule.newBuilder()
                .setSelector("google.example.v1.LibraryService.GetBook")
                .setGet("/v1/{name=shelves/*/books/*}")
                .setResponseBody("book")
                .addAdditionalBindings(HttpRule.newBuilder()
                        .setCustom(CustomHttpPattern.newBuilder()
                                .setKind("REPORT")
                                .setPath("/v1/{name=shelves/*/books/*}:report")
                                .build())
                        .setBody("*")
                        .build())
                .build();
        ResourceDescriptor bookResource = ResourceDescriptor.newBuilder()
                .setType("library.googleapis.com/Book")
                .addPattern("shelves/{shelf}/books/{book}")
                .setNameField("name")
                .setHistory(ResourceDescriptor.History.ORIGINALLY_SINGLE_PATTERN)
                .setPlural("books")
                .setSingular("book")
                .addStyle(ResourceDescriptor.Style.DECLARATIVE_FRIENDLY)
                .build();
        LabelDescriptor locationLabel = LabelDescriptor.newBuilder()
                .setKey("location")
                .setValueType(LabelDescriptor.ValueType.STRING)
                .setDescription("Cloud location")
                .build();
        MetricDescriptor requestMetric = MetricDescriptor.newBuilder()
                .setName("projects/library/metricDescriptors/library.googleapis.com/request_count")
                .setType("library.googleapis.com/request_count")
                .setMetricKind(MetricDescriptor.MetricKind.CUMULATIVE)
                .setValueType(MetricDescriptor.ValueType.INT64)
                .setUnit("1")
                .setDescription("Request count")
                .setDisplayName("Requests")
                .setLaunchStage(LaunchStage.GA)
                .addLabels(locationLabel)
                .addMonitoredResourceTypes("library.googleapis.com/Book")
                .setMetadata(MetricDescriptor.MetricDescriptorMetadata.newBuilder()
                        .setSamplePeriod(Duration.newBuilder().setSeconds(60).build())
                        .setIngestDelay(Duration.newBuilder().setSeconds(5).build())
                        .build())
                .build();
        MonitoredResourceDescriptor monitoredResource = MonitoredResourceDescriptor.newBuilder()
                .setType("library.googleapis.com/Book")
                .setDisplayName("Book")
                .setDescription("A monitored book resource")
                .addLabels(locationLabel)
                .setLaunchStage(LaunchStage.BETA)
                .build();
        Service service = Service.newBuilder()
                .setName("library.googleapis.com")
                .setTitle("Library API")
                .setDocumentation(Documentation.newBuilder()
                        .setSummary("Library service")
                        .setDocumentationRootUrl("https://example.test/docs")
                        .build())
                .setHttp(Http.newBuilder().addRules(primaryRule).build())
                .addMetrics(requestMetric)
                .addMonitoredResources(monitoredResource)
                .build();

        assertThat(service.getName()).isEqualTo("library.googleapis.com");
        assertThat(service.getHttp().getRules(0).getPatternCase()).isEqualTo(HttpRule.PatternCase.GET);
        assertThat(service.getHttp().getRules(0).getAdditionalBindings(0).getPatternCase())
                .isEqualTo(HttpRule.PatternCase.CUSTOM);
        assertThat(bookResource.getPatternList()).containsExactly("shelves/{shelf}/books/{book}");
        assertThat(bookResource.getStyleList()).containsExactly(ResourceDescriptor.Style.DECLARATIVE_FRIENDLY);
        assertThat(service.getMetrics(0).getMetadata().getSamplePeriod().getSeconds()).isEqualTo(60);
        assertThat(service.getMonitoredResources(0).getLabels(0)).isEqualTo(locationLabel);
    }

    @Test
    void buildsAuthenticationConfigurationForJwtProviders() {
        JwtLocation authorizationHeader = JwtLocation.newBuilder()
                .setHeader("Authorization")
                .setValuePrefix("Bearer ")
                .build();
        JwtLocation accessTokenQuery = JwtLocation.newBuilder()
                .setQuery("access_token")
                .build();
        AuthProvider googleAccountsProvider = AuthProvider.newBuilder()
                .setId("google_accounts")
                .setIssuer("https://accounts.google.com")
                .setJwksUri("https://www.googleapis.com/oauth2/v3/certs")
                .setAudiences("library.googleapis.com")
                .setAuthorizationUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .addJwtLocations(authorizationHeader)
                .addJwtLocations(accessTokenQuery)
                .build();
        AuthenticationRule authenticatedMethod = AuthenticationRule.newBuilder()
                .setSelector("google.example.v1.LibraryService.GetBook")
                .setOauth(OAuthRequirements.newBuilder()
                        .setCanonicalScopes("https://www.googleapis.com/auth/cloud-platform")
                        .build())
                .addRequirements(AuthRequirement.newBuilder()
                        .setProviderId(googleAccountsProvider.getId())
                        .setAudiences("library.googleapis.com")
                        .build())
                .build();
        AuthenticationRule publicHealthCheck = AuthenticationRule.newBuilder()
                .setSelector("google.example.v1.LibraryService.GetHealth")
                .setAllowWithoutCredential(true)
                .build();
        Authentication authentication = Authentication.newBuilder()
                .addProviders(googleAccountsProvider)
                .addRules(authenticatedMethod)
                .addRules(publicHealthCheck)
                .build();

        assertThat(authentication.getProviders(0).getJwtLocationsList()).containsExactly(
                authorizationHeader,
                accessTokenQuery);
        assertThat(authentication.getProviders(0).getJwtLocations(0).getInCase()).isEqualTo(JwtLocation.InCase.HEADER);
        assertThat(authentication.getRules(0).getOauth().getCanonicalScopes())
                .isEqualTo("https://www.googleapis.com/auth/cloud-platform");
        assertThat(authentication.getRules(0).getRequirements(0).getProviderId()).isEqualTo("google_accounts");
        assertThat(authentication.getRules(1).getAllowWithoutCredential()).isTrue();
    }

    @Test
    void buildsHttpBodyForArbitraryBinaryPayloads() {
        ByteString imageBytes = ByteString.copyFrom(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        Any checksumExtension = Any.newBuilder()
                .setTypeUrl("type.googleapis.com/google.api.HttpBody.checksum")
                .setValue(ByteString.copyFromUtf8("sha256:test-checksum"))
                .build();
        HttpBody httpBody = HttpBody.newBuilder()
                .setContentType("image/png")
                .setData(imageBytes)
                .addExtensions(checksumExtension)
                .build();

        assertThat(httpBody.getContentType()).isEqualTo("image/png");
        assertThat(httpBody.getData()).isEqualTo(imageBytes);
        assertThat(httpBody.getExtensionsList()).containsExactly(checksumExtension);
        assertThat(httpBody.toBuilder().setContentType("application/octet-stream").build().getData())
                .isEqualTo(imageBytes);
    }

    @Test
    void buildsDistributionWithBucketOptionsAndExemplars() {
        Distribution distribution = Distribution.newBuilder()
                .setCount(3)
                .setMean(12.5)
                .setSumOfSquaredDeviation(9.75)
                .setRange(Distribution.Range.newBuilder()
                        .setMin(10.0)
                        .setMax(20.0)
                        .build())
                .setBucketOptions(Distribution.BucketOptions.newBuilder()
                        .setExponentialBuckets(Distribution.BucketOptions.Exponential.newBuilder()
                                .setNumFiniteBuckets(4)
                                .setGrowthFactor(2.0)
                                .setScale(1.5)
                                .build())
                        .build())
                .addBucketCounts(0)
                .addBucketCounts(2)
                .addBucketCounts(1)
                .addExemplars(Distribution.Exemplar.newBuilder()
                        .setValue(17.25)
                        .setTimestamp(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
                        .addAttachments(Any.newBuilder()
                                .setTypeUrl("type.googleapis.com/google.protobuf.FieldMask")
                                .setValue(ByteString.copyFromUtf8("paths: trace_id"))
                                .build())
                        .build())
                .build();

        assertThat(distribution.getCount()).isEqualTo(3);
        assertThat(distribution.getRange().getMax()).isEqualTo(20.0);
        assertThat(distribution.getBucketOptions().getOptionsCase())
                .isEqualTo(Distribution.BucketOptions.OptionsCase.EXPONENTIAL_BUCKETS);
        assertThat(distribution.getBucketOptions().getExponentialBuckets().getGrowthFactor()).isEqualTo(2.0);
        assertThat(distribution.getBucketCountsList()).containsExactly(0L, 2L, 1L);
        assertThat(distribution.getExemplars(0).getAttachments(0).getTypeUrl())
                .isEqualTo("type.googleapis.com/google.protobuf.FieldMask");
    }

    @Test
    void buildsRpcStatusAndErrorDetailMessages() {
        BadRequest badRequest = BadRequest.newBuilder()
                .addFieldViolations(BadRequest.FieldViolation.newBuilder()
                        .setField("book.name")
                        .setDescription("must use shelves/{shelf}/books/{book}")
                        .build())
                .build();
        ErrorInfo errorInfo = ErrorInfo.newBuilder()
                .setReason("RESOURCE_EXHAUSTED")
                .setDomain("library.googleapis.com")
                .putMetadata("quota_limit", "read-requests-per-minute")
                .build();
        RetryInfo retryInfo = RetryInfo.newBuilder()
                .setRetryDelay(Duration.newBuilder().setSeconds(30).build())
                .build();
        Help help = Help.newBuilder()
                .addLinks(Help.Link.newBuilder()
                        .setDescription("Quota documentation")
                        .setUrl("https://example.test/quota")
                        .build())
                .build();
        Status status = Status.newBuilder()
                .setCode(Code.RESOURCE_EXHAUSTED_VALUE)
                .setMessage("quota exhausted")
                .addDetails(detail(
                        "type.googleapis.com/google.rpc.BadRequest",
                        badRequest.getDescriptorForType().getFullName()))
                .addDetails(detail("type.googleapis.com/google.rpc.ErrorInfo", errorInfo.getReason()))
                .addDetails(detail(
                        "type.googleapis.com/google.rpc.RetryInfo",
                        String.valueOf(retryInfo.getRetryDelay().getSeconds())))
                .addDetails(detail("type.googleapis.com/google.rpc.Help", help.getLinks(0).getUrl()))
                .build();

        assertThat(badRequest.getFieldViolations(0).getField()).isEqualTo("book.name");
        assertThat(errorInfo.getMetadataOrThrow("quota_limit")).isEqualTo("read-requests-per-minute");
        assertThat(retryInfo.hasRetryDelay()).isTrue();
        assertThat(help.getLinks(0).getDescription()).contains("Quota");
        assertThat(status.getCode()).isEqualTo(Code.RESOURCE_EXHAUSTED_VALUE);
        assertThat(status.getDetailsList()).extracting(Any::getTypeUrl)
                .containsExactly(
                        "type.googleapis.com/google.rpc.BadRequest",
                        "type.googleapis.com/google.rpc.ErrorInfo",
                        "type.googleapis.com/google.rpc.RetryInfo",
                        "type.googleapis.com/google.rpc.Help");
    }

    @Test
    void modelsLongRunningOperationsWithoutServiceStubs() {
        Any metadata = Any.newBuilder()
                .setTypeUrl("type.googleapis.com/google.protobuf.FieldMask")
                .setValue(ByteString.copyFromUtf8("metadata-paths"))
                .build();
        Any response = Any.newBuilder()
                .setTypeUrl("type.googleapis.com/google.protobuf.FieldMask")
                .setValue(ByteString.copyFromUtf8("response-paths"))
                .build();
        Operation successful = Operation.newBuilder()
                .setName("operations/books.import-123")
                .setMetadata(metadata)
                .setDone(true)
                .setResponse(response)
                .build();
        Operation failed = successful.toBuilder()
                .clearResponse()
                .setError(Status.newBuilder()
                        .setCode(Code.INTERNAL_VALUE)
                        .setMessage("import failed")
                        .build())
                .build();
        WaitOperationRequest request = WaitOperationRequest.newBuilder()
                .setName(successful.getName())
                .setTimeout(Duration.newBuilder().setSeconds(10).build())
                .build();

        assertThat(successful.getResultCase()).isEqualTo(Operation.ResultCase.RESPONSE);
        assertThat(successful.getResponse().getValue()).isEqualTo(ByteString.copyFromUtf8("response-paths"));
        assertThat(failed.getResultCase()).isEqualTo(Operation.ResultCase.ERROR);
        assertThat(failed.getError().getMessage()).isEqualTo("import failed");
        assertThat(request.getTimeout().getSeconds()).isEqualTo(10);
    }

    @Test
    void buildsCommonValueTypesWithOneofsAndRepeatedFields() {
        Date date = Date.newBuilder().setYear(2026).setMonth(5).setDay(4).build();
        TimeOfDay time = TimeOfDay.newBuilder().setHours(9).setMinutes(30).setSeconds(15).build();
        DateTime dateTime = DateTime.newBuilder()
                .setYear(date.getYear())
                .setMonth(date.getMonth())
                .setDay(date.getDay())
                .setHours(time.getHours())
                .setMinutes(time.getMinutes())
                .setTimeZone(TimeZone.newBuilder()
                        .setId("Europe/Belgrade")
                        .setVersion("2026a")
                        .build())
                .build();
        Color color = Color.newBuilder()
                .setRed(0.1F)
                .setGreen(0.5F)
                .setBlue(0.9F)
                .setAlpha(FloatValue.newBuilder().setValue(0.75F).build())
                .build();
        Money money = Money.newBuilder().setCurrencyCode("EUR").setUnits(12).setNanos(340_000_000).build();
        PostalAddress address = PostalAddress.newBuilder()
                .setRegionCode("RS")
                .setLanguageCode("sr")
                .setPostalCode("11000")
                .setLocality("Belgrade")
                .addAddressLines("Knez Mihailova 1")
                .addRecipients("Test Recipient")
                .build();
        PhoneNumber phoneNumber = PhoneNumber.newBuilder()
                .setShortCode(PhoneNumber.ShortCode.newBuilder()
                        .setRegionCode("RS")
                        .setNumber("19833")
                        .build())
                .setExtension("42")
                .build();
        LatLng location = LatLng.newBuilder().setLatitude(44.8125).setLongitude(20.4612).build();

        assertThat(dateTime.getTimeOffsetCase()).isEqualTo(DateTime.TimeOffsetCase.TIME_ZONE);
        assertThat(dateTime.getTimeZone().getId()).isEqualTo("Europe/Belgrade");
        assertThat(color.getAlpha().getValue()).isEqualTo(0.75F);
        assertThat(money.getCurrencyCode()).isEqualTo("EUR");
        assertThat(address.getAddressLinesList()).containsExactly("Knez Mihailova 1");
        assertThat(phoneNumber.getKindCase()).isEqualTo(PhoneNumber.KindCase.SHORT_CODE);
        assertThat(location.getLatitude()).isGreaterThan(44.0);
        assertThat(DayOfWeek.FRIDAY.getNumber()).isEqualTo(5);
    }

    @Test
    void buildsAuditAndLocationMessagesWithNestedAttributeContext() {
        AttributeContext.Request requestAttributes = AttributeContext.Request.newBuilder()
                .setId("request-1")
                .setMethod("google.example.v1.LibraryService.GetBook")
                .setPath("/v1/shelves/main/books/one")
                .putHeaders("x-goog-request-params", "name=shelves/main/books/one")
                .setTime(Timestamp.newBuilder().setSeconds(1_700_000_001L).build())
                .build();
        RequestMetadata requestMetadata = RequestMetadata.newBuilder()
                .setCallerIp("203.0.113.10")
                .setCallerSuppliedUserAgent("integration-test")
                .setRequestAttributes(requestAttributes)
                .build();
        ServiceAccountDelegationInfo delegationInfo = ServiceAccountDelegationInfo.newBuilder()
                .setPrincipalSubject("serviceAccount:test@example.iam.gserviceaccount.com")
                .build();
        AuthenticationInfo authenticationInfo = AuthenticationInfo.newBuilder()
                .setPrincipalEmail("user@example.test")
                .addServiceAccountDelegationInfo(delegationInfo)
                .setPrincipalSubject("user:user@example.test")
                .build();
        AuthorizationInfo authorizationInfo = AuthorizationInfo.newBuilder()
                .setResource("shelves/main/books/one")
                .setPermission("library.books.get")
                .setGranted(true)
                .build();
        AuditLog auditLog = AuditLog.newBuilder()
                .setServiceName("library.googleapis.com")
                .setMethodName("google.example.v1.LibraryService.GetBook")
                .setResourceName("shelves/main/books/one")
                .setResourceLocation(ResourceLocation.newBuilder().addCurrentLocations("global").build())
                .setAuthenticationInfo(authenticationInfo)
                .addAuthorizationInfo(authorizationInfo)
                .setRequestMetadata(requestMetadata)
                .setStatus(Status.newBuilder().setCode(Code.OK_VALUE).build())
                .build();
        Location location = Location.newBuilder()
                .setName("projects/demo/locations/europe-west1")
                .setLocationId("europe-west1")
                .setDisplayName("Europe West 1")
                .putLabels("tier", "regional")
                .setMetadata(Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/google.protobuf.FieldMask")
                        .setValue(ByteString.copyFromUtf8("location_metadata"))
                        .build())
                .build();
        ListLocationsResponse response = ListLocationsResponse.newBuilder()
                .addLocations(location)
                .setNextPageToken("next")
                .build();

        assertThat(auditLog.getAuthenticationInfo().getServiceAccountDelegationInfo(0).getPrincipalSubject())
                .startsWith("serviceAccount:");
        assertThat(auditLog.getAuthorizationInfo(0).getGranted()).isTrue();
        assertThat(auditLog.getRequestMetadata().getRequestAttributes().getHeadersOrThrow("x-goog-request-params"))
                .contains("shelves/main/books/one");
        assertThat(response.getLocations(0).getLabelsOrThrow("tier")).isEqualTo("regional");
        assertThat(response.getNextPageToken()).isEqualTo("next");
    }

    @Test
    void buildsCardAndShoppingTypeMessages() {
        TextParagraph paragraph = TextParagraph.newBuilder()
                .setText("Book import completed")
                .build();
        Widget widget = Widget.newBuilder()
                .setTextParagraph(paragraph)
                .setHorizontalAlignment(Widget.HorizontalAlignment.CENTER)
                .build();
        Card card = Card.newBuilder()
                .setName("cards/book-import")
                .setHeader(Card.CardHeader.newBuilder()
                        .setTitle("Library import")
                        .setSubtitle("Summary")
                        .setImageType(Widget.ImageType.CIRCLE)
                        .build())
                .addSections(Card.Section.newBuilder()
                        .setHeader("Results")
                        .addWidgets(widget)
                        .setCollapsible(true)
                        .setUncollapsibleWidgetsCount(1)
                        .build())
                .setDisplayStyle(Card.DisplayStyle.PEEK)
                .build();
        Price price = Price.newBuilder()
                .setAmountMicros(12_340_000)
                .setCurrencyCode("EUR")
                .build();
        Weight weight = Weight.newBuilder()
                .setAmountMicros(1_500_000)
                .setUnit(Weight.WeightUnit.KILOGRAM)
                .build();
        CustomAttribute attributes = CustomAttribute.newBuilder()
                .setName("book")
                .addGroupValues(CustomAttribute.newBuilder().setName("isbn").setValue("9780000000000").build())
                .addGroupValues(CustomAttribute.newBuilder().setName("format").setValue("hardcover").build())
                .build();

        assertThat(card.getHeader().getTitle()).isEqualTo("Library import");
        assertThat(card.getSections(0).getWidgets(0).getDataCase()).isEqualTo(Widget.DataCase.TEXT_PARAGRAPH);
        assertThat(card.getDisplayStyle()).isEqualTo(Card.DisplayStyle.PEEK);
        assertThat(price.getCurrencyCode()).isEqualTo("EUR");
        assertThat(weight.getUnit()).isEqualTo(Weight.WeightUnit.KILOGRAM);
        assertThat(attributes.getGroupValuesList()).extracting(CustomAttribute::getName)
                .containsExactly("isbn", "format");
    }

    @Test
    void exposesGeneratedDescriptorMetadataForIncludedProtoFiles() {
        assertThat(HttpRule.getDescriptor().getFullName()).isEqualTo("google.api.HttpRule");
        assertThat(Status.getDescriptor().getFields()).extracting(field -> field.getName())
                .contains("code", "message", "details");
        assertThat(Card.getDescriptor().getFile().getPackage()).isEqualTo("google.apps.card.v1");
        assertThat(Price.getDescriptor().getFields()).extracting(field -> field.getName())
                .contains("amount_micros", "currency_code");
    }

    private static Any detail(String typeUrl, String value) {
        return Any.newBuilder()
                .setTypeUrl(typeUrl)
                .setValue(ByteString.copyFromUtf8(value))
                .build();
    }
}
