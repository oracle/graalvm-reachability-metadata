/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.EnumConverter;
import com.sun.jna.platform.EnumUtils;
import com.sun.jna.platform.FileMonitor;
import com.sun.jna.platform.FileUtils;
import com.sun.jna.platform.RasterRangesUtils;
import com.sun.jna.platform.win32.FlagEnum;
import com.sun.jna.platform.win32.W32Errors;
import com.sun.jna.platform.win32.WinNT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Jna_platformTest {
    @Test
    void enumConvertersRoundTripOrdinalsAndFlags() {
        EnumConverter<ServiceState> converter = new EnumConverter<>(ServiceState.class);
        Set<AccessFlag> accessFlags = EnumSet.of(AccessFlag.READ, AccessFlag.EXECUTE);
        int bitMask = EnumUtils.setToInteger(accessFlags);

        assertThat(EnumUtils.toInteger(ServiceState.CREATED)).isZero();
        assertThat(EnumUtils.fromInteger(1, ServiceState.class)).isEqualTo(ServiceState.RUNNING);
        assertThat(EnumUtils.fromInteger(EnumUtils.UNINITIALIZED, ServiceState.class)).isNull();

        assertThat(converter.nativeType()).isEqualTo(Integer.class);
        assertThat(converter.toNative(ServiceState.STOPPED, null)).isEqualTo(2);
        assertThat(converter.fromNative(0, null)).isEqualTo(ServiceState.CREATED);

        assertThat(bitMask).isEqualTo(AccessFlag.READ.getFlag() | AccessFlag.EXECUTE.getFlag());
        assertThat(EnumUtils.setFromInteger(bitMask, AccessFlag.class))
                .containsExactlyInAnyOrder(AccessFlag.READ, AccessFlag.EXECUTE);
    }

    @Test
    void win32ErrorUtilitiesPreserveSeverityFacilityAndCode() {
        int hresultValue = W32Errors.MAKE_HRESULT((short) 1, (short) 7, (short) 5);
        WinNT.HRESULT accessDenied = W32Errors.HRESULT_FROM_WIN32(5);

        assertThat(W32Errors.FAILED(hresultValue)).isTrue();
        assertThat(W32Errors.SUCCEEDED(hresultValue)).isFalse();
        assertThat(W32Errors.HRESULT_SEVERITY(hresultValue)).isEqualTo((short) 1);
        assertThat(W32Errors.HRESULT_FACILITY(hresultValue)).isEqualTo(7);
        assertThat(W32Errors.HRESULT_CODE(hresultValue)).isEqualTo(5);

        assertThat(W32Errors.FAILED(accessDenied)).isTrue();
        assertThat(W32Errors.HRESULT_FACILITY(accessDenied.intValue())).isEqualTo(7);
        assertThat(W32Errors.HRESULT_CODE(accessDenied.intValue())).isEqualTo(5);
    }

    @Test
    void rasterRangesUtilsMergesOccupiedRangesAcrossRows() {
        int[] pixels = {
                1, 1, 0, 1,
                1, 1, 0, 1,
                0, 0, 1, 1
        };
        List<String> ranges = new ArrayList<>();

        boolean completed = RasterRangesUtils.outputOccupiedRanges(
                pixels,
                4,
                3,
                0xFF,
                (x, y, width, height) -> {
                    ranges.add(rangeKey(x, y, width, height));
                    return true;
                }
        );

        assertThat(completed).isTrue();
        assertThat(ranges)
                .containsExactlyInAnyOrder(
                        rangeKey(0, 0, 2, 2),
                        rangeKey(3, 0, 1, 2),
                        rangeKey(2, 2, 2, 1)
                );
    }

    @Test
    void rasterRangesUtilsSupportsBinaryPixelSourcesAndShortCircuiting() {
        byte[] binaryPixels = {
                (byte) 0b11110000,
                (byte) 0b11110000,
                (byte) 0b00001111
        };
        List<String> fullScanRanges = new ArrayList<>();
        List<String> earlyStopRanges = new ArrayList<>();

        boolean fullScanCompleted = RasterRangesUtils.outputOccupiedRangesOfBinaryPixels(
                binaryPixels,
                8,
                3,
                (x, y, width, height) -> {
                    fullScanRanges.add(rangeKey(x, y, width, height));
                    return true;
                }
        );
        boolean earlyStopCompleted = RasterRangesUtils.outputOccupiedRangesOfBinaryPixels(
                binaryPixels,
                8,
                3,
                (x, y, width, height) -> {
                    earlyStopRanges.add(rangeKey(x, y, width, height));
                    return false;
                }
        );

        assertThat(fullScanCompleted).isTrue();
        assertThat(fullScanRanges)
                .containsExactlyInAnyOrder(
                        rangeKey(0, 0, 4, 2),
                        rangeKey(4, 2, 4, 1)
                );

        assertThat(earlyStopCompleted).isFalse();
        assertThat(earlyStopRanges).hasSize(1);
    }

    @Test
    void fileUtilsMovesFilesToTrash(@TempDir Path tempDir) throws IOException {
        FileUtils fileUtils = FileUtils.getInstance();

        if (isWindows() || isMac()) {
            Path source = Files.writeString(tempDir.resolve("payload.txt"), "jna-platform");

            if (fileUtils.hasTrash()) {
                fileUtils.moveToTrash(source.toFile());
                assertThat(Files.exists(source)).isFalse();
            } else {
                assertThat(fileUtils.hasTrash()).isFalse();
            }
            return;
        }

        Path trashDirectory = Files.createDirectory(tempDir.resolve("trash"));
        Path source = Files.writeString(tempDir.resolve("payload.txt"), "jna-platform");
        String previousTrashDirectory = System.getProperty("fileutils.trash");
        System.setProperty("fileutils.trash", trashDirectory.toString());
        try {
            assertThat(fileUtils.hasTrash()).isTrue();
            fileUtils.moveToTrash(source.toFile());
        } finally {
            restoreProperty("fileutils.trash", previousTrashDirectory);
        }

        Path trashedFile = trashDirectory.resolve(source.getFileName());
        assertThat(Files.exists(source)).isFalse();
        assertThat(Files.isRegularFile(trashedFile)).isTrue();
        assertThat(Files.readString(trashedFile)).isEqualTo("jna-platform");
    }

    @Test
    void fileMonitorInfersDefaultRecursionFromWatchedPathType(@TempDir Path tempDir) throws IOException {
        RecordingFileMonitor monitor = new RecordingFileMonitor();
        File watchedFile = Files.writeString(tempDir.resolve("payload.txt"), "jna-platform").toFile();
        File watchedDirectory = Files.createDirectory(tempDir.resolve("watched-dir")).toFile();

        monitor.addWatch(watchedFile);
        monitor.addWatch(watchedDirectory, FileMonitor.FILE_CREATED);

        assertThat(monitor.watchRequests())
                .containsEntry(watchedFile, new WatchRequest(FileMonitor.FILE_ANY, false))
                .containsEntry(watchedDirectory, new WatchRequest(FileMonitor.FILE_CREATED, true));
    }

    @Test
    void fileMonitorDispatchesEventsToRegisteredListenersOnly(@TempDir Path tempDir) throws IOException {
        RecordingFileMonitor monitor = new RecordingFileMonitor();
        File watchedDirectory = Files.createDirectory(tempDir.resolve("watched-dir")).toFile();
        File changedFile = Files.writeString(tempDir.resolve("changed.txt"), "jna-platform").toFile();
        File unwatchedFile = tempDir.resolve("unwatched.txt").toFile();
        List<String> observedEvents = new ArrayList<>();
        FileMonitor.FileListener listener = event -> observedEvents.add(eventKey(event.getFile(), event.getType()));

        monitor.addFileListener(listener);
        monitor.addWatch(watchedDirectory, FileMonitor.FILE_MODIFIED, false);
        monitor.emit(changedFile, FileMonitor.FILE_MODIFIED);
        monitor.removeFileListener(listener);
        monitor.emit(changedFile, FileMonitor.FILE_DELETED);
        monitor.removeWatch(unwatchedFile);
        monitor.removeWatch(watchedDirectory);
        monitor.removeWatch(watchedDirectory);
        monitor.dispose();

        assertThat(monitor.watchRequests())
                .containsEntry(watchedDirectory, new WatchRequest(FileMonitor.FILE_MODIFIED, false));
        assertThat(observedEvents).containsExactly(eventKey(changedFile, FileMonitor.FILE_MODIFIED));
        assertThat(monitor.unwatchedFiles()).containsExactly(watchedDirectory);
        assertThat(monitor.isDisposed()).isTrue();
    }

    private static String rangeKey(int x, int y, int width, int height) {
        return x + ":" + y + ":" + width + ":" + height;
    }

    private static String eventKey(File file, int type) {
        return file.getName() + ":" + type;
    }

    private static boolean isMac() {
        return System.getProperty("os.name").startsWith("Mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static final class RecordingFileMonitor extends FileMonitor {
        private final Map<File, WatchRequest> watchRequests = new LinkedHashMap<>();
        private final List<File> unwatchedFiles = new ArrayList<>();
        private boolean disposed;

        @Override
        protected void watch(File file, int eventMask, boolean recursive) {
            watchRequests.put(file, new WatchRequest(eventMask, recursive));
        }

        @Override
        protected void unwatch(File file) {
            unwatchedFiles.add(file);
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        private void emit(File file, int type) {
            notify(new FileEvent(file, type));
        }

        private Map<File, WatchRequest> watchRequests() {
            return watchRequests;
        }

        private List<File> unwatchedFiles() {
            return unwatchedFiles;
        }

        private boolean isDisposed() {
            return disposed;
        }
    }

    private record WatchRequest(int eventMask, boolean recursive) {
    }

    private enum ServiceState {
        CREATED,
        RUNNING,
        STOPPED
    }

    private enum AccessFlag implements FlagEnum {
        READ(0x1),
        WRITE(0x2),
        EXECUTE(0x4);

        private final int flag;

        AccessFlag(int flag) {
            this.flag = flag;
        }

        @Override
        public int getFlag() {
            return flag;
        }
    }
}
