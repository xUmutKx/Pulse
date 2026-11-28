# Screenshots of the apps (shown in Pulse Core's "Extra apps" page)

For each app put its pictures in `docs/apps/<id>/` (the ids are in `scripts/standalone_apps.txt`: `files`, `music`, `video`, `keyboard` ...) and list their file names, one on each line, in `docs/apps/<id>/shots.txt`:

```
1.png
2.png
3.png
```

Pulse Core reads that list from this repository (the `main` branch) and shows the pictures in the app's page, under its features. An app with no `shots.txt` shows its features only.
