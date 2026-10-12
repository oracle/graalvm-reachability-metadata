/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacv;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

public final class IntegerFrameGrabber extends FrameGrabber {
    private final Integer deviceNumber;

    public IntegerFrameGrabber(Integer deviceNumber) {
        this.deviceNumber = deviceNumber;
    }

    public static void tryLoad() {
    }

    public static String[] getDeviceDescriptions() {
        return new String[] {"Integer camera"};
    }

    public Integer getDeviceNumber() {
        return deviceNumber;
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
