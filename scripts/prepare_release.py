#!/usr/bin/env python3
"""Validate a version-tagged installable JAR and prepare release metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


def prepare(tag, dist, properties, commit):
    values = dict(line.split("=", 1) for line in properties.read_text().splitlines()
                  if "=" in line and not line.lstrip().startswith("#"))
    version = values["mod_version"].strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("mod_version must be a stable three-part version")
    if not re.fullmatch(r"v" + re.escape(version) + r"(?:-rc\.[1-9]\d*)?", tag):
        raise ValueError("Tag must match mod_version: vVERSION or vVERSION-rc.N")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("A full source commit SHA is required")
    jar_name = f"{values['archives_base_name'].strip()}-{version}.jar"
    jars = list(dist.glob("*.jar"))
    if len(jars) != 1 or jars[0].name != jar_name:
        raise ValueError("Expected exactly one installable JAR, not a sources/dev JAR")
    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("id") != "bookwriter" or metadata.get("version") != version:
            raise ValueError("Packaged mod ID/version does not match this release")
        if metadata.get("environment") != "client":
            raise ValueError("Expected a client-only mod")
    checksum = hashlib.sha256(jar.read_bytes()).hexdigest()
    (dist / "SHA256SUMS").write_text(f"{checksum}  {jar_name}\n")
    preview = "-rc." in tag
    notes = (
        f"## Book Writer {tag}\n\n"
        f"Download **`{jar_name}`** below and put it in your Minecraft client's `mods` folder. "
        "Do not download GitHub's Source code archives to install the mod.\n\n"
        f"Requires **Minecraft {values['minecraft_version'].strip()}**, **Java 21**, "
        f"**Fabric Loader {values['loader_version'].strip()} or later**, and **Fabric API** "
        "for that Minecraft version. This is client-side; no server plugin is required.\n\n"
        "The workflow built and tested this tagged source before attaching the JAR. "
        "`SHA256SUMS` contains its SHA-256 checksum.\n\n"
        + ("**Release candidate:** this may be from an unmerged PR. Normal saved-book persistence, "
           "signing/offhand, multiplayer/server filtering, and custom-font release checks remain "
           "separate from automated tests; see the README checklist.\n\n" if preview else "")
        + f"Source commit: `{commit}`\n"
    )
    (dist / "release-notes.md").write_text(notes)
    return {"version": version, "jar_name": jar_name, "prerelease": str(preview).lower()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--dist", type=Path, required=True)
    parser.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    outputs = prepare(args.tag, args.dist, args.properties, args.commit)
    with args.output.open("a") as output:
        for key, value in outputs.items():
            output.write(f"{key}={value}\n")
    print(json.dumps(outputs))


if __name__ == "__main__":
    main()
