package dev.mzcy.core.anvil;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Fluent builder for opening an anvil input GUI.
 *
 * <p>Obtained via {@link AnvilInputManager#builder(Player)}.
 *
 * <p>Example:
 * <pre>{@code
 * anvilInputManager.builder(player)
 *     .title("Enter home name")
 *     .placeholder("My Home")
 *     .item(ItemBuilder.of(Material.NAME_TAG).name("Enter name").build())
 *     .validator(text -> text.length() <= 16 && text.matches("[a-zA-Z0-9_]+"))
 *     .invalidMessage("<red>Invalid name! Use only letters, numbers and _")
 *     .request()
 *     .thenAccept(result -> {
 *         if (result.isSubmitted()) {
 *             homeService.create(player, result.getValue());
 *         }
 *     });
 * }</pre>
 */
public final class AnvilInputBuilder {

    private final AnvilInputManager manager;
    private final Player player;

    private String title = "Enter text";
    private String placeholder = "";
    private ItemStack leftItem = null;
    private Predicate<String> validator = null;
    private String invalidMessage = "<red>Invalid input.";
    private boolean preventClose = false;

    AnvilInputBuilder(
            @NotNull AnvilInputManager manager,
            @NotNull Player player
    ) {
        this.manager = manager;
        this.player = player;
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Sets the inventory title shown at the top of the anvil GUI.
     *
     * @param title the title text (plain, no MiniMessage)
     */
    @NotNull
    public AnvilInputBuilder title(@NotNull String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the placeholder text pre-filled in the rename field.
     * The player sees this as the initial name of the item.
     *
     * @param placeholder the pre-filled text
     */
    @NotNull
    public AnvilInputBuilder placeholder(@NotNull String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * Sets the item displayed in the left input slot of the anvil.
     * The item's display name serves as the editable text field.
     *
     * <p>If not set, a default paper item with the placeholder text is used.
     *
     * @param item the input item
     */
    @NotNull
    public AnvilInputBuilder item(@NotNull ItemStack item) {
        this.leftItem = item.clone();
        return this;
    }

    /**
     * Attaches a validator predicate. If the player's text fails validation,
     * the submission is rejected and {@link #invalidMessage} is shown.
     * The anvil stays open for another attempt.
     *
     * @param validator returns true if the text is valid
     */
    @NotNull
    public AnvilInputBuilder validator(@NotNull Predicate<String> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Sets the MiniMessage error message shown when validation fails.
     * Defaults to {@code "<red>Invalid input."}.
     *
     * @param miniMessage the error message
     */
    @NotNull
    public AnvilInputBuilder invalidMessage(@NotNull String miniMessage) {
        this.invalidMessage = miniMessage;
        return this;
    }

    /**
     * When true, the player cannot close the anvil without submitting valid text.
     * Re-opens the anvil if they try to close it.
     * Defaults to {@code false}.
     *
     * @param prevent true to prevent closing
     */
    @NotNull
    public AnvilInputBuilder preventClose(boolean prevent) {
        this.preventClose = prevent;
        return this;
    }

    // =========================================================================
    // Terminal operation
    // =========================================================================

    /**
     * Opens the anvil input GUI for the player and returns a
     * {@link CompletableFuture} that completes with the result.
     *
     * <p>The future always completes on the <b>main server thread</b>.
     *
     * @return the future result
     */
    @NotNull
    public CompletableFuture<AnvilInputResult> request() {
        return manager.open(
                player, title, placeholder,
                leftItem, validator,
                invalidMessage, preventClose
        );
    }
}