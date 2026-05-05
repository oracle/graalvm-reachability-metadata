/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.InvocationEvent;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.diffplug.common.base.Errors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ErrorsInnerPluginsTest {
    @Test
    @Timeout(10)
    void defaultDialogSchedulesAndRunsSwingDialogHandler() throws Exception {
        String previousHeadlessProperty = System.setProperty("java.awt.headless", "true");

        CapturingEventQueue eventQueue = new CapturingEventQueue();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(eventQueue);
        eventQueue.clearCapturedEvents();

        try {
            RuntimeException error = new RuntimeException("dialog failure");

            assertThatCode(() -> Errors.Plugins.defaultDialog(error)).doesNotThrowAnyException();

            AWTEvent dialogEvent = eventQueue.takeCapturedEvent();
            assertThat(dialogEvent).isNotNull();
            eventQueue.dispatchCapturedEvent(dialogEvent);
        } finally {
            eventQueue.popSelf();
            restoreProperty("java.awt.headless", previousHeadlessProperty);
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class CapturingEventQueue extends EventQueue {
        private final LinkedBlockingQueue<AWTEvent> capturedEvents = new LinkedBlockingQueue<>();

        @Override
        public void postEvent(AWTEvent event) {
            if (event instanceof InvocationEvent) {
                capturedEvents.add(event);
            } else {
                super.postEvent(event);
            }
        }

        private void clearCapturedEvents() {
            capturedEvents.clear();
        }

        private AWTEvent takeCapturedEvent() throws InterruptedException {
            return capturedEvents.poll(5, TimeUnit.SECONDS);
        }

        private void dispatchCapturedEvent(AWTEvent event) {
            dispatchEvent(event);
        }

        private void popSelf() {
            pop();
        }
    }
}
