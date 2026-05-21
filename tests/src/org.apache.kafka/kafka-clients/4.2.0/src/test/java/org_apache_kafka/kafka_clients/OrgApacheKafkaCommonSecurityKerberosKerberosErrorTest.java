/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.apache.kafka.common.utils.ChildFirstClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sun.security.krb5.KrbException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSecurityKerberosKerberosErrorTest {

    @TempDir
    Path serviceRoot;

    @Test
    void initializesKerberosErrorWithIbmVendorInChildFirstLoader() throws Exception {
        Path servicesDirectory = serviceRoot.resolve("META-INF/services");
        Files.createDirectories(servicesDirectory);
        Files.writeString(
                servicesDirectory.resolve(Runnable.class.getName()),
                IbmVendorKerberosErrorRunner.class.getName(),
                StandardCharsets.UTF_8);

        String previousVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(childFirstClasspath(), getClass().getClassLoader())) {
            ServiceLoader<Runnable> serviceLoader = ServiceLoader.load(Runnable.class, classLoader);
            Iterator<Runnable> runners = serviceLoader.iterator();

            assertThat(runners).hasNext();
            runners.next().run();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreJavaVendor(previousVendor);
        }
    }

    @Test
    void mapsNestedKerberosExceptionReturnCode() {
        String previousVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
        try {
            Exception saslFailure = new Exception("sasl failure", new Exception("gss failure", new KrbException(21)));

            KerberosError error = KerberosError.fromException(saslFailure);

            assertThat(error).isEqualTo(KerberosError.CLIENT_NOT_YET_VALID);
            assertThat(error.retriable()).isTrue();
        } finally {
            restoreJavaVendor(previousVendor);
        }
    }

    public static final class IbmVendorKerberosErrorRunner implements Runnable {

        @Override
        public void run() {
            Exception saslFailure = new Exception("sasl failure", new Exception("gss failure", new KrbException(21)));

            KerberosError error = KerberosError.fromException(saslFailure);

            if (error != KerberosError.CLIENT_NOT_YET_VALID || !error.retriable()) {
                throw new AssertionError("Expected retriable Kerberos client-not-yet-valid error");
            }
        }
    }

    private String childFirstClasspath() {
        StringBuilder classpath = new StringBuilder(serviceRoot.toString());
        String[] entries = System.getProperty("java.class.path", "").split(File.pathSeparator);
        for (String entry : entries) {
            if (isTestClassesDirectory(entry) || isKafkaClientsJar(entry)) {
                classpath.append(File.pathSeparator).append(entry);
            }
        }
        return classpath.toString();
    }

    private static boolean isTestClassesDirectory(String entry) {
        return entry.endsWith("/classes/java/test") || entry.endsWith("\\classes\\java\\test");
    }

    private static boolean isKafkaClientsJar(String entry) {
        String fileName = Path.of(entry).getFileName().toString();
        return fileName.startsWith("kafka-clients-") && fileName.endsWith(".jar");
    }

    private static void restoreJavaVendor(String previousVendor) {
        if (previousVendor == null) {
            System.clearProperty("java.vendor");
        } else {
            System.setProperty("java.vendor", previousVendor);
        }
    }
}
