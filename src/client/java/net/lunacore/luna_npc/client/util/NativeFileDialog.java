package net.lunacore.luna_npc.client.util;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

// Native OS open-file dialog on top of SDL3, which replaced GLFW and tinyfd in Minecraft 26.3. Call open()
// on the render thread; the future completes on the render thread with the chosen path, or null on cancel.
public final class NativeFileDialog {

    private NativeFileDialog() {
    }

    // extensions is SDL's pattern list: bare extensions separated by ';' (e.g. "json;bbmodel").
    public static CompletableFuture<String> open(String title, String filterName, String extensions) {
        CompletableFuture<String> chosen = new CompletableFuture<>();
        // SDL reads the filters until the callback fires, so they live on the heap rather than the stack.
        ByteBuffer name = MemoryUtil.memUTF8(filterName);
        ByteBuffer pattern = MemoryUtil.memUTF8(extensions);
        SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).set(name, pattern);
        SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, fileList, filter) ->
                chosen.complete(fileList == MemoryUtil.NULL ? null
                        : MemoryUtil.memUTF8Safe(MemoryUtil.memGetAddress(fileList))));

        // An exclusive-fullscreen window minimizes when the dialog steals focus; suppress that for the
        // dialog's lifetime and restore whatever value was set before.
        String previousHint = SDLHints.SDL_GetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS);
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");

        int props = SDLProperties.SDL_CreateProperties();
        SDLProperties.SDL_SetStringProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
        SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
        SDLProperties.SDL_SetNumberProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1);
        SDLProperties.SDL_SetPointerProperty(props, SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
                Minecraft.getInstance().getWindow().handle());
        SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, callback, MemoryUtil.NULL, props);
        SDLProperties.SDL_DestroyProperties(props);

        return chosen.whenCompleteAsync((path, error) -> {
            if (previousHint == null) {
                SDLHints.SDL_ResetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS);
            } else {
                SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, previousHint);
            }
            callback.free();
            filters.free();
            MemoryUtil.memFree(name);
            MemoryUtil.memFree(pattern);
        }, Minecraft.getInstance());
    }
}
