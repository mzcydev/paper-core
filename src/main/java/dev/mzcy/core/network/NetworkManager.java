package dev.mzcy.core.network;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.CoreException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all plugin messaging channel operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Proxy auto-detection and {@link ProxyAdapter} wiring</li>
 *   <li>Registering outgoing and incoming plugin message channels</li>
 *   <li>Discovering {@link MessageHandler} methods via {@link ScanResult}</li>
 *   <li>Routing received messages to typed handlers</li>
 *   <li>Delegating proxy actions to the active {@link ProxyAdapter}</li>
 * </ul>
 *
 * <p>The public API is identical regardless of which proxy is active —
 * BungeeCord, Velocity, or none. Swap the adapter at runtime via
 * {@link #setProxyType(ProxyType)}.
 */
@Log
public final class NetworkManager {

    private final Plugin    plugin;
    private final Container container;

    /** The active proxy adapter — never null (falls back to {@link NoOpProxyAdapter}). */
    @Getter
    private ProxyAdapter proxyAdapter;

    /** Registered outgoing channels. */
    private final Set<String> registeredOutgoing = ConcurrentHashMap.newKeySet();

    /** Registered incoming channels. */
    private final Set<String> registeredIncoming = ConcurrentHashMap.newKeySet();

    /**
     * Message handlers grouped by fully qualified message type name.
     * Key = message class name, Value = list of registered handlers.
     */
    private final Map<String, List<HandlerRegistration>> handlers
            = new ConcurrentHashMap<>();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates a {@link NetworkManager} with auto-detected proxy type.
     *
     * @param plugin    the owning plugin
     * @param container the DI container for resolving handler instances
     */
    public NetworkManager(
            @NotNull Plugin plugin,
            @NotNull Container container
    ) {
        this(plugin, container, detectProxyType(plugin));
    }

    /**
     * Creates a {@link NetworkManager} with an explicit proxy type.
     *
     * @param plugin     the owning plugin
     * @param container  the DI container
     * @param proxyType  the proxy type to use
     */
    public NetworkManager(
            @NotNull Plugin plugin,
            @NotNull Container container,
            @NotNull ProxyType proxyType
    ) {
        this.plugin    = plugin;
        this.container = container;
        applyProxyType(proxyType);
    }

    // =========================================================================
    // Discovery
    // =========================================================================

    /**
     * Discovers all {@link NetworkMessage}-annotated classes and
     * {@link MessageHandler}-annotated methods from the given scan result,
     * and registers them with this manager.
     *
     * @param result the scan result
     */
    public void discoverAndRegister(@NotNull ScanResult result) {
        int messageTypes = 0;
        int handlerCount = 0;

        for (final Class<?> cls : result.getComponents()) {

            // Register @NetworkMessage types
            final NetworkMessage msgAnnotation = cls.getAnnotation(NetworkMessage.class);
            if (msgAnnotation != null) {
                registerOutgoing(msgAnnotation.channel());
                registerIncoming(msgAnnotation.channel());
                messageTypes++;
                log.fine(() -> "Registered message type: "
                        + cls.getSimpleName() + " → " + msgAnnotation.channel());
            }

            // Register @MessageHandler methods
            for (final java.lang.reflect.Method method : cls.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(MessageHandler.class)) continue;

                if (method.getParameterCount() != 1) {
                    log.warning(() -> "@MessageHandler must have exactly one parameter: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                if (!method.getReturnType().equals(void.class)) {
                    log.warning(() -> "@MessageHandler must return void: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                final Class<?> messageType = method.getParameterTypes()[0];

                try {
                    final Object instance = container.resolve(cls);
                    final HandlerRegistration reg =
                            new HandlerRegistration(instance, method, messageType);
                    handlers.computeIfAbsent(messageType.getName(), k -> new ArrayList<>())
                            .add(reg);
                    handlerCount++;
                    log.fine(() -> "Registered @MessageHandler: "
                            + cls.getSimpleName() + "#" + method.getName()
                            + "(" + messageType.getSimpleName() + ")");
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to register @MessageHandler: "
                                    + cls.getName() + "#" + method.getName(), ex);
                }
            }
        }

        log.info("NetworkManager: " + messageTypes + " message type(s), "
                + handlerCount + " handler(s) registered.");
    }

    // =========================================================================
    // Channel registration
    // =========================================================================

    /**
     * Registers an outgoing plugin messaging channel.
     * No-op if already registered.
     *
     * @param channel the channel name
     */
    public void registerOutgoing(@NotNull String channel) {
        if (registeredOutgoing.add(channel)) {
            plugin.getServer().getMessenger()
                    .registerOutgoingPluginChannel(plugin, channel);
            log.fine(() -> "Registered outgoing channel: " + channel);
        }
    }

    /**
     * Registers an incoming plugin messaging channel and attaches the
     * message routing listener.
     * No-op if already registered.
     *
     * @param channel the channel name
     */
    public void registerIncoming(@NotNull String channel) {
        if (registeredIncoming.add(channel)) {
            plugin.getServer().getMessenger()
                    .registerIncomingPluginChannel(plugin, channel, this::onPluginMessage);
            log.fine(() -> "Registered incoming channel: " + channel);
        }
    }

    /**
     * Registers a raw {@link org.bukkit.plugin.messaging.PluginMessageListener}
     * for a channel. Use this for channels where you need direct access to the
     * raw bytes (e.g., parsing BungeeCord responses manually).
     *
     * @param channel  the channel to listen on
     * @param listener the raw listener
     */
    public void registerRawListener(
            @NotNull String channel,
            @NotNull org.bukkit.plugin.messaging.PluginMessageListener listener
    ) {
        registerIncoming(channel);
        plugin.getServer().getMessenger()
                .registerIncomingPluginChannel(plugin, channel, listener);
    }

    // =========================================================================
    // Sending typed messages
    // =========================================================================

    /**
     * Sends a {@link NetworkMessage}-annotated message through a player's
     * connection to the proxy.
     *
     * @param player  the carrier player (must be online)
     * @param message the message to send
     * @throws CoreException if the message class lacks {@link NetworkMessage}
     */
    public void send(@NotNull Player player, @NotNull Object message) {
        final NetworkMessage annotation =
                message.getClass().getAnnotation(NetworkMessage.class);

        if (annotation == null) {
            throw new CoreException(
                    message.getClass().getSimpleName()
                            + " must be annotated with @NetworkMessage");
        }

        if (!player.isOnline()) {
            log.fine(() -> "Skipping send — carrier player offline: " + player.getName());
            return;
        }

        final byte[] data = NetworkSerializer.serialize(message);
        player.sendPluginMessage(plugin, annotation.channel(), data);

        log.fine(() -> "Sent " + message.getClass().getSimpleName()
                + " via: " + player.getName()
                + " on: " + annotation.channel());
    }

    /**
     * Sends raw bytes on a channel through a player's connection.
     *
     * @param player  the carrier player
     * @param channel the target channel
     * @param data    the raw bytes to send
     */
    public void sendRaw(
            @NotNull Player player,
            @NotNull String channel,
            byte[] data
    ) {
        registerOutgoing(channel);
        player.sendPluginMessage(plugin, channel, data);
    }

    // =========================================================================
    // Proxy action delegation
    // =========================================================================

    public void connectToServer(@NotNull Player player, @NotNull String server) {
        proxyAdapter.connectToServer(player, server);
    }

    public void connectOtherToServer(
            @NotNull Player carrier,
            @NotNull String targetPlayer,
            @NotNull String server
    ) {
        proxyAdapter.connectOtherToServer(carrier, targetPlayer, server);
    }

    public void sendToPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String message
    ) {
        proxyAdapter.sendToPlayer(carrier, targetName, message);
    }

    public void broadcastNetwork(@NotNull Player carrier, @NotNull String message) {
        proxyAdapter.broadcastNetwork(carrier, message);
    }

    public void kickPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String reason
    ) {
        proxyAdapter.kickPlayer(carrier, targetName, reason);
    }

    public void forward(
            @NotNull Player carrier,
            @NotNull String server,
            @NotNull String channel,
            byte[] data
    ) {
        proxyAdapter.forward(carrier, server, channel, data);
    }

    public void requestServerName(@NotNull Player player) {
        proxyAdapter.requestServerName(player);
    }

    public void requestPlayerCount(@NotNull Player carrier, @NotNull String server) {
        proxyAdapter.requestPlayerCount(carrier, server);
    }

//    public void requestServerList(@NotNull Player carrier) {
//        proxyAdapter.requestServerList(carrier);
//    }

//    public void requestPlayerList(@NotNull Player carrier, @NotNull String server) {
//        proxyAdapter.requestPlayerList(carrier, server);
//    }

    // =========================================================================
    // Proxy type management
    // =========================================================================

    /**
     * Returns the currently active {@link ProxyType}.
     *
     * @return the proxy type
     */
    @NotNull
    public ProxyType getProxyType() {
        return proxyAdapter.getProxyType();
    }

    /**
     * Switches the active proxy adapter at runtime.
     * Useful after a {@code /core reload} where the proxy type may have changed.
     *
     * @param type the new proxy type
     */
    public void setProxyType(@NotNull ProxyType type) {
        applyProxyType(type);
        log.info("Switched proxy adapter to: " + type);
    }

    private void applyProxyType(@NotNull ProxyType type) {
        this.proxyAdapter = switch (type) {
            case BUNGEECORD -> new BungeeCordAdapter(plugin);
            case VELOCITY   -> new VelocityAdapter(plugin);
            case NONE       -> new NoOpProxyAdapter(plugin);
        };

        if (type != ProxyType.NONE) {
            registerOutgoing(proxyAdapter.getMainChannel());
            registerIncoming(proxyAdapter.getMainChannel());

            // Velocity also needs the player_info channel
            if (type == ProxyType.VELOCITY) {
                registerOutgoing(NetworkChannel.VELOCITY_PLAYER_INFO);
                registerIncoming(NetworkChannel.VELOCITY_PLAYER_INFO);
            }
        }
    }

    // =========================================================================
    // Message routing
    // =========================================================================

    private void onPluginMessage(
            @NotNull String channel,
            @NotNull Player player,
            byte[] data
    ) {
        // Skip channels with no typed handlers — handled via raw listeners
        final boolean isProxyChannel =
                channel.equals(NetworkChannel.BUNGEECORD)
                        || channel.equals(NetworkChannel.BUNGEECORD_MODERN)
                        || channel.equals(NetworkChannel.VELOCITY_PLAYER_INFO)
                        || channel.equals(NetworkChannel.VELOCITY_BRIDGE);

        if (isProxyChannel && handlers.isEmpty()) return;

        // Attempt to deserialize as a typed message
        final Object message;
        try {
            message = NetworkSerializer.deserialize(data,
                    plugin.getClass().getClassLoader());
        } catch (CoreException ex) {
            // Not a typed message — expected for raw proxy channels
            log.finest(() -> "Non-typed message on channel: " + channel);
            return;
        }

        final String typeName = message.getClass().getName();
        final List<HandlerRegistration> registrations = handlers.get(typeName);

        if (registrations == null || registrations.isEmpty()) {
            log.fine(() -> "No handlers for message type: "
                    + message.getClass().getSimpleName()
                    + " on channel: " + channel);
            return;
        }

        // Determine thread delivery
        final NetworkMessage annotation =
                message.getClass().getAnnotation(NetworkMessage.class);
        final boolean mainThread = annotation == null || annotation.mainThread();

        @SuppressWarnings({"unchecked", "rawtypes"})
        final MessagePayload<Object> payload =
                new MessagePayload<>((Object) message, channel, player);

        if (mainThread) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> dispatch(registrations, payload));
        } else {
            dispatch(registrations, payload);
        }
    }

    private void dispatch(
            @NotNull List<HandlerRegistration> registrations,
            @NotNull MessagePayload<?> payload
    ) {
        for (final HandlerRegistration reg : registrations) {
            try {
                reg.invoke(payload);
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Exception in @MessageHandler "
                                + reg.getInstance().getClass().getSimpleName()
                                + "#" + reg.getMethod().getName()
                                + " for type: "
                                + payload.getMessage().getClass().getSimpleName(), ex);
            }
        }
    }

    // =========================================================================
    // Auto-detection
    // =========================================================================

    /**
     * Auto-detects the proxy type from server configuration files.
     *
     * <p>Detection order:
     * <ol>
     *   <li>Check {@code spigot.yml → settings.bungeecord: true}
     *       → {@link ProxyType#BUNGEECORD}</li>
     *   <li>Check {@code config/paper-global.yml} or spigot config for
     *       Velocity forwarding secret → {@link ProxyType#VELOCITY}</li>
     *   <li>Default → {@link ProxyType#NONE}</li>
     * </ol>
     *
     * @param plugin the owning plugin
     * @return the detected proxy type
     */
    @SuppressWarnings("removal")
    @NotNull
    private static ProxyType detectProxyType(@NotNull Plugin plugin) {
        // Check spigot.yml for BungeeCord mode
        try {
            final boolean bungeeCord = plugin.getServer()
                    .spigot().getConfig()
                    .getBoolean("settings.bungeecord", false);

            if (bungeeCord) {
                log.info("Auto-detected proxy: BUNGEECORD (spigot.yml)");
                return ProxyType.BUNGEECORD;
            }
        } catch (Exception ignored) {}

        // Check paper.yml / paper-global.yml for Velocity forwarding
        try {
            final java.io.File paperGlobal =
                    new java.io.File("config/paper-global.yml");
            if (paperGlobal.exists()) {
                final org.bukkit.configuration.file.YamlConfiguration cfg =
                        org.bukkit.configuration.file.YamlConfiguration
                                .loadConfiguration(paperGlobal);
                final boolean velocityEnabled = cfg.getBoolean(
                        "proxies.velocity.enabled", false);
                if (velocityEnabled) {
                    log.info("Auto-detected proxy: VELOCITY (paper-global.yml)");
                    return ProxyType.VELOCITY;
                }
            }
        } catch (Exception ignored) {}

        log.info("No proxy detected — using NONE (single-server mode).");
        return ProxyType.NONE;
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Unregisters all plugin messaging channels and clears state.
     * Called on plugin disable.
     */
    public void shutdown() {
        plugin.getServer().getMessenger()
                .unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger()
                .unregisterOutgoingPluginChannel(plugin);
        registeredIncoming.clear();
        registeredOutgoing.clear();
        handlers.clear();
        log.fine("NetworkManager shut down.");
    }
}