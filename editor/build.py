#!/usr/bin/env python3
"""Bundles src/* into a single dependency-free waifing_editor.html."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
rd = lambda n: open(os.path.join(here, "src", n), encoding="utf-8").read()
js = "\n".join(rd(f) for f in ("js0_i18n.js", "js1_core.js", "js2_logic.js", "js3_canvas.js", "js4_ui.js", "js5_tools.js"))
html = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Waifing Skilltree Editor</title>
<style>
{rd("style.css")}
</style>
</head>
<body>
{rd("body.html")}
<script>
{js}
</script>
</body>
</html>
"""
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "waifing_editor.html")
html = html.replace("<!doctype html>", "<!doctype html>\n<!-- Waifing Skilltree Editor. Copyright (C) 2026 Dankest0. Licensed under the GNU GPL v3 or later: https://www.gnu.org/licenses/gpl-3.0.html -->", 1)
open(out, "w", encoding="utf-8").write(html)
print(f"{out}: {len(html)//1024} KB")
