package dev.mzcy.core.scoreboard;

import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * A high-performance, per-player sidebar scoreboard.
 *
 * <p>Each player gets their own {@link Scoreboard} instance — this means
 * different players can see different values in the same sidebar at the same time.
 * No shared state between players.
 *
 * <p>Uses Paper's Adventure API directly for all text — no legacy color codes.
 * Lines are updated via score manipulation rather than objective recreation,
 * making updates flicker-free.
 *
 * <p>Inspired by <a href="https://github.com/catcoderr/ProtocolSidebar">ProtocolSidebar</a>
 * but implemented without packet manipulation — uses Paper's Scoreboard API
 * directly for maximum compatibility and zero NMS.
 *
 * <p>Lifecycle:
 * <pre>{@code
 * FastSidebar sidebar = SidebarBuilder.create("<gold>MyServer")
 *     .blank()
 *     .dynamic(() -> "<yellow>Players: " + Bukkit.getOnlinePlayers().size())
 *     .blank()
 *     .line("<gray>play.myserver.net")
 *     .build(plugin);
 *
 * // Show to a player
 * sidebar.show(player);
 *
 * // Start auto-update (every 20 ticks)
 * sidebar.startUpdating(20L);
 *
 * // Manual update
 * sidebar.update(player);
 * sidebar.updateAll();
 *
 * // Hide on quit
 * sidebar.hide(player);
 *
 * // Destroy on disable
 * sidebar.destroy();
 * }</pre>
 */
@Log
public final class FastSidebar {

    private static final String OBJECTIVE_NAME = "core_sidebar";

    private final Plugin plugin;

    /** The static title — may be overridden per-player via dynamic title line. */
    private Component title;

    /** All line definitions in top-to-bottom order (index 0 = top). */
    private final List<ScoreboardLine> lines;

    /**
     * Optional dynamic title supplier extracted from lines during construction.
     * Index -1 in {@link SidebarBuilder} is used as a sentinel for the title.
     */
    @Nullable
    private final ScoreboardLine dynamicTitle;

    /** Per-player scoreboards. Each player has their own isolated scoreboard. */
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();

    /** Per-player objectives. */
    private final Map<UUID, Objective> playerObjectives = new ConcurrentHashMap<>();

    /**
     * The last rendered line texts per player — used for dirty-checking
     * to avoid unnecessary score updates.
     */
    private final Map<UUID, List<Component>> lastRendered = new ConcurrentHashMap<>();

    /** Auto-update task ID, or -1 if not running. */
    private int updateTaskId = -1;

    FastSidebar(
            @NotNull Plugin plugin,
            @NotNull Component title,
            @NotNull List<ScoreboardLine> lines
    ) {
        this.plugin = plugin;
        this.title  = title;

        // Extract dynamic title (sentinel index -1)
        this.dynamicTitle = lines.stream()
                .filter(l -> l.getLineIndex() == -1)
                .findFirst()
                .orElse(null);

        // Content lines: everything with index >= 0, top-to-bottom
        this.lines = lines.stream()
                .filter(l -> l.getLineIndex() >= 0)
                .sorted(Comparator.comparingInt(ScoreboardLine::getLineIndex))
                .collect(java.util.stream.Collectors.toList());
    }

    // =========================================================================
    // Show / Hide
    // =========================================================================

    /**
     * Shows this sidebar to the given player.
     * Creates a new per-player scoreboard and renders all lines immediately.
     *
     * @param player the player to show the sidebar to
     */
    public void show(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();

        // Clean up any previous board for this player
        hide(player);

        final Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        final Objective objective = board.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                resolveTitle()
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        playerBoards.put(uuid, board);
        playerObjectives.put(uuid, objective);

        renderLines(player, objective, board);

        player.setScoreboard(board);
        log.fine(() -> "Showed sidebar to: " + player.getName());
    }

    /**
     * Hides this sidebar from the given player,
     * restoring the server's main scoreboard.
     *
     * @param player the player to hide the sidebar from
     */
    public void hide(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();

        playerBoards.remove(uuid);
        playerObjectives.remove(uuid);
        lastRendered.remove(uuid);

        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Shows this sidebar to all currently online players.
     */
    public void showAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    /**
     * Hides this sidebar from all players it is currently shown to.
     */
    public void hideAll() {
        new ArrayList<>(playerBoards.keySet()).forEach(uuid -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) hide(player);
        });
    }

    // =========================================================================
    // Updating
    // =========================================================================

    /**
     * Updates the sidebar for a specific player.
     * Only re-sends lines that have changed since the last render (dirty check).
     *
     * @param player the player to update
     */
    public void update(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        final Objective objective = playerObjectives.get(uuid);
        final Scoreboard board    = playerBoards.get(uuid);

        if (objective == null || board == null) return;
        if (!player.isOnline()) {
            hide(player);
            return;
        }

        // Update title if dynamic
        if (dynamicTitle != null) {
            objective.displayName(dynamicTitle.resolve());
        }

        renderLines(player, objective, board);
    }

    /**
     * Updates the sidebar for all players it is currently shown to.
     */
    public void updateAll() {
        new ArrayList<>(playerBoards.keySet()).forEach(uuid -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                update(player);
            } else if (player == null) {
                // Player went offline — clean up
                playerBoards.remove(uuid);
                playerObjectives.remove(uuid);
                lastRendered.remove(uuid);
            }
        });
    }

    /**
     * Starts a repeating auto-update task on the main thread.
     * Calling this again while already running replaces the existing task.
     *
     * @param periodTicks ticks between updates (20 = 1 second)
     */
    public void startUpdating(long periodTicks) {
        stopUpdating();
        updateTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::updateAll, periodTicks, periodTicks)
                .getTaskId();
        log.fine(() -> "Sidebar auto-update started (period=" + periodTicks + "t)");
    }

    /**
     * Stops the auto-update task if running.
     */
    public void stopUpdating() {
        if (updateTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    // =========================================================================
    // Dynamic line mutation
    // =========================================================================

    /**
     * Updates the title and immediately pushes it to all active player boards.
     *
     * @param miniMessage the new title in MiniMessage format
     */
    public void setTitle(@NotNull String miniMessage) {
        this.title = net.kyori.adventure.text.minimessage.MiniMessage
                .miniMessage().deserialize(miniMessage);
        playerObjectives.forEach((uuid, obj) -> obj.displayName(this.title));
    }

    /**
     * Replaces the content of a specific line by index and immediately
     * pushes the change to all active player boards.
     *
     * @param lineIndex the 0-based line index (top = 0)
     * @param miniMessage the new MiniMessage content
     */
    public void setLine(int lineIndex, @NotNull String miniMessage) {
        if (lineIndex < 0 || lineIndex >= lines.size()) return;
        lines.set(lineIndex, ScoreboardLine.staticLine(lineIndex, miniMessage));
        updateAll();
    }

    /**
     * Returns the number of lines in this sidebar.
     */
    public int lineCount() {
        return lines.size();
    }

    /**
     * Returns true if the sidebar is currently shown to the given player.
     */
    public boolean isShownTo(@NotNull Player player) {
        return playerBoards.containsKey(player.getUniqueId());
    }

    /**
     * Returns the number of players this sidebar is currently shown to.
     */
    public int viewerCount() {
        return playerBoards.size();
    }

    // =========================================================================
    // Destruction
    // =========================================================================

    /**
     * Stops updating, hides from all players, and releases all resources.
     * Call this on plugin disable.
     */
    public void destroy() {
        stopUpdating();
        hideAll();
        playerBoards.clear();
        playerObjectives.clear();
        lastRendered.clear();
        log.fine("Sidebar destroyed.");
    }

    // =========================================================================
    // Internal rendering
    // =========================================================================

    /**
     * Renders all lines to the given player's objective.
     *
     * <p>Uses dirty checking — only lines that have changed since the last
     * render are updated, minimising packet overhead.
     *
     * <p>Scores are assigned in reverse order so that line 0 (top) gets
     * the highest score and appears at the top of the sidebar.
     */
    private void renderLines(
            @NotNull Player player,
            @NotNull Objective objective,
            @NotNull Scoreboard board
    ) {
        final UUID uuid = player.getUniqueId();
        final List<Component> previous = lastRendered.getOrDefault(uuid,
                Collections.emptyList());
        final List<Component> current  = new ArrayList<>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            final Component resolved = lines.get(i).resolve();
            current.add(resolved);

            // Dirty check — skip if unchanged
            if (i < previous.size() && previous.get(i).equals(resolved)) continue;

            // Score: lines.size() - i ensures top line has highest score
            final int score = lines.size() - i;
            updateScore(board, objective, resolved, score);
        }

        // Remove stale scores if line count shrank
        if (previous.size() > current.size()) {
            cleanStaleScores(board, previous, current);
        }

        lastRendered.put(uuid, current);
    }

    private void updateScore(
            @NotNull Scoreboard board,
            @NotNull Objective objective,
            @NotNull Component text,
            int score
    ) {
        try {
            // Paper 1.21+ supports component-based team display names
            // We use teams to set the display text per score entry
            final String entryKey = "line_" + score;

            Team team = board.getTeam(entryKey);
            if (team == null) {
                team = board.registerNewTeam(entryKey);
                team.addEntry(entryKey);
            }

            // Set the prefix (displayed as the line content)
            team.prefix(text);
            team.suffix(Component.empty());

            // Set the score
            objective.getScore(entryKey).setScore(score);

        } catch (Exception ex) {
            log.log(Level.FINE, "Failed to update score for entry at " + score, ex);
        }
    }

    private void cleanStaleScores(
            @NotNull Scoreboard board,
            @NotNull List<Component> previous,
            @NotNull List<Component> current
    ) {
        for (int i = current.size(); i < previous.size(); i++) {
            final int score  = lines.size() - i;
            final String key = "line_" + score;
            board.resetScores(key);
            final Team team = board.getTeam(key);
            if (team != null) team.unregister();
        }
    }

    @NotNull
    private Component resolveTitle() {
        return dynamicTitle != null ? dynamicTitle.resolve() : title;
    }
}