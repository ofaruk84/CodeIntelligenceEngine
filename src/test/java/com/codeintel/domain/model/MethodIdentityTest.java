package com.codeintel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MethodIdentityTest {
    private static final SymbolId OWNER = SymbolId.canonical("com.example.PaymentService");
    private static final TypeReference STRING = TypeReference.resolved("java.lang.String");
    private static final TypeReference MONEY = TypeReference.resolved("java.math.BigDecimal");
    private static final SourceLocation LOCATION = new SourceLocation("src/PaymentService.java", 4, 1, 6, 2);

    @Test void canonicalIdentityIncludesOwnerAndOrderedParameterTypes() {
        var id = MethodId.canonical(OWNER, "pay", List.of(STRING, MONEY));
        assertEquals("com.example.PaymentService#pay(java.lang.String,java.math.BigDecimal)", id.value());
        assertNotEquals(id, MethodId.canonical(OWNER, "pay", List.of(MONEY, STRING)));
        assertNotEquals(id, MethodId.canonical(OWNER, "pay", List.of(STRING)));
        assertNotEquals(id, MethodId.canonical(SymbolId.canonical("other.PaymentService"), "pay", List.of(STRING, MONEY)));
        assertEquals("com.example.PaymentService#pay()", MethodId.canonical(OWNER, "pay", List.of()).value());
    }

    @Test void constructorsAndMemberNestedTypesHaveDistinctIdentities() {
        var nested = SymbolId.canonical("com.example.Outer$Inner");
        var constructor = MethodId.canonical(nested, "<init>", List.of(STRING));
        assertEquals("com.example.Outer$Inner#<init>(java.lang.String)", constructor.value());
        assertNotEquals(constructor, MethodId.canonical(nested, "Inner", List.of(STRING)));
        assertNotEquals(constructor, MethodId.canonical(SymbolId.canonical("com.example.Other$Inner"), "<init>", List.of(STRING)));
    }

    @Test void semanticVarargsAndArrayTypesShareIdentity() {
        var varargsType = STRING.asArray();
        var arrayType = new TypeReference("java.lang.String", 1, true);
        assertEquals(MethodId.canonical(OWNER, "pay", List.of(arrayType)),
                MethodId.canonical(OWNER, "pay", List.of(varargsType)));
        assertEquals("java.lang.String[][]", varargsType.asArray().identity());
    }

    @Test void erasedSemanticTypesAreAcceptedButGenericSyntaxIsNotParsed() {
        var erasedList = TypeReference.resolved("java.util.List");
        assertEquals("com.example.PaymentService#pay(java.util.List)",
                MethodId.canonical(OWNER, "pay", List.of(erasedList)).value());
        assertThrows(IllegalArgumentException.class, () -> TypeReference.resolved("java.util.List<String>"));
        assertThrows(IllegalArgumentException.class, () -> TypeReference.resolved("java.lang.String..."));
        assertThrows(IllegalArgumentException.class, () -> TypeReference.resolved("java.lang.String[]"));
    }

    @Test void unresolvedIdentityIsStableForUnchangedSemanticInputAndLocation() {
        var unknown = TypeReference.unresolved("Missing<T>");
        var id = new MethodId(OWNER, "pay", List.of(unknown), Optional.of(LOCATION));
        var windowsLocation = new SourceLocation("src\\PaymentService.java", 4, 1, 6, 2);
        var repeated = new MethodId(OWNER, "pay", List.of(unknown), Optional.of(windowsLocation));
        assertEquals(id, repeated);
        assertEquals(id.value(), repeated.value());
        assertTrue(id.value().contains("unresolved:10:Missing<T>"));
        assertTrue(id.value().endsWith("@23:src/PaymentService.java:4:1-6:2"));
        assertThrows(IllegalArgumentException.class, () -> MethodId.canonical(OWNER, "pay", List.of(unknown)));
    }

    @Test void fallbackLocationsDistinguishFilesAndDeclarationRanges() {
        var unknown = TypeReference.unresolved("Missing");
        var id = new MethodId(OWNER, "pay", List.of(unknown), Optional.of(LOCATION));
        assertNotEquals(id, id.at(new SourceLocation("other/PaymentService.java", 4, 1, 6, 2)));
        assertNotEquals(id, id.at(new SourceLocation(LOCATION.path(), 7, 1, 9, 2)));
        assertNotEquals(id.value(), id.at(new SourceLocation(LOCATION.path(), 4, 2, 6, 2)).value());
        assertNotEquals(id, MethodId.canonical(OWNER, "pay", List.of(TypeReference.resolved("example.Missing"))));
    }

    @Test void unresolvedSpellingCannotInjectSignatureSeparators() {
        var one = new MethodId(OWNER, "pay", List.of(TypeReference.unresolved("A,unresolved:1:B")), Optional.of(LOCATION));
        var two = new MethodId(OWNER, "pay", List.of(TypeReference.unresolved("A"), TypeReference.unresolved("B")), Optional.of(LOCATION));
        assertNotEquals(one.value(), two.value());
        var baseWithBrackets = TypeReference.unresolved("A[]");
        assertNotEquals(baseWithBrackets.identity(), TypeReference.unresolved("A").asArray().identity());
    }

    @Test void duplicateCanonicalDefinitionsCanBeQualifiedWithoutOverwriting() {
        var canonical = MethodId.canonical(OWNER, "pay", List.of(STRING));
        var first = canonical.at(LOCATION);
        var second = canonical.at(new SourceLocation("other/PaymentService.java", 4, 1, 6, 2));
        assertNotEquals(first, second);
        assertNotEquals(first.value(), second.value());
        assertEquals(canonical.parameterTypes(), first.parameterTypes());
        assertEquals(canonical.name(), second.name());
    }

    @Test void localAndAnonymousTypeLocationsAlsoQualifyTheirCallables() {
        var first = SymbolId.canonical("com.example.Outer$Local").at(LOCATION);
        var second = SymbolId.canonical("com.example.Outer$Local")
                .at(new SourceLocation(LOCATION.path(), 10, 1, 12, 2));
        assertNotEquals(MethodId.canonical(first, "run", List.of()).value(),
                MethodId.canonical(second, "run", List.of()).value());
    }

    @Test void parameterListsAreDefensivelyCopiedAndUnmodifiable() {
        var mutable = new ArrayList<>(List.of(STRING));
        var id = MethodId.canonical(OWNER, "pay", mutable);
        mutable.add(MONEY);
        assertEquals(List.of(STRING), id.parameterTypes());
        assertThrows(UnsupportedOperationException.class, () -> id.parameterTypes().add(MONEY));
        assertThrows(NullPointerException.class, () -> MethodId.canonical(OWNER, "pay", java.util.Arrays.asList(STRING, null)));
    }

    @Test void invalidLocationsAndIdentityComponentsAreRejected() {
        for (String path : List.of("/absolute.java", "C:\\absolute.java", "../A.java", "src/../A.java", "src//A.java", "src/./A.java", "src/"))
            assertThrows(IllegalArgumentException.class, () -> new SourceLocation(path, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("A.java", 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("A.java", 2, 4, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> SymbolId.canonical("a..B"));
        assertThrows(IllegalArgumentException.class, () -> MethodId.canonical(OWNER, "bad#name", List.of()));
        assertThrows(IllegalArgumentException.class, () -> MethodId.canonical(OWNER, "pay", List.of(TypeReference.resolved("void"))));
        assertThrows(IllegalArgumentException.class, () -> TypeReference.resolved("void").asArray());
        assertThrows(IllegalArgumentException.class, () -> new TypeReference("int", -1, true));
        assertThrows(IllegalArgumentException.class, () -> TypeReference.unresolved(" Missing "));
    }
}
