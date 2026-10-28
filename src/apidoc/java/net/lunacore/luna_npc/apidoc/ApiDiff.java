package net.lunacore.luna_npc.apidoc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// What changed in the API between two snapshots, as one flat list. Breaking = code written against the
// older version may no longer compile: anything removed, a changed signature or field type, or a new
// record component (its constructor changed). Added types, functions and enum constants are not.
// Prose edits are not changes.
final class ApiDiff {

    private final List<JsonObject> changes = new ArrayList<>();

    private ApiDiff() {
    }

    static JsonObject between(JsonObject from, JsonObject to) {
        ApiDiff diff = new ApiDiff();
        Map<String, JsonObject> oldTypes = types(from);
        Map<String, JsonObject> newTypes = types(to);
        for (String name : oldTypes.keySet()) {
            if (!newTypes.containsKey(name)) {
                diff.add(name, null, "type", "removed", true, oldTypes.get(name).get("kind").getAsString(), null);
            }
        }
        for (Map.Entry<String, JsonObject> entry : newTypes.entrySet()) {
            JsonObject old = oldTypes.get(entry.getKey());
            if (old == null) {
                diff.add(entry.getKey(), null, "type", "added", false, null, entry.getValue().get("kind").getAsString());
                continue;
            }
            boolean record = entry.getValue().get("kind").getAsString().equals("record");
            diff.fields(entry.getKey(), old.getAsJsonArray("fields"), entry.getValue().getAsJsonArray("fields"),
                    record ? "field" : "constant", record);
            diff.functions(entry.getKey(), old.getAsJsonArray("functions"), entry.getValue().getAsJsonArray("functions"));
        }
        Map<String, JsonObject> oldEnums = enums(from);
        Map<String, JsonObject> newEnums = enums(to);
        for (String name : oldEnums.keySet()) {
            if (!newEnums.containsKey(name)) {
                diff.add(name, null, "type", "removed", true, "enum", null);
            }
        }
        for (Map.Entry<String, JsonObject> entry : newEnums.entrySet()) {
            JsonObject old = oldEnums.get(entry.getKey());
            if (old == null) {
                diff.add(entry.getKey(), null, "type", "added", false, null, "enum");
                continue;
            }
            diff.fields(entry.getKey(), old.getAsJsonArray("constants"), entry.getValue().getAsJsonArray("constants"),
                    "enum-constant", false);
            diff.functions(entry.getKey(), old.getAsJsonArray("methods"), entry.getValue().getAsJsonArray("methods"));
        }
        JsonObject result = new JsonObject();
        result.addProperty("from", from.get("version").getAsString());
        result.addProperty("to", to.get("version").getAsString());
        JsonObject summary = new JsonObject();
        for (String change : List.of("added", "removed", "changed")) {
            summary.addProperty(change, diff.changes.stream().filter(c -> c.get("change").getAsString().equals(change)).count());
        }
        summary.addProperty("breaking", diff.changes.stream().filter(c -> c.get("breaking").getAsBoolean()).count());
        result.add("summary", summary);
        JsonArray list = new JsonArray();
        diff.changes.forEach(list::add);
        result.add("changes", list);
        return result;
    }

    // Record components and enum constants count as members; for records a new component changes the
    // constructor, a new UPPER_CASE constant does not.
    private void fields(String type, JsonArray oldFields, JsonArray newFields, String memberKind, boolean record) {
        Map<String, String> old = new LinkedHashMap<>();
        oldFields.forEach(field -> old.put(Snapshot.name(field), typeOf(field)));
        Map<String, String> now = new LinkedHashMap<>();
        newFields.forEach(field -> now.put(Snapshot.name(field), typeOf(field)));
        String kind = memberKind.equals("constant") ? "field" : memberKind;
        old.forEach((name, fieldType) -> {
            if (!now.containsKey(name)) {
                add(type, name, kind, "removed", true, describe(name, fieldType), null);
            } else if (!fieldType.equals(now.get(name))) {
                add(type, name, kind, "changed", true, describe(name, fieldType), describe(name, now.get(name)));
            }
        });
        now.forEach((name, fieldType) -> {
            if (!old.containsKey(name)) {
                add(type, name, kind, "added", record && !name.equals(name.toUpperCase()), null, describe(name, fieldType));
            }
        });
    }

    private void functions(String type, JsonArray oldFunctions, JsonArray newFunctions) {
        Map<String, JsonObject> old = byKey(oldFunctions);
        Map<String, JsonObject> now = byKey(newFunctions);
        List<String> removed = new ArrayList<>(old.keySet());
        removed.removeAll(now.keySet());
        List<String> added = new ArrayList<>(now.keySet());
        added.removeAll(old.keySet());
        // A removed overload pairs with the added one of the same name: the only one, or else the shortest
        // whose parameters start with the removed one's (create(String) -> create(String, NpcEditAccess)).
        for (String key : List.copyOf(removed)) {
            String name = Snapshot.name(old.get(key));
            List<String> sameName = added.stream().filter(other -> Snapshot.name(now.get(other)).equals(name)).toList();
            long removedSameName = removed.stream().filter(other -> Snapshot.name(old.get(other)).equals(name)).count();
            String oldParams = key.substring(key.indexOf('(') + 1, key.length() - 1);
            String match = sameName.size() == 1 && removedSameName == 1 ? sameName.get(0) : sameName.stream()
                    .filter(other -> other.substring(other.indexOf('(') + 1).startsWith(oldParams))
                    .min(java.util.Comparator.comparingInt(String::length)).orElse(null);
            if (match != null) {
                add(type, name, "function", "changed", true, signature(old.get(key)), signature(now.get(match)));
                removed.remove(key);
                added.remove(match);
            }
        }
        removed.forEach(key -> add(type, Snapshot.name(old.get(key)), "function", "removed", true, signature(old.get(key)), null));
        added.forEach(key -> add(type, Snapshot.name(now.get(key)), "function", "added", false, null, signature(now.get(key))));
    }

    private void add(String type, String member, String memberKind, String change, boolean breaking, String before, String after) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        if (member != null) {
            entry.addProperty("member", member);
        }
        entry.addProperty("memberKind", memberKind);
        entry.addProperty("change", change);
        entry.addProperty("breaking", breaking);
        if (before != null) {
            entry.addProperty("before", before);
        }
        if (after != null) {
            entry.addProperty("after", after);
        }
        changes.add(entry);
    }

    private static String typeOf(JsonElement field) {
        JsonObject object = field.getAsJsonObject();
        return object.has("type") ? object.get("type").getAsString() : "";
    }

    private static String describe(String name, String type) {
        return type.isEmpty() ? name : type + " " + name;
    }

    private static String signature(JsonObject function) {
        return function.get("signature").getAsString();
    }

    private static Map<String, JsonObject> byKey(JsonArray functions) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        functions.forEach(function -> map.put(SourceReader.key(function.getAsJsonObject()), function.getAsJsonObject()));
        return map;
    }

    private static Map<String, JsonObject> types(JsonObject snapshot) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        snapshot.getAsJsonArray("types").forEach(type -> map.put(Snapshot.name(type), type.getAsJsonObject()));
        return map;
    }

    private static Map<String, JsonObject> enums(JsonObject snapshot) {
        Map<String, JsonObject> map = new LinkedHashMap<>();
        snapshot.getAsJsonArray("enumFamilies").forEach(family -> family.getAsJsonObject().getAsJsonArray("members")
                .forEach(member -> map.put(Snapshot.name(member), member.getAsJsonObject())));
        return map;
    }
}
