/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import org.jets3t.service.model.S3Object;
import org.jets3t.service.utils.gatekeeper.SignatureRequest;
import org.jets3t.service.utils.signedurl.GatekeeperClientUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatekeeperClientUtilsTest {
    @Test
    public void initializesClientAndBuildsObjectsFromSignatureRequests() {
        GatekeeperClientUtils clientUtils = new GatekeeperClientUtils(
            "https://gatekeeper.example.invalid/sign",
            "coverage-client",
            1,
            1_000,
            null);
        SignatureRequest firstRequest = new SignatureRequest(SignatureRequest.SIGNATURE_TYPE_GET, "photos/one.jpg");
        firstRequest.addObjectMetadata("content-type", "image/jpeg");
        firstRequest.addObjectMetadata("requester", "integration-test");
        SignatureRequest secondRequest = new SignatureRequest(SignatureRequest.SIGNATURE_TYPE_PUT, "documents/two.txt");
        secondRequest.addObjectMetadata("content-type", "text/plain");

        S3Object[] objects = clientUtils.buildS3ObjectsFromSignatureRequests(
            new SignatureRequest[] {firstRequest, secondRequest});

        assertThat(clientUtils.getGatekeeperUrl()).isEqualTo("https://gatekeeper.example.invalid/sign");
        assertThat(objects).hasSize(2);
        assertThat(objects[0].getKey()).isEqualTo("photos/one.jpg");
        assertThat(objects[0].getMetadataMap())
            .containsEntry("content-type", "image/jpeg")
            .containsEntry("requester", "integration-test");
        assertThat(objects[1].getKey()).isEqualTo("documents/two.txt");
        assertThat(objects[1].getMetadataMap()).containsEntry("content-type", "text/plain");
    }
}
