package com.bbh.itss.dso.portal.domain.pipeline;

import static java.util.Objects.requireNonNull;

public record IssuedKey(long pipelineId, PipelineKey key) {

    public IssuedKey {
        requireNonNull(key, "an issued key names the key");
    }

    public long authorize() {
        key.requireActive();
        return pipelineId;
    }
}
