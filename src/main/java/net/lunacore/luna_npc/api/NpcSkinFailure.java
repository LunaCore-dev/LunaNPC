package net.lunacore.luna_npc.api;

import java.util.Locale;

// Why a skin download or edit did not happen (see SkinRegistry).
public enum NpcSkinFailure {
    // Neither a player name nor an http(s) URL.
    INVALID_INPUT,
    // No Minecraft account has this name.
    NO_SUCH_PLAYER,
    // The account wears a default skin, so there is no skin file to download.
    NO_CUSTOM_SKIN,
    // The URL points at a local or private network address.
    BLOCKED_ADDRESS,
    // The server could not be reached, timed out or answered with an error.
    NETWORK_ERROR,
    // The file is larger than 1 MB.
    TOO_LARGE,
    NOT_A_PNG,
    // The PNG is not 64x64 (or legacy 64x32).
    WRONG_SIZE,
    // The skin name breaks the naming rules or another skin already has it.
    INVALID_NAME,
    // The world already holds the maximum number of downloaded skins.
    LIMIT_REACHED,
    // No downloaded skin has this id.
    NO_SUCH_SKIN;

    // The lang key of the message shown in-game.
    public String translationKey() {
        return "message.luna_npc.skin." + name().toLowerCase(Locale.ROOT);
    }
}
