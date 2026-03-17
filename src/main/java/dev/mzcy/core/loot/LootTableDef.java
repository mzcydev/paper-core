package dev.mzcy.core.loot;

import java.lang.annotation.*;

/**
 * Marks a method in a {@link dev.mzcy.core.annotation.Component} as
 * a {@link LootTable} factory — automatically discovered and registered
 * by {@link LootManager} during boot.
 *
 * <p>The method must:
 * <ul>
 *   <li>Be public</li>
 *   <li>Take no parameters</li>
 *   <li>Return a {@link LootTable}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class ModLootTables {
 *
 *     @LootTableDef
 *     public LootTable zombieDrops() {
 *         return LootTable.builder("zombie_drops")
 *             .pool(...)
 *             .build();
 *     }
 *
 *     @LootTableDef
 *     public LootTable bossDrops() {
 *         return LootTable.builder("boss_drops")
 *             .pool(...)
 *             .build();
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LootTableDef {
}