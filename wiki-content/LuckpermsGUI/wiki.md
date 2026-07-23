# LuckpermsGUI Technical Reference Manual

Welcome to the technical specification manual for LuckpermsGUI. This page details the exact
commands, permission nodes, GUI modules, and chat-input syntax engineered into the plugin.

---

## Commands & Permissions

LuckpermsGUI uses a single permission node for everything — there's no separate `.use` tier, since
the entire plugin is an administrative tool. Aliases for the base command are `/lpg` and
`/luckpermsgui`.

| Command | Description | Permission Node | Default Access |
| :--- | :--- | :--- | :--- |
| `/lpgui` | Opens the Launcher GUI — the hub linking to every module. | `luckpermsgui.admin` | Operator Only |
| `/lpgui menu` | Identical to the bare command; an explicit alias. | `luckpermsgui.admin` | Operator Only |
| `/lpgui open [player]` | Jumps straight to a player's Dashboard, bypassing the Launcher. | `luckpermsgui.admin` | Operator Only |
| `/lpgui setgroup <player> <group>` | Sets a player's primary group (grants membership automatically if they don't already have it). | `luckpermsgui.admin` | Operator Only |
| `/lpgui addperm <player> <permission>` | Adds a permission node to a player with value `true`. | `luckpermsgui.admin` | Operator Only |
| `/lpgui removeperm <player> <permission>` | Removes a permission node from a player. | `luckpermsgui.admin` | Operator Only |
| `/lpgui help` | Prints command usage as plain chat text. | `luckpermsgui.admin` | Operator Only |

**Hard dependency:** LuckPerms must be installed and started successfully before LuckpermsGUI will
enable. If LuckPerms isn't found, LuckpermsGUI logs a clear console error and disables itself
rather than running in a broken state.

**No `config.yml`.** Unlike some MuffDev plugins, LuckpermsGUI has no configuration file — every
setting it has is either a fixed convention (see below) or managed live through the GUI itself.
There's nothing to edit on disk after install.

---

## GUI Modules

Everything below is reached by running `/lpgui` and clicking through — there is no separate
command for most of these; the Launcher is the single entry point.

| Module | What it does |
| :--- | :--- |
| **Launcher** | Six tiles: Users, Groups, Tracks, Contexts, Action Log, System Tools. |
| **Users** | Directory (browse/search every known player), Dashboard (groups, primary group, promote/demote), Permission Nodes editor, Meta Keys editor. |
| **Groups** | Directory, Dashboard (weight, prefix, suffix, display name, parent groups, delete), Permission Nodes editor. |
| **Tracks** | Directory, Sequence Editor (select-then-place reordering of a promotion ladder). |
| **Contexts** | Read-only: server static context and any online player's currently active context set. |
| **Action Log** | Browses the LuckPerms audit trail with an actor/target name filter. |
| **System Tools** | Sync Network, Open Web Editor (LuckPerms' own `/lp editor`), Purge Local Cache. |

### The permission tri-state

Every permission node shown in the GUI is one of exactly three states, matching how LuckPerms
itself stores permissions — nothing invented on top of it:

* **True** — a stored node with `value = true`
* **Negated** — a stored node with `value = false`
* **Unset** — no node at all (absence, not a third stored value)

Left-click a permission item to flip True/Negated. Right-click to unset it entirely. This applies
identically in both the User and Group node editors.

### Universal navigation convention

Every paginated screen (54 slots) uses the same bottom row: page controls in the middle, and the
last slot is always **Back** (if you navigated in from somewhere) or **Exit** (if this is the root
of your session). Right-click generally means remove; left-click generally means toggle or select.
Destructive actions (deleting a group or track) require two clicks within ten seconds rather than
acting on the first click.

---

## Chat Input Syntax

Chest inventories can't take multi-field forms, so anywhere the GUI needs more than one value, it
accepts a single compact line typed in chat. Type `cancel`, or click the bundled Cancel link, to
back out of any prompt — every prompt also times out on its own after 30 seconds.

| Context | Syntax | Example |
| :--- | :--- | :--- |
| Add a permission (User or Group node editor) | `key [true\|false] [duration] [context=value]` | `essentials.fly false 7d world=nether` |
| Add a group / parent | `group-name [duration]` | `vip 30d` |
| Add a meta key | `key value` (everything after the first space is the value) | `guild Frostbite Raiders` |
| Duration tokens | `30s` `10m` `2h` `7d` `4w` `6mo` `1y` | one number, one unit — no compounding |
| Context restriction | `key=value` | `world=nether`, `server=survival` |

Only the first value (the key, name, etc.) is ever required — everything in brackets is optional
and can appear in any order after it.
