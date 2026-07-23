package com.luckpermsgui.menu;

import com.luckpermsgui.LuckpermsGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * A 54-slot menu with a standardized paginated content grid (slots 0-44) and page controls:
 * Previous at 48, a page indicator at 49, Next at 50. Slot 53 remains the universal back/exit
 * slot inherited from {@link AbstractMenu}. Slots 45-47 and 51-52 are left for the concrete menu
 * to use (typically filler, but available for module-specific action buttons).
 *
 * <p>Not every subclass needs to fill the whole 0-44 range with paginated items - some (like the
 * user editor) reserve part of that range for fixed, non-paginated content (a head, a row of
 * group tags) and only paginate a subset of it. {@link #onContentClick} still receives clicks
 * from the entire 0-44 range either way; subclasses that don't recognise a given slot simply
 * ignore it.
 */
public abstract class PaginatedMenu extends AbstractMenu {

    public static final int SIZE = 54;
    public static final int CONTENT_START = 0;
    public static final int CONTENT_END = 44;
    public static final int PREV_PAGE_SLOT = 48;
    public static final int PAGE_INDICATOR_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 50;

    private int page = 0;
    private int totalPages = 1;

    protected PaginatedMenu(LuckpermsGUI plugin, MenuSession session, Player viewer, Component title) {
        super(plugin, session, viewer, SIZE, title);
    }

    protected final int getPage() {
        return page;
    }

    protected final int getTotalPages() {
        return totalPages;
    }

    /** Call from {@link #render()} once the current item count for this page is known; clamps the current page. */
    protected final void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
        if (page >= this.totalPages) {
            page = this.totalPages - 1;
        }
        if (page < 0) {
            page = 0;
        }
    }

    /** Paints the page-control row and the back/exit button. Call from {@link #render()}, after painting content. */
    protected final void renderPageControls() {
        getInventory().setItem(PREV_PAGE_SLOT, page > 0 ? MenuItems.prevPageButton() : MenuItems.filler());
        getInventory().setItem(PAGE_INDICATOR_SLOT, MenuItems.pageIndicator(page + 1, totalPages));
        getInventory().setItem(NEXT_PAGE_SLOT, page < totalPages - 1 ? MenuItems.nextPageButton() : MenuItems.filler());
        renderBackOrExitButton();
    }

    @Override
    protected final void onClick(int slot, ClickType clickType, Player player) {
        if (slot == PREV_PAGE_SLOT) {
            if (page > 0) {
                page--;
                render();
            }
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            if (page < totalPages - 1) {
                page++;
                render();
            }
            return;
        }
        if (slot == PAGE_INDICATOR_SLOT) {
            return;
        }
        if (slot >= CONTENT_START && slot <= CONTENT_END) {
            onContentClick(slot, clickType, player);
            return;
        }
        onToolbarClick(slot, clickType, player);
    }

    /** Handles a click on one of the content slots (0-44). Pure navigation clicks never reach here. */
    protected abstract void onContentClick(int slot, ClickType clickType, Player player);

    /**
     * Handles a click on one of the "free" slots in the bottom row (45-47, 51-52) that aren't
     * claimed by pagination controls or the back/exit button. Default no-op; override to add
     * module-specific action buttons there (filters, force-refresh, and the like).
     */
    protected void onToolbarClick(int slot, ClickType clickType, Player player) {
    }
}
