/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.Message;

public final class DescriptorMessageInfoFactoryCoverageNested extends GeneratedMessageV3 {
    private static final String NO_BUILDERS_MESSAGE =
            "Builders are not needed for this coverage fixture";
    private static final DescriptorMessageInfoFactoryCoverageNested DEFAULT_INSTANCE =
            new DescriptorMessageInfoFactoryCoverageNested();

    private String value_ = "";

    public static DescriptorMessageInfoFactoryCoverageNested getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public String getValue() {
        return value_;
    }

    @Override
    protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
        return DescriptorMessageInfoFactoryCoverageMessage.nestedFieldAccessorTable();
    }

    @Override
    protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }
}
