package com.codeintel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {
    private static final SourceLocation LOCATION = new SourceLocation("src/example/Example.java", 1, 1, 30, 1);
    private static final SymbolId OWNER = SymbolId.canonical("example.Example");
    private static final TypeReference STRING = TypeReference.resolved("java.lang.String");
    private static final TypeReference VOID = TypeReference.resolved("void");

    private static MethodNode method(SymbolId owner, List<ParameterInfo> parameters) {
        return new MethodNode(MethodId.canonical(owner, "run", parameters.stream().map(ParameterInfo::type).toList()),
                CallableKind.METHOD, Optional.of(VOID), parameters, Set.of("public"), List.of(), LOCATION);
    }

    private static ClassNode type(SymbolId id, Optional<String> name, TypeKind kind,
                                  ClassNode.Nesting nesting, Optional<SymbolId> enclosing, List<MethodNode> methods) {
        return new ClassNode(id, name, kind, nesting, enclosing, Set.of(), List.of(), List.of(), methods, LOCATION);
    }

    @Test void extractedSourceRetainsImportsFieldsParametersMethodsAndConstructors() {
        var imports = List.of(new ImportInfo("java.util", false, true, LOCATION),
                new ImportInfo("java.util.Collections.emptyList", true, false, LOCATION));
        var parameter = new ParameterInfo("input", STRING, false, Set.of("final"), LOCATION);
        var constructor = new MethodNode(MethodId.canonical(OWNER, "<init>", List.of(STRING)),
                CallableKind.CONSTRUCTOR, Optional.empty(), List.of(parameter), Set.of("public"), List.of(), LOCATION);
        var field = new FieldInfo("value", STRING, Set.of("private", "final"), LOCATION);
        var definition = new ClassNode(OWNER, Optional.of("Example"), TypeKind.CLASS, ClassNode.Nesting.TOP_LEVEL,
                Optional.empty(), Set.of("public"), List.of(TypeReference.resolved("java.lang.Object")),
                List.of(field), List.of(constructor, method(OWNER, List.of(parameter))), LOCATION);
        var unit = new SourceUnit(LOCATION.path(), "example", imports, List.of(definition), List.of(), List.of(), false);
        assertTrue(unit.imports().getFirst().onDemand());
        assertTrue(unit.imports().get(1).isStatic());
        assertEquals("value", unit.types().getFirst().fields().getFirst().name());
        assertEquals(CallableKind.CONSTRUCTOR, unit.types().getFirst().methods().getFirst().kind());
        assertTrue(constructor.returnType().isEmpty());
        assertEquals("input", unit.types().getFirst().methods().get(1).parameters().getFirst().name());
        assertFalse(unit.partial());
    }

    @Test void everyJavaTypeKindCanBeRepresentedWithoutParserObjects() {
        for (TypeKind kind : TypeKind.values()) {
            assertEquals(kind, type(OWNER, Optional.of("Example"), kind, ClassNode.Nesting.TOP_LEVEL,
                    Optional.empty(), List.of()).kind());
        }
    }

    @Test void collectionInputsAndAccessorsCannotMutateDefinitions() {
        var modifiers = new HashSet<>(Set.of("final"));
        var parameter = new ParameterInfo("input", STRING, false, modifiers, LOCATION);
        var field = new FieldInfo("value", STRING, modifiers, LOCATION);
        var parameters = new ArrayList<>(List.of(parameter));
        var thrown = new ArrayList<>(List.of(TypeReference.resolved("java.io.IOException")));
        var callable = new MethodNode(MethodId.canonical(OWNER, "run", List.of(STRING)), CallableKind.METHOD,
                Optional.of(VOID), parameters, modifiers, thrown, LOCATION);
        var fields = new ArrayList<>(List.of(field));
        var methods = new ArrayList<>(List.of(callable));
        var supers = new ArrayList<>(List.of(TypeReference.resolved("java.lang.Object")));
        var definition = new ClassNode(OWNER, Optional.of("Example"), TypeKind.CLASS, ClassNode.Nesting.TOP_LEVEL,
                Optional.empty(), modifiers, supers, fields, methods, LOCATION);
        modifiers.clear();
        parameters.clear();
        thrown.clear();
        fields.clear();
        methods.clear();
        supers.clear();
        assertEquals(Set.of("final"), parameter.modifiers());
        assertEquals(Set.of("final"), field.modifiers());
        assertEquals(Set.of("final"), callable.modifiers());
        assertEquals(Set.of("final"), definition.modifiers());
        assertEquals(List.of(parameter), callable.parameters());
        assertEquals(1, callable.thrownTypes().size());
        assertEquals(List.of(field), definition.fields());
        assertEquals(List.of(callable), definition.methods());
        assertEquals(1, definition.supertypes().size());
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> parameter.modifiers().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> field.modifiers().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> callable.modifiers().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> callable.parameters().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> callable.thrownTypes().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> definition.modifiers().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> definition.fields().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> definition.methods().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> definition.supertypes().clear()));
    }

    @Test void sourceUnitAndDiagnosticsDefensivelyCopyCollections() {
        var related = new ArrayList<>(List.of(new SourceLocation("other/Example.java", 1, 1, 2, 1)));
        var diagnostic = new AnalysisDiagnostic(DiagnosticSeverity.WARNING, "PARTIAL_PARSE", "Recovered definitions only",
                Optional.of(LOCATION), related);
        var imports = new ArrayList<>(List.of(new ImportInfo("java.util", false, true, LOCATION)));
        var types = new ArrayList<>(List.of(type(OWNER, Optional.of("Example"), TypeKind.CLASS,
                ClassNode.Nesting.TOP_LEVEL, Optional.empty(), List.of())));
        var calls = new ArrayList<>(List.of(new MethodCall("initialize()", "initialize", Optional.empty(), LOCATION,
                Optional.empty(), Optional.of(MethodId.canonical(OWNER, "initialize", List.of())),
                ResolutionStatus.RESOLVED, Optional.empty())));
        var diagnostics = new ArrayList<>(List.of(diagnostic));
        var unit = new SourceUnit(LOCATION.path(), "example", imports, types, calls, diagnostics, true);
        related.clear();
        imports.clear();
        types.clear();
        calls.clear();
        diagnostics.clear();
        assertEquals(1, diagnostic.relatedLocations().size());
        assertEquals(1, unit.imports().size());
        assertEquals(1, unit.types().size());
        assertEquals(1, unit.calls().size());
        assertEquals(List.of(diagnostic), unit.diagnostics());
        assertTrue(unit.partial());
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> diagnostic.relatedLocations().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> unit.imports().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> unit.types().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> unit.calls().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> unit.diagnostics().clear()));
    }

    @Test void callableKindReturnAndParameterIdentityMustAgree() {
        var id = MethodId.canonical(OWNER, "run", List.of());
        assertThrows(IllegalArgumentException.class, () -> new MethodNode(id, CallableKind.CONSTRUCTOR,
                Optional.empty(), List.of(), Set.of(), List.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new MethodNode(id, CallableKind.METHOD,
                Optional.empty(), List.of(), Set.of(), List.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new MethodNode(MethodId.canonical(OWNER, "<init>", List.of()),
                CallableKind.CONSTRUCTOR, Optional.of(VOID), List.of(), Set.of(), List.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new MethodNode(id, CallableKind.METHOD, Optional.of(VOID),
                List.of(new ParameterInfo("input", STRING, false, Set.of(), LOCATION)), Set.of(), List.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new MethodNode(id.at(new SourceLocation("other.java", 1, 1, 2, 1)),
                CallableKind.METHOD, Optional.of(VOID), List.of(), Set.of(), List.of(), LOCATION));
    }

    @Test void onlyLastParameterCanBeVarargsAndItMustHaveArrayType() {
        assertThrows(IllegalArgumentException.class, () -> new ParameterInfo("values", STRING, true, Set.of(), LOCATION));
        var varargs = new ParameterInfo("values", STRING.asArray(), true, Set.of(), LOCATION);
        var ordinary = new ParameterInfo("input", STRING, false, Set.of(), LOCATION);
        assertThrows(IllegalArgumentException.class, () -> method(OWNER, List.of(varargs, ordinary)));
        assertEquals("example.Example#run(java.lang.String,java.lang.String[])",
                method(OWNER, List.of(ordinary, varargs)).id().value());
    }

    @Test void localAndAnonymousTypesRequireLocationQualification() {
        var localId = SymbolId.canonical("example.Example$Local");
        assertThrows(IllegalArgumentException.class, () -> type(localId, Optional.of("Local"), TypeKind.CLASS,
                ClassNode.Nesting.LOCAL, Optional.of(OWNER), List.of()));
        assertEquals(localId.at(LOCATION), type(localId.at(LOCATION), Optional.of("Local"), TypeKind.CLASS,
                ClassNode.Nesting.LOCAL, Optional.of(OWNER), List.of()).id());
        var anonymous = SymbolId.canonical("example.Example$anonymous").at(LOCATION);
        assertTrue(type(anonymous, Optional.empty(), TypeKind.CLASS, ClassNode.Nesting.ANONYMOUS,
                Optional.of(OWNER), List.of()).simpleName().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> type(anonymous, Optional.empty(), TypeKind.INTERFACE,
                ClassNode.Nesting.ANONYMOUS, Optional.of(OWNER), List.of()));
    }

    @Test void memberTypesUseDollarAndPreserveLocationQualifiedOwnerDiscrimination() {
        var nested = SymbolId.canonical("example.Example$Inner");
        assertEquals(nested, type(nested, Optional.of("Inner"), TypeKind.CLASS, ClassNode.Nesting.MEMBER,
                Optional.of(OWNER), List.of()).id());
        assertThrows(IllegalArgumentException.class, () -> type(SymbolId.canonical("example.Example.Inner"),
                Optional.of("Inner"), TypeKind.CLASS, ClassNode.Nesting.MEMBER, Optional.of(OWNER), List.of()));
        assertThrows(IllegalArgumentException.class, () -> type(nested, Optional.of("Inner"), TypeKind.CLASS,
                ClassNode.Nesting.MEMBER, Optional.of(OWNER.at(LOCATION)), List.of()));
        assertEquals(nested.at(LOCATION), type(nested.at(LOCATION), Optional.of("Inner"), TypeKind.CLASS,
                ClassNode.Nesting.MEMBER, Optional.of(OWNER.at(LOCATION)), List.of()).id());
    }

    @Test void typeCannotOwnAnotherTypesMethod() {
        assertThrows(IllegalArgumentException.class, () -> type(OWNER, Optional.of("Example"), TypeKind.CLASS,
                ClassNode.Nesting.TOP_LEVEL, Optional.empty(), List.of(method(SymbolId.canonical("other.Type"), List.of()))));
    }

    @Test void duplicatesRemainSeparateValuesWithLocationEvidence() {
        var other = new SourceLocation("other/Example.java", 1, 1, 30, 1);
        var diagnostic = AnalysisDiagnostic.duplicateDeclaration(OWNER.value(), LOCATION, other);
        assertEquals("DUPLICATE_DECLARATION", diagnostic.code());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
        assertEquals(Optional.of(LOCATION), diagnostic.location());
        assertEquals(List.of(other), diagnostic.relatedLocations());
        assertTrue(diagnostic.message().contains(OWNER.value()));
        assertNotEquals(OWNER.at(LOCATION), OWNER.at(other));
        assertThrows(IllegalArgumentException.class, () -> AnalysisDiagnostic.duplicateDeclaration(OWNER.value(), LOCATION, LOCATION));
        var first = type(OWNER.at(LOCATION), Optional.of("Example"), TypeKind.CLASS,
                ClassNode.Nesting.TOP_LEVEL, Optional.empty(), List.of());
        // Lists deliberately retain repeated declarations for later duplicate detection.
        var unit = new SourceUnit(LOCATION.path(), "example", List.of(), List.of(first, first), List.of(), List.of(diagnostic), true);
        assertEquals(2, unit.types().size());
    }

    @Test void sourceUnitRejectsForeignDefinitionsButSupportsDefaultPackageAndEmptyPartialExtraction() {
        var definition = type(OWNER, Optional.of("Example"), TypeKind.CLASS, ClassNode.Nesting.TOP_LEVEL, Optional.empty(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new SourceUnit("Other.java", "example", List.of(),
                List.of(definition), List.of(), List.of(), false));
        var diagnostic = new AnalysisDiagnostic(DiagnosticSeverity.ERROR, "PARSE_FAILURE", "No reliable definitions",
                Optional.empty(), List.of());
        var partial = new SourceUnit(LOCATION.path(), "", List.of(), List.of(), List.of(), List.of(diagnostic), true);
        assertEquals("", partial.packageName());
        assertTrue(partial.types().isEmpty());
        assertTrue(partial.partial());
    }

    @Test void nullAndMeaninglessValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FieldInfo("value", VOID, Set.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new ParameterInfo("value", VOID, false, Set.of(), LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new ImportInfo("java.util.*", false, true, LOCATION));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisDiagnostic(DiagnosticSeverity.ERROR, " ", "message", Optional.empty(), List.of()));
        assertThrows(NullPointerException.class, () -> new SourceUnit(LOCATION.path(), "example", List.of(),
                java.util.Arrays.asList((ClassNode) null), List.of(), List.of(), false));
    }
}
