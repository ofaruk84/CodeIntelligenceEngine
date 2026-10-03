package com.codeintel.application.result;

import com.codeintel.domain.model.MethodId;
import java.util.*;

public record DependencyResult(MethodId target, Map<MethodId, Integer> minimumDistances) {
    public DependencyResult {
        Objects.requireNonNull(target);
        minimumDistances = Collections.unmodifiableMap(new LinkedHashMap<>(minimumDistances));
    }
}
