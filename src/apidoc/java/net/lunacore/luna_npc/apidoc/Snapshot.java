package net.lunacore.luna_npc.apidoc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Builds one version's snapshot: the structure read from source, with every description, group, kind and
// throws note carried over from an earlier snapshot. An overload is matched by name and parameter types,
// or by name alone when that name has a single overload on both sides (a changed signature keeps its
// prose). Enums keep their family; new ones land in UNSORTED. missing() lists every empty text left.
final class Snapshot {

    static final String UNSORTED = "Unsorted";

    private Snapshot() {
    }

    static JsonObject build(SourceReader source, String version, JsonObject previous) {
        Map<String, JsonObject> oldTypes = new LinkedHashMap<>();
        Map<String, JsonObject> oldEnums = new LinkedHashMap<>();
        if (previous != null) {
            previous.getAsJsonArray("types").forEach(type -> oldTypes.put(name(type), type.getAsJsonObject()));
            previous.getAsJsonArray("enumFamilies").forEach(family -> family.getAsJsonObject()
                    .getAsJsonArray("members").forEach(member -> oldEnums.put(name(member), member.getAsJsonObject())));
        }
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("package", "net.lunacore.luna_npc.api");
        snapshot.addProperty("version", version);

        JsonArray types = new JsonArray();
        List<String> order = new ArrayList<>(oldTypes.keySet());
        order.retainAll(source.types.keySet());
        source.types.keySet().stream().filter(name -> !order.contains(name)).forEach(order::add);
        for (String name : order) {
            JsonObject type = source.types.get(name);
            JsonObject old = oldTypes.get(name);
            JsonObject out = new JsonObject();
            out.addProperty("name", name);
            out.addProperty("kind", type.get("kind").getAsString());
            out.addProperty("group", text(old, "group"));
            out.addProperty("summary", text(old, "summary"));
            out.add("fields", fields(type.getAsJsonArray("fields"), old == null ? null : old.getAsJsonArray("fields")));
            out.add("functions", functions(type.getAsJsonArray("functions"),
                    old == null ? null : old.getAsJsonArray("functions"), name));
            types.add(out);
        }
        snapshot.add("types", types);

        JsonArray families = new JsonArray();
        Map<String, JsonArray> placed = new LinkedHashMap<>();
        if (previous != null) {
            for (JsonElement element : previous.getAsJsonArray("enumFamilies")) {
                JsonObject family = element.getAsJsonObject();
                JsonObject out = new JsonObject();
                out.addProperty("name", name(family));
                out.addProperty("whenToUse", text(family, "whenToUse"));
                JsonArray members = new JsonArray();
                out.add("members", members);
                family.getAsJsonArray("members").forEach(member -> placed.put(name(member), members));
                families.add(out);
            }
        }
        JsonArray unsorted = null;
        for (Map.Entry<String, JsonObject> entry : source.enums.entrySet()) {
            JsonArray members = placed.get(entry.getKey());
            if (members == null) {
                if (unsorted == null) {
                    JsonObject family = new JsonObject();
                    family.addProperty("name", UNSORTED);
                    family.addProperty("whenToUse", "");
                    unsorted = new JsonArray();
                    family.add("members", unsorted);
                    families.add(family);
                }
                members = unsorted;
            }
            JsonObject old = oldEnums.get(entry.getKey());
            JsonObject member = new JsonObject();
            member.addProperty("name", entry.getKey());
            member.addProperty("summary", text(old, "summary"));
            member.add("constants", fields(entry.getValue().getAsJsonArray("constants"),
                    old == null ? null : old.getAsJsonArray("constants")));
            member.add("methods", functions(entry.getValue().getAsJsonArray("methods"),
                    old == null ? null : old.getAsJsonArray("methods"), entry.getKey()));
            members.add(member);
        }
        for (int index = families.size() - 1; index >= 0; index--) {
            if (families.get(index).getAsJsonObject().getAsJsonArray("members").isEmpty()) {
                families.remove(index);
            }
        }
        snapshot.add("enumFamilies", families);
        return snapshot;
    }

    private static JsonArray fields(JsonArray fields, JsonArray oldFields) {
        Map<String, JsonObject> old = byName(oldFields);
        JsonArray out = new JsonArray();
        for (JsonElement element : fields) {
            JsonObject field = element.getAsJsonObject().deepCopy();
            field.addProperty("desc", text(old.get(name(field)), "desc"));
            out.add(field);
        }
        return out;
    }

    private static JsonArray functions(JsonArray functions, JsonArray oldFunctions, String typeName) {
        Map<String, JsonObject> byKey = new LinkedHashMap<>();
        Map<String, List<JsonObject>> byName = new LinkedHashMap<>();
        if (oldFunctions != null) {
            for (JsonElement element : oldFunctions) {
                JsonObject function = element.getAsJsonObject();
                byKey.put(SourceReader.key(function), function);
                byName.computeIfAbsent(name(function), ignored -> new ArrayList<>()).add(function);
            }
        }
        Map<String, Integer> overloads = new LinkedHashMap<>();
        functions.forEach(function -> overloads.merge(name(function), 1, Integer::sum));
        JsonArray out = new JsonArray();
        for (JsonElement element : functions) {
            JsonObject function = element.getAsJsonObject();
            JsonObject old = byKey.get(SourceReader.key(function));
            List<JsonObject> sameName = byName.getOrDefault(name(function), List.of());
            if (old == null && sameName.size() == 1 && overloads.get(name(function)) == 1) {
                old = sameName.get(0);
            }
            JsonObject result = new JsonObject();
            result.addProperty("name", name(function));
            result.addProperty("signature", function.get("signature").getAsString());
            String kind = text(old, "kind");
            result.addProperty("kind", kind.isEmpty() ? guessKind(function, typeName) : kind);
            result.addProperty("static", function.get("static").getAsBoolean());
            result.add("params", fields(function.getAsJsonArray("params"), old == null ? null : old.getAsJsonArray("params")));
            JsonObject returns = function.getAsJsonObject("returns").deepCopy();
            returns.addProperty("desc", old != null && old.has("returns") ? text(old.getAsJsonObject("returns"), "desc") : "");
            result.add("returns", returns);
            result.addProperty("description", text(old, "description"));
            if (old != null && old.has("throws")) {
                result.add("throws", old.get("throws").deepCopy());
            }
            out.add(result);
        }
        return out;
    }

    private static String guessKind(JsonObject function, String typeName) {
        String name = name(function);
        String returns = function.getAsJsonObject("returns").get("type").getAsString();
        boolean noParams = function.getAsJsonArray("params").isEmpty();
        if (function.get("static").getAsBoolean() && returns.equals(typeName.substring(typeName.lastIndexOf('.') + 1))) {
            return "static-factory";
        }
        if (name.startsWith("with")) {
            return "builder";
        }
        if (name.startsWith("is") && returns.equals("boolean") && noParams) {
            return name.equals("isValid") ? "validation" : "computed";
        }
        if (returns.equals("void")) {
            return "mutator";
        }
        return noParams ? "accessor" : "method";
    }

    // Every empty description, summary, group or family placement, as "where: what" lines.
    static List<String> missing(JsonObject snapshot) {
        List<String> missing = new ArrayList<>();
        for (JsonElement element : snapshot.getAsJsonArray("types")) {
            JsonObject type = element.getAsJsonObject();
            String where = name(type);
            check(missing, type, "group", where);
            check(missing, type, "summary", where);
            type.getAsJsonArray("fields").forEach(field -> check(missing, field.getAsJsonObject(), "desc", where + "." + name(field)));
            type.getAsJsonArray("functions").forEach(function -> checkFunction(missing, function.getAsJsonObject(), where));
        }
        for (JsonElement element : snapshot.getAsJsonArray("enumFamilies")) {
            JsonObject family = element.getAsJsonObject();
            for (JsonElement memberElement : family.getAsJsonArray("members")) {
                JsonObject member = memberElement.getAsJsonObject();
                String where = name(member);
                if (name(family).equals(UNSORTED)) {
                    missing.add(where + ": enum family");
                }
                check(missing, member, "summary", where);
                member.getAsJsonArray("constants").forEach(constant -> check(missing, constant.getAsJsonObject(), "desc", where + "." + name(constant)));
                member.getAsJsonArray("methods").forEach(method -> checkFunction(missing, method.getAsJsonObject(), where));
            }
        }
        return missing;
    }

    private static void checkFunction(List<String> missing, JsonObject function, String type) {
        String where = type + "." + SourceReader.key(function);
        check(missing, function, "description", where);
        function.getAsJsonArray("params").forEach(param -> check(missing, param.getAsJsonObject(), "desc", where + " param " + name(param)));
        JsonObject returns = function.getAsJsonObject("returns");
        if (!returns.get("type").getAsString().equals("void")) {
            check(missing, returns, "desc", where + " returns");
        }
    }

    private static void check(List<String> missing, JsonObject object, String key, String where) {
        if (text(object, key).isBlank()) {
            missing.add(where + ": " + key);
        }
    }

    private static Map<String, JsonObject> byName(JsonArray array) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        if (array != null) {
            array.forEach(element -> map.put(name(element), element.getAsJsonObject()));
        }
        return map;
    }

    static String name(JsonElement element) {
        return element.getAsJsonObject().get("name").getAsString();
    }

    private static String text(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
