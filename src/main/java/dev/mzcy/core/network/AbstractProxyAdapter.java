package dev.mzcy.core.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.logging.Level;

/**
 * Shared base for {@link BungeeCordAdapter} and {@link VelocityAdapter}.
 *
 * <p>Provides a shared {@link #write} helper for building and sending
 * plugin messages, eliminating the boilerplate try-with-resources
 * pattern in every action method.
 */
@Log
@RequiredArgsConstructor
public abstract class AbstractProxyAdapter implements ProxyAdapter {

    @NotNull
    protected final Plugin plugin;

    /**
     * Builds a plugin message using the given writer and sends it
     * on the adapter's main channel through the given player.
     *
     * @param player the player whose connection to use
     * @param writer a consumer that writes to the {@link DataOutputStream}
     */
    protected void write(
            @NotNull Player player,
            @NotNull DataWriter writer
    ) {
        write(player, getMainChannel(), writer);
    }

    /**
     * Builds a plugin message on a specific channel.
     */
    protected void write(
            @NotNull Player player,
            @NotNull String channel,
            @NotNull DataWriter writer
    ) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final DataOutputStream dos = new DataOutputStream(baos)) {
            writer.write(dos);
            player.sendPluginMessage(plugin, channel, baos.toByteArray());
        } catch (IOException ex) {
            log.log(Level.WARNING,
                    "[" + getProxyType() + "] Failed to send plugin message", ex);
        }
    }

    /**
     * Functional interface for writing to a {@link DataOutputStream}.
     */
    @FunctionalInterface
    protected interface DataWriter {
        void write(@NotNull DataOutputStream out) throws IOException;
    }
}