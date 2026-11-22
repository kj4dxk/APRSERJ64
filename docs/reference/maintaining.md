# Build, search and maintain this manual

The Markdown files under `docs/` are the authoritative manual and can be read directly on GitHub. The root `mkdocs.yml` defines reading order. Generated reference tables come from the example configuration and Kotlin field schema.

## Build the searchable editions

From the repository root, with Python 3.11 or later:

```powershell
python -m venv build/docs-venv
build/docs-venv/Scripts/python -m pip install -r requirements-docs.txt
build/docs-venv/Scripts/python scripts/manual_reference.py --check
build/docs-venv/Scripts/python scripts/manual_build.py
```

Outputs:

- `build/manual-site/index.html`: web edition with full-text search; serve it over HTTP.
- `build/APRSIS64-manual.html`: self-contained offline edition. Open directly in a browser, search chapter text with its search field, use Ctrl+F for individual matches, or print to PDF.

For local web preview:

```powershell
build/docs-venv/Scripts/python -m mkdocs serve
```

Open the address printed by MkDocs. No application build or transmission is involved.

## Update checklist

1. Check the release version and actual UI labels against the app source.
2. Update task instructions and known limitations together with feature changes.
3. Run `python scripts/manual_reference.py` after changing the example/schema; review the generated diff.
4. Run the build command above. It validates navigation, local links, anchors, TOML/JSON examples and the strict MkDocs build, and creates the offline edition.
5. Check the relevant hardware or UI workflow when available; distinguish source inspection from real-world verification.
6. Commit documentation and its build files together. Do not commit credentials, runtime data or build output.

The documentation workflow builds downloadable artifacts on GitHub. Publishing a hosted site is separate; repository Markdown and the offline edition remain usable without GitHub Pages.
