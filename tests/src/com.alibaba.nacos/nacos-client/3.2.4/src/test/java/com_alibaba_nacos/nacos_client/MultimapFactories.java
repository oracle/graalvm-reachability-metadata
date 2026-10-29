/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import com.alibaba.nacos.shaded.com.google.common.base.Supplier;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

final class MultimapFactories {
    private MultimapFactories() {
    }

    static final class Lists implements Supplier<List<String>>, Serializable {
        public List<String> get() {
            return new ArrayList<>();
        }
    }

    static final class Collections implements Supplier<Collection<String>>, Serializable {
        public Collection<String> get() {
            return new ArrayList<>();
        }
    }

    static final class Sets implements Supplier<Set<String>>, Serializable {
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }

    static final class SortedSets implements Supplier<SortedSet<String>>, Serializable {
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
