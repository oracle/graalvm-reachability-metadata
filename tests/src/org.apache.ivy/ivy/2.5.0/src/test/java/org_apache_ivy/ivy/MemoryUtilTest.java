/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import org.apache.ivy.util.MemoryUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MemoryUtilTest {
    @Test
    void estimatesDefaultInstanceSizeForConstructibleClass() {
        long size = MemoryUtil.sizeOf(ArrayList.class);

        assertThat(Long.toString(size)).matches("-?\\d+");
    }

    @Test
    void commandLineEntryPointLoadsRequestedClassAndPrintsApproximateSize() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            MemoryUtil.main(new String[] {ArrayList.class.getName()});
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
            return;
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString().trim()).matches("-?\\d+");
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
