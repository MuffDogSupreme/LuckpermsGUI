# Modrinth Submission Checklist

Everything below is what I know for certain about this plugin, mapped onto Modrinth's project
creation form. I can't submit this for you — I have no Modrinth account, API token, or browser
session to do it with — but every value here is either a verified fact about the plugin or a
clearly marked placeholder for something only you can supply. Modrinth's exact form fields/limits
can change; if something here doesn't match what you see on the actual page, trust the page.

---

## Project Details

| Field | Value |
|---|---|
| **Project name** | `LuckpermsGUI` |
| **Slug** | `luckpermsgui` (matches your existing `luckpermsgui.muffdev.xyz` naming) |
| **Project type** | Plugin |
| **Summary** | See `modrinth/summary.txt` — 149 characters. Paste it in and confirm it fits; I can't verify Modrinth's current exact limit from here. |
| **Description** | The full `README.md` at the project root is written to drop directly into Modrinth's description field (it's plain Markdown, no repo-specific syntax). |
| **Icon** | `modrinth/icon.svg` — square, 512×512. If Modrinth's uploader wants a raster image rather than SVG, convert it first (Inkscape, or any online SVG→PNG tool, exporting at 512×512). |

## Categorization

| Field | Value | Why |
|---|---|---|
| **Categories** | `Management`, `Utility` | It's an admin tool for managing existing LuckPerms data — not gameplay content, so gameplay categories (Adventure, Magic, Mobs, etc.) don't apply. |
| **Loaders** | `Paper` only | Uses Paper-exclusive APIs (`LifecycleEvents.COMMANDS`/`BasicCommand` for command registration, Paper's `AsyncChatEvent`). **Do not** also select Spigot/Bukkit — it won't run there. |
| **Environment** | Client: **Unsupported** · Server: **Required** | Standard classification for a server-side-only admin plugin. The chest GUI is just inventory packets — no client-side mod needed, works with any vanilla client. |
| **Game versions** | 1.20.6 minimum | This is a hard floor, not a style choice — the command registration API doesn't exist before 1.20.6. I compiled and verified against 1.20.6 specifically. I have **not** personally verified 1.21.x+ — the plugin's `api-version: '1.20'` declaration means it's expected to keep working on newer Paper builds, but "expected" isn't "tested." Check your actual target version before marking broader support, or just list 1.20.6 conservatively and widen it later once you've confirmed. |

## License

| Field | Value |
|---|---|
| **License** | Custom / All Rights Reserved (whichever label Modrinth currently uses for "not a standard open-source license") |
| **License URL** | Point this at `LICENSE.md` if you push this to a repo, or at your wiki's `/LuckpermsGUI/license` page once it has a real domain. Don't pick an SPDX license like MIT/GPL — the EULA you already use for your other plugins is explicitly proprietary (no redistribution, no resale), which is incompatible with those licenses' terms. |

## Dependencies

| Field | Value |
|---|---|
| **Required dependency** | LuckPerms. When Modrinth's version-upload flow asks you to link dependencies, search for LuckPerms there — I believe its project slug is `luckperms`, but I can't browse Modrinth from here to confirm that's still current, so verify it's the right project before linking. |

## Links (all currently placeholders — fill in what you have)

| Field | Value |
|---|---|
| **Source code (GitHub)** | Not set — this project doesn't have a public repo yet. Leave blank, or set one up first if you want this filled in. |
| **Issue tracker** | Same as above. |
| **Wiki** | You have wiki content ready (see `../wiki-content/`), but I don't know what domain it's actually hosted at — you only shared path fragments (`/LuckpermsGUI/wiki` etc.), not the root domain. Fill in once you know it. |
| **Discord/support** | Not set — add your own support channel if you have one. |

## Per-Version Upload (when you upload the 1.0.0 jar itself)

| Field | Value |
|---|---|
| **Version number** | `1.0.0` (matches the jar's actual `pom.xml` version — not made up) |
| **Version title** | `1.0.0 — Initial Release` |
| **Changelog** | `modrinth/version-1.0.0-changelog.md` — paste directly |
| **Release channel** | Release |
| **Game versions** | Same as above (1.20.6, widen once you've tested further) |
| **Loaders** | Paper |
| **File to upload** | `dist/LuckpermsGUI-1.0.0-Release.jar` (this is the actual compiled jar, built and verified in this project — not a placeholder) |

## Gallery / Screenshots

Not included. There's no live Minecraft server in the environment I built this in, so I have no
way to capture real in-game screenshots of the GUI without fabricating them — and I won't fake
screenshots of a working product. Once you've got this running on an actual server, screenshots of
the Launcher and a couple of the module screens (the Users Dashboard and the permission node
editor are probably the most visually interesting) would round out the listing well. Happy to help
you write alt text / captions for them once you have them.
