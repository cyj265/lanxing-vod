package com.fongmi.android.tv.player.engine;

import static com.fongmi.android.tv.player.engine.PlayerEngine.Type.EXO;

import androidx.media3.common.Player;

import com.fongmi.android.tv.player.exo.ExoPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;

/**
 * Resolves the playback engine.
 * <p>
 * The MPV backend has been removed from this build (no bundled native libraries),
 * so every request resolves to {@link ExoPlayerEngine}. The overloads taking a
 * {@link PlaySpec} or a {@link PlayerEngine.Type} are kept so existing call sites
 * do not have to change.
 */
public final class PlayerEngineFactory {

    public static PlayerEngine create(int decode, Player.Listener listener) {
        return new ExoPlayerEngine(decode, listener);
    }

    public static PlayerEngine create(int decode, PlaySpec spec, Player.Listener listener) {
        return create(decode, listener);
    }

    public static PlayerEngine create(int decode, PlayerEngine.Type type, Player.Listener listener) {
        return create(decode, listener);
    }

    public static boolean matches(PlayerEngine engine, PlaySpec spec) {
        return engine != null && engine.getType() == EXO;
    }
}
