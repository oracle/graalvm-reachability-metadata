/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.build;

public final class ToolDynamicAccessSupport {
    private ToolDynamicAccessSupport() {
    }

    public static Class<?> loadToolClassThroughGeneratedBridge() {
        return Tool.class$(Tool.class.getName());
    }
}
