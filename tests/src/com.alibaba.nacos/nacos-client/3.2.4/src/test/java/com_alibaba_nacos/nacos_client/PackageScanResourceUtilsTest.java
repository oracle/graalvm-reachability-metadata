/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.common.packagescan.util.ResourceUtils;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

public class PackageScanResourceUtilsTest {
    @Test
    void rejectsAbsentClasspathFiles() {
        FileNotFoundException failure = assertThrows(FileNotFoundException.class,
                () -> ResourceUtils.getFile("classpath:nacos-metadata-test-absent.properties"));
        assertTrue(failure.getMessage().contains("does not exist"));
    }
}
