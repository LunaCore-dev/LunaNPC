package net.lunacore.luna_npc.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.lunacore.luna_npc.api.LunaNpcApi;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.api.NpcSkinFailure;

// Fetches a skin PNG off the server thread: a player name goes through Mojang's profile API (which also
// says whether the arms are slim), anything else must be an http(s) URL. Every host is resolved first and
// refused when it is local or private, so the server can't be pointed at its own network; redirects are
// followed by hand for the same check. The image is re-encoded as a clean 64x64 PNG, a legacy 64x32 skin
// expanded the way vanilla does it.
final class SkinDownloader {

    static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

    record Fetched(byte[] png, NpcSkinArms arms, String source) {
    }

    static final class Failed extends Exception {
        final NpcSkinFailure failure;

        Failed(NpcSkinFailure failure) {
            super(failure.name(), null, false, false);
            this.failure = failure;
        }
    }

    private record Response(int status, byte[] body) {
    }

    private SkinDownloader() {
    }

    static boolean isUrl(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    static Fetched fetch(String input) throws Failed {
        String trimmed = input == null ? "" : input.strip();
        if (isUrl(trimmed)) {
            return new Fetched(toSkin(get(uri(trimmed), true).body()), NpcSkinArms.CLASSIC, trimmed);
        }
        if (!PLAYER_NAME.matcher(trimmed).matches()) {
            throw new Failed(NpcSkinFailure.INVALID_INPUT);
        }
        Response lookup = get(uri("https://api.mojang.com/users/profiles/minecraft/" + trimmed), false);
        if (lookup.status() == 204 || lookup.status() == 404) {
            throw new Failed(NpcSkinFailure.NO_SUCH_PLAYER);
        }
        if (lookup.status() != 200) {
            throw new Failed(NpcSkinFailure.NETWORK_ERROR);
        }
        JsonObject account = json(lookup);
        String id = account.get("id").getAsString();
        String name = account.has("name") ? account.get("name").getAsString() : trimmed;
        JsonObject profile = json(get(uri("https://sessionserver.mojang.com/session/minecraft/profile/" + id), true));
        JsonObject skin = null;
        for (JsonElement property : profile.getAsJsonArray("properties")) {
            JsonObject entry = property.getAsJsonObject();
            if ("textures".equals(entry.get("name").getAsString())) {
                String decoded = new String(Base64.getDecoder().decode(entry.get("value").getAsString()),
                        StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
                skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            }
        }
        if (skin == null || !skin.has("url")) {
            throw new Failed(NpcSkinFailure.NO_CUSTOM_SKIN);
        }
        JsonObject metadata = skin.getAsJsonObject("metadata");
        boolean slim = metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
        byte[] png = toSkin(get(uri(skin.get("url").getAsString()), true).body());
        return new Fetched(png, slim ? NpcSkinArms.SLIM : NpcSkinArms.CLASSIC, name);
    }

    private static URI uri(String text) throws Failed {
        try {
            URI uri = URI.create(text);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new Failed(NpcSkinFailure.INVALID_INPUT);
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new Failed(NpcSkinFailure.INVALID_INPUT);
        }
    }

    private static JsonObject json(Response response) throws Failed {
        try {
            return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new Failed(NpcSkinFailure.NETWORK_ERROR);
        }
    }

    // requireOk: anything but 200 is a network error; otherwise the caller reads the status itself.
    private static Response get(URI uri, boolean requireOk) throws Failed {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkAddress(current.getHost());
            HttpResponse<InputStream> response;
            try {
                response = CLIENT.send(HttpRequest.newBuilder(current).timeout(TIMEOUT)
                        .header("User-Agent", "LunaNPC/" + LunaNpcApi.apiVersion()).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException exception) {
                throw new Failed(NpcSkinFailure.NETWORK_ERROR);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new Failed(NpcSkinFailure.NETWORK_ERROR);
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.headers().firstValue("Location").isPresent()) {
                close(response.body());
                current = uri(current.resolve(response.headers().firstValue("Location").get()).toString());
                continue;
            }
            byte[] body = read(response.body());
            if (requireOk && status != 200) {
                throw new Failed(NpcSkinFailure.NETWORK_ERROR);
            }
            return new Response(status, body);
        }
        throw new Failed(NpcSkinFailure.NETWORK_ERROR);
    }

    private static byte[] read(InputStream stream) throws Failed {
        try (stream) {
            byte[] body = stream.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                throw new Failed(NpcSkinFailure.TOO_LARGE);
            }
            return body;
        } catch (IOException exception) {
            throw new Failed(NpcSkinFailure.NETWORK_ERROR);
        }
    }

    private static void close(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Nothing was read from a redirect body.
        }
    }

    private static void checkAddress(String host) throws Failed {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new Failed(NpcSkinFailure.NETWORK_ERROR);
        }
        for (InetAddress address : addresses) {
            byte[] raw = address.getAddress();
            boolean uniqueLocal = address instanceof Inet6Address && (raw[0] & 0xFE) == 0xFC;
            boolean sharedSpace = raw.length == 4 && (raw[0] & 0xFF) == 100 && (raw[1] & 0xC0) == 64;
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocal || sharedSpace) {
                throw new Failed(NpcSkinFailure.BLOCKED_ADDRESS);
            }
        }
    }

    private static byte[] toSkin(byte[] file) throws Failed {
        if (file.length < PNG_SIGNATURE.length || !java.util.Arrays.equals(file, 0, PNG_SIGNATURE.length,
                PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
            throw new Failed(NpcSkinFailure.NOT_A_PNG);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(file));
        } catch (IOException exception) {
            throw new Failed(NpcSkinFailure.NOT_A_PNG);
        }
        if (image == null) {
            throw new Failed(NpcSkinFailure.NOT_A_PNG);
        }
        if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
            throw new Failed(NpcSkinFailure.WRONG_SIZE);
        }
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < 64; x++) {
                skin.setRGB(x, y, image.getRGB(x, y));
            }
        }
        if (image.getHeight() == 32) {
            expandLegacy(skin);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(skin, "png", out);
        } catch (IOException exception) {
            throw new Failed(NpcSkinFailure.NOT_A_PNG);
        }
        return out.toByteArray();
    }

    // Vanilla's legacy-skin step: the old format has one arm and one leg, mirrored into the new left limbs.
    private static void expandLegacy(BufferedImage skin) {
        copyMirrored(skin, 4, 16, 16, 32, 4, 4);
        copyMirrored(skin, 8, 16, 16, 32, 4, 4);
        copyMirrored(skin, 0, 20, 24, 32, 4, 12);
        copyMirrored(skin, 4, 20, 16, 32, 4, 12);
        copyMirrored(skin, 8, 20, 8, 32, 4, 12);
        copyMirrored(skin, 12, 20, 16, 32, 4, 12);
        copyMirrored(skin, 44, 16, -8, 32, 4, 4);
        copyMirrored(skin, 48, 16, -8, 32, 4, 4);
        copyMirrored(skin, 40, 20, 0, 32, 4, 12);
        copyMirrored(skin, 44, 20, -8, 32, 4, 12);
        copyMirrored(skin, 48, 20, -16, 32, 4, 12);
        copyMirrored(skin, 52, 20, -8, 32, 4, 12);
    }

    private static void copyMirrored(BufferedImage skin, int fromX, int fromY, int offsetX, int offsetY, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                skin.setRGB(fromX + offsetX + width - 1 - x, fromY + offsetY + y, skin.getRGB(fromX + x, fromY + y));
            }
        }
    }
}
