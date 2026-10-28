package net.lunacore.luna_npc.apidoc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// The API reference pipeline behind ./gradlew exportApi / checkApi (see CLAUDE.md).
//   export: writes <out>/<version>.json from the API source, its prose carried over from --descriptions,
//           else that version's existing snapshot, else the newest older one; then rewrites versions.json,
//           every changes/<a>_to_<b>.json between neighbouring versions and, for the newest, --latest.
//   check:  fails when the snapshot for --version is missing, out of date with the source, or has empty texts.
public final class ApiExport {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Comparator<String> BY_VERSION = Comparator.comparing(ApiExport::versionParts,
            (a, b) -> {
                for (int index = 0; index < Math.max(a.length, b.length); index++) {
                    int left = index < a.length ? a[index] : 0;
                    int right = index < b.length ? b[index] : 0;
                    if (left != right) {
                        return Integer.compare(left, right);
                    }
                }
                return 0;
            });

    private ApiExport() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = new HashMap<>();
        for (int index = 1; index + 1 < args.length; index += 2) {
            options.put(args[index], args[index + 1]);
        }
        Path source = Path.of(options.get("--source"));
        String version = options.get("--version");
        Path out = Path.of(options.get("--out"));
        SourceReader reader = SourceReader.read(source);
        if (args[0].equals("check")) {
            System.exit(check(reader, version, out) ? 0 : 1);
        }
        export(reader, version, out, options.get("--descriptions"), options.get("--latest"));
    }

    private static void export(SourceReader reader, String version, Path out, String descriptions, String latest)
            throws IOException {
        Files.createDirectories(out.resolve("changes"));
        JsonObject previous = descriptions != null ? load(Path.of(descriptions)) : previousSnapshot(out, version);
        JsonObject snapshot = Snapshot.build(reader, version, previous);
        write(out.resolve(version + ".json"), snapshot);
        List<String> versions = versions(out);
        JsonObject index = new JsonObject();
        index.addProperty("latest", versions.get(versions.size() - 1));
        JsonArray list = new JsonArray();
        versions.forEach(list::add);
        index.add("versions", list);
        write(out.resolve("versions.json"), index);
        for (int step = 1; step < versions.size(); step++) {
            String from = versions.get(step - 1);
            String to = versions.get(step);
            write(out.resolve("changes").resolve(from + "_to_" + to + ".json"),
                    ApiDiff.between(load(out.resolve(from + ".json")), load(out.resolve(to + ".json"))));
        }
        if (latest != null && version.equals(versions.get(versions.size() - 1))) {
            write(Path.of(latest), snapshot);
        }
        List<String> missing = Snapshot.missing(snapshot);
        System.out.println("API " + version + ": " + snapshot.getAsJsonArray("types").size() + " types, "
                + missing.size() + " empty texts");
        missing.forEach(line -> System.out.println("  " + line));
    }

    private static boolean check(SourceReader reader, String version, Path out) throws IOException {
        Path file = out.resolve(version + ".json");
        if (!Files.exists(file)) {
            System.out.println("No API reference for " + version + " yet: run ./gradlew exportApi");
            return false;
        }
        JsonObject saved = load(file);
        boolean upToDate = Snapshot.build(reader, version, saved).equals(saved);
        if (!upToDate) {
            System.out.println("API reference " + version + " is out of date with the source: run ./gradlew exportApi");
        }
        List<String> missing = Snapshot.missing(saved);
        if (!missing.isEmpty()) {
            System.out.println("API reference " + version + " has " + missing.size() + " empty texts:");
            missing.forEach(line -> System.out.println("  " + line));
        }
        return upToDate && missing.isEmpty();
    }

    private static JsonObject previousSnapshot(Path out, String version) throws IOException {
        if (Files.exists(out.resolve(version + ".json"))) {
            return load(out.resolve(version + ".json"));
        }
        List<String> older = versions(out).stream().filter(other -> BY_VERSION.compare(other, version) < 0).toList();
        return older.isEmpty() ? null : load(out.resolve(older.get(older.size() - 1) + ".json"));
    }

    private static List<String> versions(Path out) throws IOException {
        try (Stream<Path> files = Files.list(out)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d+(\\.\\d+)*\\.json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted(BY_VERSION).toList();
        }
    }

    private static int[] versionParts(String version) {
        return Stream.of(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    private static JsonObject load(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }
}
