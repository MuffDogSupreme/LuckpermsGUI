<p align="center">
  <img src="modrinth/icon.svg" width="96" height="96" alt="LuckpermsGUI icon" />
</p>

<h1 align="center">LuckpermsGUI</h1>

<p align="center">
  A chest-GUI administrative front end for LuckPerms — manage users, groups, tracks, contexts,<br/>
  and the permission audit log entirely through in-game menus, with no need to memorize<br/>
  <code>/lp</code> command syntax.
</p>

---

## Overview

LuckpermsGUI is a standalone Paper plugin that puts LuckPerms permission, group, and track
management inside in-game chest inventories. It's built for server admins and staff who'd rather
click through a menu than remember exact `/lp` subcommand syntax — while still doing everything
through LuckPerms' own public API, so nothing it does is invisible to LuckPerms itself.

Every change you make — toggling a permission, reordering a promotion ladder, setting a group's
prefix — is written back through the same load-then-save path LuckPerms uses internally, and is
logged to LuckPerms' own action log. Run `/lp log` afterward and changes made through this GUI
look identical to changes made through LuckPerms' own commands.

**This plugin has no dependencies besides LuckPerms itself**, and shares no code or state with any
other plugin.

## Features

- **Launcher** — one command, `/lpgui`, opens a hub with six tiles: Users, Groups, Tracks,
  Contexts, Action Log, System Tools.
- **Users** — browse or search every player LuckPerms knows about (online or not); manage groups,
  primary group, and track promotion/demotion from a dashboard; edit permission nodes with an
  optional expiry and context restriction; manage custom meta key/value pairs.
- **Groups** — create, delete, and browse every group on the server; edit weight, prefix, suffix,
  and display name; manage parent groups; the same permission-node editor as Users.
- **Tracks** — build promotion ladders visually; reorder by clicking the group you want to move,
  then clicking where it should go.
- **Contexts** — see the server's static context and any online player's currently active context
  set (read-only diagnostic view).
- **Action Log** — browse the LuckPerms audit trail without leaving the GUI; filter by actor or
  target name; force-refresh when you need the very latest entry.
- **System Tools** — sync permissions across your network in one click; jump straight into
  LuckPerms' own web editor; purge locally cached permission data after a bulk change.

## Requirements

| | |
|---|---|
| **Server software** | Paper, or a Paper-based fork that maintains full Paper API compatibility (e.g. Purpur, Pufferfish). **Not** tested against Folia's regionized threading model — don't assume Folia compatibility. **Not** compatible with plain Spigot/Bukkit, since it uses Paper-exclusive APIs (Brigadier command registration, Paper's chat event). |
| **Minecraft / Paper version** | Paper build **1.20.6 or newer** — this is a hard floor, not a suggestion: the plugin uses `LifecycleEvents.COMMANDS`/`BasicCommand`, which doesn't exist on earlier Paper builds. Compiled and verified against 1.20.6 specifically; newer versions are expected to work (the plugin declares `api-version: '1.20'`) but haven't been individually verified — check your own target version before relying on it in production. |
| **Java** | 21 |
| **Dependencies** | LuckPerms (required — the plugin will not enable without it, and says so clearly in the console) |

## Installation

1. Drop the plugin jar into your server's `plugins/` folder.
2. Make sure LuckPerms is in the same folder — LuckpermsGUI won't start without it.
3. Restart the server (a plugin reload isn't enough for a fresh install).
4. Grant `luckpermsgui.admin` to whichever staff should have access — it defaults to operators only.
5. Run `/lpgui` in-game to open the Launcher.

## Commands

Base command `/lpgui`, aliases `/lpg` and `/luckpermsgui`. Every subcommand requires
`luckpermsgui.admin`.

| Command | Effect |
|---|---|
| `/lpgui` | Opens the Launcher GUI |
| `/lpgui menu` | Same as bare — explicit alias |
| `/lpgui open [player]` | Jumps straight to a player's dashboard |
| `/lpgui setgroup <player> <group>` | Sets a player's primary group (grants membership if needed) |
| `/lpgui addperm <player> <permission>` | Adds a permission node |
| `/lpgui removeperm <player> <permission>` | Removes a permission node |
| `/lpgui help` | Shows command usage as chat text |

## Chat Input Syntax

Chest inventories can't take multi-field forms, so anywhere the GUI needs more than one value, it
accepts a single compact line typed in chat. Type `cancel`, or click the bundled Cancel link, to
back out — every prompt also times out after 30 seconds on its own.

| Context | Syntax | Example |
|---|---|---|
| Add permission | `key [true\|false] [duration] [context=value]` | `essentials.fly false 7d world=nether` |
| Add group / parent | `group-name [duration]` | `vip 30d` |
| Add meta key | `key value` | `guild Frostbite Raiders` |
| Durations | `30s · 10m · 2h · 7d · 4w · 6mo · 1y` | one number, one unit |

Only the first value is ever required — everything else is optional.

## How Permissions Work

Every permission node is one of exactly three states — this is just how LuckPerms itself stores
permissions, nothing invented:

- **True** — a stored node with `value = true`
- **Negated** — a stored node with `value = false`
- **Unset** — no node at all

Left-click a permission to flip True/Negated; right-click to unset it. The same rule applies
everywhere in the GUI: right-click generally means remove, left-click generally means toggle or
select.

## Limitations, Honestly

- Runs on Paper 1.20.6+ only (see Requirements above) — not a limitation so much as a hard floor.
- One context restriction per permission node, not an arbitrary set.
- Prefix/suffix priority is fixed at 100 when set through the GUI — setting a new one replaces any
  existing one rather than stacking multiple priorities.
- Group deletion doesn't hard-block on "this is someone's primary group" or "this group is on a
  track" — the dashboard shows track membership before you delete, and deletion always needs two
  clicks, but there's no automatic prevention.
- Meta values added via the simple chat syntax can't carry an expiry, since a value can contain
  almost anything, including something that looks like a duration.
- The Action Log has no server-side search — LuckPerms' API returns the whole log at once, so
  filtering happens client-side against a cached copy that refreshes at most every 45 seconds.

## License

LuckpermsGUI is proprietary software distributed under the MuffDev EULA — see
[`LICENSE.md`](LICENSE.md). In short: you may run it on any server you operate, including
commercial/monetized ones, but you may not redistribute, resell, or re-upload it elsewhere without
written permission from MuffDev.

## Links

- **Wiki:** _add your wiki URL here_
- **Download:** _add your Modrinth/storefront URL here once published_
- **Support:** _add your Discord/support channel URL here_

---

<p align="center"><sub>Not affiliated with the LuckPerms project.</sub></p>
