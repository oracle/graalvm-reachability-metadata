/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

/** Test full-runtime registry used by ExtensionRegistryFactory when it is present. */
public final class ExtensionRegistry extends ExtensionRegistryLite {
    private static final ExtensionRegistry EMPTY_REGISTRY = new ExtensionRegistry(true);

    public ExtensionRegistry() {
        super();
    }

    private ExtensionRegistry(boolean empty) {
        super(empty);
    }

    private ExtensionRegistry(ExtensionRegistryLite other) {
        super(other);
    }

    public static ExtensionRegistry newInstance() {
        return new ExtensionRegistry();
    }

    public static ExtensionRegistry getEmptyRegistry() {
        return EMPTY_REGISTRY;
    }

    @Override
    public ExtensionRegistry getUnmodifiable() {
        return new ExtensionRegistry(this);
    }
}
