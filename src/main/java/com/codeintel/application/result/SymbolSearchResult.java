package com.codeintel.application.result;

import com.codeintel.domain.model.*;
import java.util.*;

public record SymbolSearchResult(List<Symbol> symbols) {
    public SymbolSearchResult { symbols = List.copyOf(symbols); }
    public enum Kind { TYPE, METHOD, CONSTRUCTOR }
    public record Symbol(String id, String name, Kind kind, SourceLocation location) {
        public Symbol { Objects.requireNonNull(id); Objects.requireNonNull(name);
            Objects.requireNonNull(kind); Objects.requireNonNull(location); }
    }
}
