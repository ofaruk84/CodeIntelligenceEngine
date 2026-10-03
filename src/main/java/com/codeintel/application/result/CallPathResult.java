package com.codeintel.application.result;

import com.codeintel.domain.model.MethodId;
import java.util.*;

/** Empty path means both endpoints are known but disconnected. */
public record CallPathResult(MethodId source, MethodId target, Optional<List<MethodId>> path) {
    public CallPathResult { Objects.requireNonNull(source); Objects.requireNonNull(target);
        path = Objects.requireNonNull(path).map(List::copyOf); }
}
