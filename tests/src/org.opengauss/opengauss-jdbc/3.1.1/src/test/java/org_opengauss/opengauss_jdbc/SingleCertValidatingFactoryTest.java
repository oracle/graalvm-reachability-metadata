/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ssl.SingleCertValidatingFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleCertValidatingFactoryTest {
    private static final String CERTIFICATE_RESOURCE =
            "org_opengauss/opengauss_jdbc/single-cert-validating-factory-test.crt";
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDNTCCAh2gAwIBAgIUIVKZqChq2ulj7NGNQnbsKXFvV48wDQYJKoZIhvcNAQEL
            BQAwKjEoMCYGA1UEAwwfU2luZ2xlQ2VydFZhbGlkYXRpbmdGYWN0b3J5VGVzdDAe
            Fw0yNjA1MjYxNjQwMjdaFw0zNjA1MjMxNjQwMjdaMCoxKDAmBgNVBAMMH1Npbmds
            ZUNlcnRWYWxpZGF0aW5nRmFjdG9yeVRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IB
            DwAwggEKAoIBAQDlLHB/AUP5L3MWU2AcFangxJJf3YHnPGMIX4ymb5l0AvsrrIvY
            HSokKXzzdKy5WCVW012T0uoKUXMgr6/RiXGUPXPCeMzFA91tLRQ1nIRYmySPQM3D
            kfGkjJK1OboaWQAx9jshgmPqCmx8JJmEh47woHCHsZ/QLgJPhdq2X/4+lrJeu8Xp
            DyN+eIEXmgrd5zz6HTyqXkMaw6lO5fF5vpJfp60sT2CnvuwwHREBDEGUWBIW8xFU
            9BD4Mex5rzu29fZG5YIP/aqq0mHEFjrADuNQzhApCh/WPMkT/0thW4HarpGiYnin
            U51WF7G9G22OzEg60gsUxPQC94Nc+1MEePjBAgMBAAGjUzBRMB0GA1UdDgQWBBQK
            9g/zcAatuu/nR3LNDGPqC9LfZjAfBgNVHSMEGDAWgBQK9g/zcAatuu/nR3LNDGPq
            C9LfZjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQB/xRlcA3Pv
            9LZs87ZylK0lc5bjG9lPAhL6h9SczrxH5HEB6XEoSZL4NXrxCnJ7AiIosB4Xf0NZ
            WpoRBp5OG+KEHyRoUxb2ZYNExvAllMx7rcV0lqhuYZ4X3zJOUZJxoAyrche86qbV
            xDu9gEB2r/ZwwaZ+14WAEtm/Y1LtmdONLULkS7KiDtMP+9oBpeB1J22jkp2RE5oT
            ZviBOEJ0mQ5GChXoOkDr8RS4Pt81a/83/eLVx5Ga//FCu/RLItlMv3fJYqpSJKOQ
            9SLg9lsY4UF8LLvsVV22knb7wjPvDkmszHhrKZbcij8S63mDQksOwbcBZrhgFO81
            nO+YnHL5o+ne
            -----END CERTIFICATE-----
            """;

    @Test
    void loadsPinnedServerCertificateFromClasspathResource(@TempDir Path tempDir) throws Exception {
        Path certificateFile = tempDir.resolve(CERTIFICATE_RESOURCE);
        Files.createDirectories(certificateFile.getParent());
        Files.writeString(certificateFile, CERTIFICATE);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] classpath = new URL[]{tempDir.toUri().toURL()};
        try (URLClassLoader resourceClassLoader = new URLClassLoader(classpath, originalContextClassLoader)) {
            Thread.currentThread().setContextClassLoader(resourceClassLoader);

            SingleCertValidatingFactory factory = new SingleCertValidatingFactory("classpath:" + CERTIFICATE_RESOURCE);

            assertThat(factory.getSupportedCipherSuites()).isNotEmpty();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
