/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j.isolated;

import ch_qos_reload4j.reload4j.IsolatedLoaderAction;

import org.apache.log4j.EnhancedThrowableRenderer;

public class EnhancedThrowableRendererActionProvider implements IsolatedLoaderAction {
    @Override
    public String loadClass(String className) {
        Throwable throwable = new IllegalStateException("enhanced-renderer-isolated-test");
        throwable.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(className, "isolatedRenderedMethod", "EnhancedThrowableRendererTest.java", 321)
        });
        return new EnhancedThrowableRenderer().doRender(throwable)[1];
    }
}
