"""Static checks for the selectable logo catalogue (no Android SDK required)."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
aliases = manifest.findall("application/activity-alias")
assert len(aliases) == 8, "Every choice must have a launcher alias"
assert sum(a.get(ANDROID + "enabled") == "true" for a in aliases) == 1
catalog = (ROOT / "app/src/main/java/dev/endlesssea/app/branding/AppLogos.kt").read_text()
for alias in aliases:
    assert alias.get(ANDROID + "targetActivity") == "dev.endlesssea.app.MainActivity"
    assert alias.get(ANDROID + "name") in catalog
    icon = alias.get(ANDROID + "icon").split("/")[1]
    adaptive = RES / "mipmap-anydpi-v26" / (icon + ".xml")
    assert adaptive.is_file(), icon
    tree = ET.parse(adaptive).getroot()
    assert tree.tag == "adaptive-icon"
    assert tree.find("foreground") is not None
for name in re.findall(r"R.drawable.(logo_\w+)", catalog):
    image = RES / "drawable-nodpi" / (name + ".webp")
    assert image.is_file(), name
images = list((RES / "drawable-nodpi").glob("logo_*.webp"))
assert len(images) == 8
size = sum(p.stat().st_size for p in images)
assert size < 600_000, f"Logo budget exceeded: {size} bytes"
assert not (RES / "drawable-nodpi/new_logos").exists()
print(f"8 choices, 8 launcher aliases, one default; WebP logos: {size:,} bytes")
