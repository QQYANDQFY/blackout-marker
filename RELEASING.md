# Releasing Blackout Marker

Repository: <https://github.com/QQYANDQFY/blackout-marker>

The project is already committed and tagged `1.0.0`. The Modrinth token is stored
as the `MODRINTH_TOKEN` Actions secret.

## 1. Push / update the repository

```bash
cd "D:\PCL\.minecraft\versions\26.3-Fabric 0.19.5\blackout-marker"
git -c http.proxy= -c https.proxy= -c http.version=HTTP/1.1 push origin main
```

## 2. Configure the Modrinth project

In the Modrinth project settings:

- License: **LGPL-3.0-only**
- Project URL: <https://modrinth.com/project/blackout-marker>
- Description: paste the relevant content from `MODRINTH.md`, including the AI disclosure.
- Links → GitHub: <https://github.com/QQYANDQFY/blackout-marker>

## 3. Trigger the publish workflow

The workflow runs on a pushed tag matching `*`. Use a version tag without a `v`
prefix so Modrinth receives a clean version string:

```bash
git tag 1.0.0
git -c http.proxy= -c https.proxy= -c http.version=HTTP/1.1 push origin 1.0.0
```

For a new release, bump the version in `fabric.mod.json` and `build.ps1`, rebuild,
commit the new `dist/*.jar`, push `main`, then push the new tag.

The workflow publishes the committed `dist/blackout-marker-1.0.0.jar`. It does
not compile the source, because the manual build depends on non-redistributed
Minecraft/Fabric jars in `libs/`.

A GitHub Release is optional and is not required to trigger publication.

Never commit `MODRINTH_TOKEN`, `libs/`, `build/`, or local logs/configs.
