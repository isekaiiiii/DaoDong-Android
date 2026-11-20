#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
manifest = root / "app/src/main/AndroidManifest.xml"
xml_files = list(res.rglob("*.xml")) + [manifest]
for file in xml_files:
    ET.parse(file)

strings = set()
for file in res.glob("values*/*.xml"):
    tree = ET.parse(file)
    for node in tree.getroot():
        if node.tag == "string" and node.get("name"):
            strings.add(node.get("name"))

xml_names = {p.stem for p in (res / "xml").glob("*.xml")}
missing = []
for file in xml_files:
    text = file.read_text(encoding="utf-8")
    for name in re.findall(r"@string/([A-Za-z0-9_]+)", text):
        if name not in strings:
            missing.append(f"{file.relative_to(root)}: @string/{name}")
    for name in re.findall(r"@xml/([A-Za-z0-9_]+)", text):
        if name not in xml_names:
            missing.append(f"{file.relative_to(root)}: @xml/{name}")

if missing:
    print("Missing Android resources:")
    print("\n".join(f" - {item}" for item in sorted(set(missing))))
    sys.exit(1)

props = (root / "gradle.properties").read_text(encoding="utf-8")
if re.search(r"(?im)^\s*org\.gradle\.java\.home\s*=.*(?:[A-Za-z]:\\|\\Android\\Studio)", props):
    print("Do not commit a machine-specific Windows org.gradle.java.home path.")
    sys.exit(1)

main_activity = (root / "app/src/main/java/com/ayane/dynamicisland/MainActivity.kt").read_text(encoding="utf-8")
if "IslandOverlayService.start(this)" in main_activity:
    print("Launcher Activity must not start the overlay service automatically.")
    sys.exit(1)

print(f"Resource/startup preflight passed: {len(xml_files)} XML files, all @string/@xml references resolved, no Windows Java path, no launcher auto-start.")
