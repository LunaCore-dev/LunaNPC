package net.lunacore.luna_npc.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// Player skins downloaded once and kept with the world for good (LunaNpcApi.skins). Every player on the
// server gets them, so an NPC wearing one looks the same to everyone; later skin changes or the account
// going away never touch a stored skin. Call from the server thread; download results also complete on it.
// Skins are shared things like routes, not covered by an NPC's NpcEditAccess.
public interface SkinRegistry {

    // Downloads by player name (arms read from the profile) or from an http(s) URL of a 64x64 or legacy
    // 64x32 PNG (classic arms). The new skin is named playermodel, playermodel_2, ...
    default CompletableFuture<NpcSkinResult> download(String playerNameOrUrl) {
        return download(playerNameOrUrl, null, null);
    }

    // name null = the next free playermodel name; arms null = from the profile (classic for a URL).
    // URLs on local or private networks are refused; files over 1 MB too.
    CompletableFuture<NpcSkinResult> download(String playerNameOrUrl, String name, NpcSkinArms arms);

    List<NpcSkin> all();

    Optional<NpcSkin> byId(String id);

    Optional<NpcSkin> byName(String name);

    // Names follow the model id rules (lowercase a-z, 0-9, _ . -, up to 64) and are unique. Empty when it
    // worked, otherwise the reason.
    Optional<NpcSkinFailure> rename(String id, String name);

    Optional<NpcSkinFailure> setArms(String id, NpcSkinArms arms);

    // NPCs still wearing a deleted skin fall back to the default player skin.
    boolean delete(String id);
}
