/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jets3t.service.utils.gatekeeper.GatekeeperMessage;
import org.jets3t.service.utils.gatekeeper.SignatureRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatekeeperMessageTest {
    @Test
    public void encodesAndDecodesApplicationMessageAndSignatureRequests() {
        GatekeeperMessage message = new GatekeeperMessage();
        message.addApplicationProperty("client", "desktop-uploader");
        message.addMessageProperty(GatekeeperMessage.PROPERTY_TRANSACTION_ID, "transaction-123");

        SignatureRequest getRequest = new SignatureRequest(SignatureRequest.SIGNATURE_TYPE_GET, "photos/image.jpg");
        getRequest.setBucketName("coverage-bucket");
        getRequest.addObjectMetadata("content-type", "image/jpeg");
        getRequest.signRequest("https://example.invalid/signed-get");

        SignatureRequest putRequest = new SignatureRequest(SignatureRequest.SIGNATURE_TYPE_PUT, "uploads/new.txt");
        putRequest.declineRequest("quota exceeded");

        message.addSignatureRequests(new SignatureRequest[] {getRequest, putRequest});

        Properties encodedProperties = message.encodeToProperties();
        GatekeeperMessage decodedMessage = GatekeeperMessage.decodeFromProperties(encodedProperties);

        assertThat(decodedMessage.getApplicationProperties()).containsEntry("client", "desktop-uploader");
        assertThat(decodedMessage.getMessageProperties())
            .containsEntry(GatekeeperMessage.PROPERTY_TRANSACTION_ID, "transaction-123");

        SignatureRequest[] decodedRequests = decodedMessage.getSignatureRequests();
        assertThat(decodedRequests).hasSize(2);
        assertThat(decodedRequests[0].getSignatureType()).isEqualTo(SignatureRequest.SIGNATURE_TYPE_GET);
        assertThat(decodedRequests[0].getBucketName()).isEqualTo("coverage-bucket");
        assertThat(decodedRequests[0].getObjectKey()).isEqualTo("photos/image.jpg");
        assertThat(decodedRequests[0].getObjectMetadata()).containsEntry("content-type", "image/jpeg");
        assertThat(decodedRequests[0].getSignedUrl()).isEqualTo("https://example.invalid/signed-get");
        assertThat(decodedRequests[0].isSigned()).isTrue();
        assertThat(decodedRequests[1].getSignatureType()).isEqualTo(SignatureRequest.SIGNATURE_TYPE_PUT);
        assertThat(decodedRequests[1].getObjectKey()).isEqualTo("uploads/new.txt");
        assertThat(decodedRequests[1].getDeclineReason()).isEqualTo("quota exceeded");
        assertThat(decodedRequests[1].isSigned()).isFalse();
    }

    @Test
    public void decodesServletStyleParameterMapValues() {
        Map<String, Object> postProperties = new HashMap<String, Object>();
        postProperties.put("application|version", new String[] {"1.0"});
        postProperties.put("message|priorFailureMessage", "retrying after timeout");
        postProperties.put("request|0|signatureType", new String[] {SignatureRequest.SIGNATURE_TYPE_DELETE});
        postProperties.put("request|0|objectKey", new String[] {"old-object.txt"});
        postProperties.put("request|0|metadata|requester", "integration-test");
        postProperties.put("ignored|property", "ignored-value");

        GatekeeperMessage decodedMessage = GatekeeperMessage.decodeFromProperties(postProperties);

        assertThat(decodedMessage.getApplicationProperties()).containsEntry("version", "1.0");
        assertThat(decodedMessage.getMessageProperties())
            .containsEntry("priorFailureMessage", "retrying after timeout");

        SignatureRequest[] decodedRequests = decodedMessage.getSignatureRequests();
        assertThat(decodedRequests).hasSize(1);
        assertThat(decodedRequests[0].getSignatureType()).isEqualTo(SignatureRequest.SIGNATURE_TYPE_DELETE);
        assertThat(decodedRequests[0].getObjectKey()).isEqualTo("old-object.txt");
        assertThat(decodedRequests[0].getObjectMetadata()).containsEntry("requester", "integration-test");
    }
}
