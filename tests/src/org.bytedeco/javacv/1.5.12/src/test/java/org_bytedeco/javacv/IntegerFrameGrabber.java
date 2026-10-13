/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import java.io.File;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

public final class IntegerFrameGrabber extends FrameGrabber {
    private final Integer deviceNumber;
    private final File deviceFile;
    private final String devicePath;

    public IntegerFrameGrabber(Integer deviceNumber) {
        this.deviceNumber = deviceNumber;
        this.deviceFile = null;
        this.devicePath = null;
    }

    public IntegerFrameGrabber(File deviceFile) {
        this.deviceNumber = null;
        this.deviceFile = deviceFile;
        this.devicePath = null;
    }

    public IntegerFrameGrabber(String devicePath) {
        this.deviceNumber = null;
        this.deviceFile = null;
        this.devicePath = devicePath;
    }

    public static void tryLoad() {
    }

    public static String[] getDeviceDescriptions() {
        return new String[] {"Integer camera"};
    }

    public Integer getDeviceNumber() {
        return deviceNumber;
    }

    public File getDeviceFile() {
        return deviceFile;
    }

    public String getDevicePath() {
        return devicePath;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void trigger() {
    }

    @Override
    public Frame grab() {
        return new Frame();
    }

    @Override
    public void release() {
    }
}
