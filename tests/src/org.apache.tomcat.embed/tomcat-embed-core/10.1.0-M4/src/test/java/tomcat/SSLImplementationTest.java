package tomcat;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.apache.tomcat.util.net.jsse.JSSEUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLImplementationTest {
    @Test
    void selectsTheConfiguredJsseImplementation() throws Exception {
        SSLImplementation implementation = SSLImplementation.getInstance(JSSEImplementation.class.getName());
        SSLHostConfig host = new SSLHostConfig();
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(
                host, SSLHostConfigCertificate.Type.RSA);
        assertThat(implementation.getSSLUtil(certificate)).isInstanceOf(JSSEUtil.class);
        assertThat(implementation.getSSLUtil(certificate).getEnabledProtocols()).contains("TLSv1.2");
    }
}
