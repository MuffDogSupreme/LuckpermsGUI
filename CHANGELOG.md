# Changelog

All notable changes to LuckpermsGUI are documented here. Versions follow `MAJOR.MINOR.PATCH`.

## [1.0.0] — Initial Release

Initial public release. Full feature set:

### Added
- **Launcher** — 27-slot hub linking every module.
- **Users** — Directory (search/browse, 200-player head-rendering threshold), Dashboard (groups,
  primary group, track promotion/demotion), Permission Nodes editor (True/Negated/Unset, temporary
  nodes with expiry, single-context restriction), Meta Keys editor.
- **Groups** — Directory (with group creation), Dashboard (weight, prefix, suffix, display name,
  parent groups, two-click-confirm deletion), Permission Nodes editor.
- **Tracks** — Directory (with track creation), Sequence Editor (select-then-place visual
  reordering, two-click-confirm deletion).
- **Contexts** — Read-only view of platform info, server static context, and any online player's
  active context set.
- **Action Log** — Cached (45s TTL), filterable view of the LuckPerms audit trail, with a
  force-refresh option.
- **System Tools** — Network sync, LuckPerms web editor bridge, local permission cache purge.
- `/lpgui` command with `open`, `setgroup`, `addperm`, `removeperm`, `menu`, and `help`
  subcommands, plus `/lpg` and `/luckpermsgui` aliases.
- Every mutating action is submitted to LuckPerms' own action log, so changes made through this
  plugin are indistinguishable from changes made through LuckPerms' own commands in `/lp log`.

### Requirements
- Paper 1.20.6 or newer (uses Paper's Brigadier-based command registration API, unavailable on
  earlier builds)
- Java 21
- LuckPerms, installed and started successfully
