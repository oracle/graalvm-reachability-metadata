/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

/** Test full-runtime extension marker used when ExtensionRegistryLite bridges to full runtime. */
public abstract class Extension<ContainingType extends MessageLite, Type>
        extends ExtensionLite<ContainingType, Type> {
}
