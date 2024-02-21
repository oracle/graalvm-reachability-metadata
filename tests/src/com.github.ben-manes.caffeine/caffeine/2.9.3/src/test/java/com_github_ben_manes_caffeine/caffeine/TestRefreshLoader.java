/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import java.util.concurrent.CompletableFuture;

public class TestRefreshLoader {
    private Boolean flag = false;

    public CompletableFuture<String> load(String ignoredKey) {
        if (getFlag()) {
            return CompletableFuture.completedFuture("Universe");
        }
        return CompletableFuture.completedFuture("World");
    }

    public Boolean getFlag() {
        return flag;
    }

    public void setFlag(Boolean flag) {
        this.flag = flag;
    }
}
