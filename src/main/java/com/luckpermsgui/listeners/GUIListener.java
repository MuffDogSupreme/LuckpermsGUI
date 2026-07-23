package com.luckpermsgui.listeners;

import com.luckpermsgui.menu.AbstractMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Routes every click inside one of our menus to {@link AbstractMenu#handleClick}.
 *
 * <p>Identification is purely by the top inventory's holder - never by title - via
 * {@code event.getInventory().getHolder() instanceof AbstractMenu}. Every such click is cancelled
 * unconditionally first, which also blocks shift-clicks staged from the player's own inventory:
 * those still fire against the same top inventory/holder, so the guard catches them too.
 */
public final class GUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AbstractMenu menu)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(menu.getInventory())) {
            return;
        }

        menu.handleClick(event.getSlot(), event.getClick(), player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AbstractMenu)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
