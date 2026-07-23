# LuckpermsGUI Operator Implementation Guide

Follow this guide to install LuckpermsGUI and get comfortable with the workflows you'll use most as
staff.

---

## Quick-Start Walkthrough (Under 2 Minutes)

1. **Download and Deploy:** Download the `LuckpermsGUI.jar` file from your dashboard and drop it
   directly into your server's `/plugins/` directory, alongside your existing LuckPerms jar.
2. **Boot the Server:** Start your PaperMC server instance (build 1.20.6 or newer). LuckpermsGUI
   checks for LuckPerms on startup and will refuse to enable — with a clear console message — if
   it can't find it.
3. **Configure Permissions:** Assign `luckpermsgui.admin` to your Staff group via LuckPerms. There
   is no separate "player" tier for this plugin — it's an admin tool end to end.
4. **Open the Launcher:** In-game, run `/lpgui`. That's the hub for every module — Users, Groups,
   Tracks, Contexts, Action Log, and System Tools.
5. **Manage Properties:** Everything from here is point-and-click. Toggling a permission, changing
   a group's prefix, or reordering a promotion ladder all take effect immediately — no reboot, no
   `/lp reload`, needed.

---

## Walkthrough: Build a Rank Ladder From Scratch

1. `/lpgui` → **Groups** → **Create Group**, typing each rank's name in chat (e.g. `member`, `vip`,
   `mvp`).
2. Open each group's **Dashboard** to set its weight (higher weight takes precedence), prefix, and
   suffix.
3. **Launcher** → **Tracks** → **Create Track**, naming it (e.g. `ranks`).
4. In the Sequence Editor, use **Add Group** to append `member`, `vip`, `mvp` in that order.
5. From any player's **Dashboard**, use **Promote on Track** and type `ranks` to move them up one
   rung at a time. **Demote on Track** works the same way in reverse.

To reorder an existing ladder later: open the track, left-click the group you want to move, then
left-click the group whose position you want it to take — everything shifts to make room. Click
the same tile again first if you change your mind before the second click.

---

## Critical Technical Notes & Operational Gotchas

> ⚠️ **User Directory Rendering Threshold**
> The Users Directory shows a real player-skull icon for every row on servers with fewer than 200
> total known players. At 200 or above, only players whose primary group isn't literally `default`
> get a skull — everyone else renders as a plain name tag, to avoid hundreds of skin-texture
> resolutions on one screen. Names are always shown either way; use the Search button to jump
> straight to any specific player regardless of what's visible on the current page.

> 🛡️ **Destructive Actions Require Confirmation, Not Assumption**
> Deleting a group or a track needs two clicks within ten seconds — the first click only arms a
> warning state. There is **no automatic check** for whether a group being deleted is still
> someone's primary group or a member of a track; the Group Dashboard shows track membership
> before you delete so you can check yourself, but nothing blocks the deletion outright.

> 💾 **Meta Values Can't Carry an Expiry Through the Simple Syntax**
> The `key value` syntax for meta keys takes everything after the first space as the value
> verbatim — including something that happens to look like a duration token. The plugin
> deliberately doesn't try to guess whether a trailing `5d` is part of your value or an expiry, so
> temporary meta isn't exposed through this input path.

> 📖 **The Action Log Is Cached, Not Live**
> LuckPerms' own API returns its entire audit log in one call with no server-side filtering, so
> LuckpermsGUI caches it for up to 45 seconds and filters by actor/target name locally. If you need
> to see a change that just happened, use the **Force Refresh** button in the Action Log screen
> rather than waiting.
