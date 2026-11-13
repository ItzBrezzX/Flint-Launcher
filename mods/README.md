# :mods

Modrinth-only mod discovery, compatibility filtering, dependency resolution, install and uninstall.

## Features

- Search is restricted to Modrinth `project_type:mod` results.
- Feature/category and loader filters use Modrinth's `categories` facet.
- Minecraft compatibility uses the `versions` facet.
- Client/server usage filters use the documented `client_side` and `server_side` facets.
- Supported categories, loaders and Minecraft versions are loaded from Modrinth's tag endpoints.
- Mod versions are resolved against the selected profile's Minecraft version and loader.
- Required dependencies are recursively resolved from Modrinth before the selected mod is installed.
- Installed JARs are isolated in each profile's `mods/` directory.
- Downloads are accepted only from `https://cdn.modrinth.com` and verified with Modrinth SHA-512 (SHA-1 fallback).
- A per-profile manifest tracks installed project/version IDs and supports uninstall.

## Source policy

The catalogue and version metadata are read from the public Modrinth API v2. Artifact downloads are restricted to the official Modrinth CDN; no CurseForge catalog, third-party mirror, or arbitrary project URL is used.

## Limitations

- Mod installation does not install or switch the profile's Fabric/Quilt/Forge/NeoForge loader itself. A profile must already use a compatible loader.
- Optional dependencies are not installed automatically. Required dependencies are.
- The launcher still needs a real game launch path and in-game verification before claiming that every downloaded mod loads successfully.

*Type:* Kotlin/JVM module  
*Status:* Modrinth browser and verified per-profile JAR workflow implemented; CI validation pending.
