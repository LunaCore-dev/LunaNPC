package net.lunacore.luna_npc.apidoc;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Reads the API package source into the reference's shape, structure only (names, kinds, signatures,
// parameter and return types); the prose is merged in afterwards from an earlier snapshot. Nested records
// and classes become their own "Outer.Inner" types, nested interfaces (the event listeners) fields of
// their outer type, and enums enum members. Codec fields and functions whose signature names an internal
// LunaNPC type (the events' fire hooks) are plumbing, not API, and left out.
final class SourceReader {

    final Map<String, JsonObject> types = new LinkedHashMap<>();
    final Map<String, JsonObject> enums = new LinkedHashMap<>();
    // Simple names imported from LunaNPC outside the api package, for the file being read.
    private Set<String> internalNames = Set.of();

    static SourceReader read(Path apiDir) throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        SourceReader reader = new SourceReader();
        List<Path> files;
        try (Stream<Path> listing = Files.list(apiDir)) {
            files = listing.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .sorted().toList();
        }
        for (Path file : files) {
            CompilationUnit unit = StaticJavaParser.parse(file);
            reader.internalNames = unit.getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .filter(imported -> imported.startsWith("net.lunacore.luna_npc.") && !imported.startsWith("net.lunacore.luna_npc.api."))
                    .map(imported -> imported.substring(imported.lastIndexOf('.') + 1))
                    .collect(Collectors.toSet());
            for (TypeDeclaration<?> type : unit.getTypes()) {
                if (type.isPublic()) {
                    reader.visit(type, null);
                }
            }
        }
        return reader;
    }

    private void visit(TypeDeclaration<?> type, JsonObject outer) {
        String name = outer == null ? type.getNameAsString() : outer.get("name").getAsString() + "." + type.getNameAsString();
        if (type instanceof EnumDeclaration enumType) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", name);
            JsonArray constants = new JsonArray();
            enumType.getEntries().forEach(constant -> {
                JsonObject value = new JsonObject();
                value.addProperty("name", constant.getNameAsString());
                constants.add(value);
            });
            entry.add("constants", constants);
            entry.add("methods", methods(type, Set.of(), false));
            enums.put(name, entry);
            return;
        }
        boolean isInterface = type instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface();
        if (outer != null && isInterface) {
            JsonObject listener = new JsonObject();
            listener.addProperty("name", type.getNameAsString());
            listener.addProperty("type", "interface");
            outer.getAsJsonArray("fields").add(listener);
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("kind", type instanceof RecordDeclaration ? "record" : isInterface ? "interface" : "class");
        JsonArray fields = new JsonArray();
        Set<String> components = Set.of();
        if (type instanceof RecordDeclaration record) {
            components = record.getParameters().stream().map(Parameter::getNameAsString).collect(Collectors.toSet());
            record.getParameters().forEach(parameter -> fields.add(field(parameter.getNameAsString(), typeOf(parameter))));
        }
        for (FieldDeclaration field : type.getFields()) {
            boolean constant = isInterface || (field.isPublic() && field.isStatic() && field.isFinal());
            for (VariableDeclarator variable : field.getVariables()) {
                String fieldType = variable.getTypeAsString();
                if (constant && !fieldType.contains("Codec")) {
                    fields.add(field(variable.getNameAsString(), fieldType));
                }
            }
        }
        entry.add("fields", fields);
        entry.add("functions", methods(type, components, isInterface));
        types.put(name, entry);
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested && (nested.isPublic() || isInterface)) {
                visit(nested, entry);
            }
        }
    }

    // The declaration as written: modifiers, type parameters, return type, name and parameters.
    private static String signature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();
        method.getModifiers().forEach(modifier -> signature.append(modifier.getKeyword().asString()).append(' '));
        if (!method.getTypeParameters().isEmpty()) {
            signature.append(method.getTypeParameters().stream().map(Object::toString)
                    .collect(Collectors.joining(", ", "<", "> ")));
        }
        signature.append(method.getTypeAsString()).append(' ').append(method.getNameAsString())
                .append(method.getParameters().stream().map(parameter -> typeOf(parameter) + " " + parameter.getNameAsString())
                        .collect(Collectors.joining(", ", "(", ")")));
        return signature.toString();
    }

    private boolean namesInternalType(MethodDeclaration method) {
        Stream<String> types = Stream.concat(Stream.of(method.getTypeAsString()),
                method.getParameters().stream().map(Parameter::getTypeAsString));
        return types.anyMatch(type -> internalNames.stream().anyMatch(internal -> type.matches(".*\\b" + internal + "\\b.*")));
    }

    private static JsonObject field(String name, String type) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        return field;
    }

    private static String typeOf(Parameter parameter) {
        return parameter.getTypeAsString() + (parameter.isVarArgs() ? "..." : "");
    }

    // Public methods (non-private ones in an interface), minus a record's own accessors.
    private JsonArray methods(TypeDeclaration<?> type, Set<String> components, boolean isInterface) {
        JsonArray functions = new JsonArray();
        for (MethodDeclaration method : type.getMethods()) {
            boolean visible = isInterface ? !method.isPrivate() : method.isPublic();
            boolean accessor = components.contains(method.getNameAsString()) && method.getParameters().isEmpty();
            if (!visible || accessor || namesInternalType(method)) {
                continue;
            }
            JsonObject function = new JsonObject();
            function.addProperty("name", method.getNameAsString());
            function.addProperty("signature", signature(method));
            function.addProperty("static", method.isStatic());
            JsonArray params = new JsonArray();
            method.getParameters().forEach(parameter -> {
                JsonObject param = new JsonObject();
                param.addProperty("name", parameter.getNameAsString());
                param.addProperty("type", typeOf(parameter));
                params.add(param);
            });
            function.add("params", params);
            JsonObject returns = new JsonObject();
            returns.addProperty("type", method.getTypeAsString());
            function.add("returns", returns);
            functions.add(function);
        }
        return functions;
    }

    // Identifies one overload: its name and parameter types, ignoring whitespace.
    static String key(JsonObject function) {
        StringBuilder key = new StringBuilder(function.get("name").getAsString()).append('(');
        JsonArray params = function.has("params") ? function.getAsJsonArray("params") : new JsonArray();
        for (int index = 0; index < params.size(); index++) {
            key.append(index == 0 ? "" : ",")
                    .append(params.get(index).getAsJsonObject().get("type").getAsString().replace(" ", ""));
        }
        return key.append(')').toString();
    }
}
