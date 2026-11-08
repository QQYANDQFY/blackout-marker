# Releasing Blackout Marker

The GitHub repository has to be created by the maintainer. The local project is
already initialized as a git repository and tagged `1.0.0`.

## 1. Create the GitHub repository

Create an **empty** repository named `blackout-marker` (no README, license, or
`.gitignore`), then from this directory:

```bash
git remote add origin https://github.com/<your-account>/blackout-marker.git
git push -u origin main
git push origin 1.0.0
```

## 2. Add the Modrinth token

Create a Modrinth API token at <https://modrinth.com/settings/account> and add it
to the GitHub repository as an Actions secret:

- Name: `MODRINTH_TOKEN`
- Value: the token (never commit it)

## 3. Configure the Modrinth project

In the Modrinth project settings:

- License: **LGPL-3.0-only**
- Project URL: <https://modrinth.com/project/blackout-marker>
- Description: paste the relevant content from `MODRINTH.md`, including the AI disclosure.

## 4. Create the GitHub release

Create a release from tag **`1.0.0`** (no `v` prefix). Use `CHANGELOG.md` as the
release body. Publishing the release triggers `.github/workflows/publish.yml`.

The workflow publishes the committed `dist/blackout-marker-1.0.0.jar`. It does
not compile the source, because the local manual build depends on non-redistributed
Minecraft/Fabric jars in `libs/`.

## Future releases

1. Update `version` in `fabric.mod.json` and `build.ps1`/output names.
2. Rebuild with `build.ps1`; commit the new `dist/*.jar`.
3. Commit and push.
4. Create a new GitHub release from the new tag.

Never commit `MODRINTH_TOKEN`, `libs/`, `build/`, or local logs/configs.
