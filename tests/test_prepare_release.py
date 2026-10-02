import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("prepare_release", Path(__file__).parents[1] / "scripts/prepare_release.py")
assert spec is not None and spec.loader is not None
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.dist = self.root / "dist"
        self.dist.mkdir()
        self.properties = self.root / "gradle.properties"
        self.properties.write_text("mod_version=1.1.0\narchives_base_name=bookwriter\nminecraft_version=1.21.7\nloader_version=0.16.14\n")
        self.jar = self.dist / "bookwriter-1.1.0.jar"
        self.make_jar()

    def make_jar(self, version="1.1.0", environment="client"):
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "bookwriter", "version": version, "environment": environment}))

    def prepare(self, tag="v1.1.0", commit="a" * 40):
        return release.prepare(tag, self.dist, self.properties, commit)

    def test_stable_release_and_checksum(self):
        result = self.prepare()
        self.assertEqual(result["prerelease"], "false")
        digest, filename = (self.dist / "SHA256SUMS").read_text().split()
        self.assertEqual(filename, self.jar.name)
        import hashlib
        self.assertEqual(digest, hashlib.sha256(self.jar.read_bytes()).hexdigest())
        self.assertIn("Source commit:", (self.dist / "release-notes.md").read_text())

    def test_candidate_is_marked_and_explained(self):
        self.assertEqual(self.prepare("v1.1.0-rc.1")["prerelease"], "true")
        self.assertIn("unmerged PR", (self.dist / "release-notes.md").read_text())

    def test_wrong_version_or_unsafe_tag_is_rejected(self):
        for tag in ["v1.2.0", "v1.1.0-rc.0", "v1.1.0-rc.1\nother=value", "v1.1.0-beta.1"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.prepare(tag)

    def test_wrong_packaged_version_is_rejected(self):
        self.make_jar(version="1.0.2")
        with self.assertRaises(ValueError): self.prepare()

    def test_server_mod_is_rejected(self):
        self.make_jar(environment="server")
        with self.assertRaises(ValueError): self.prepare()

    def test_extra_sources_jar_is_rejected(self):
        (self.dist / "bookwriter-1.1.0-sources.jar").write_bytes(b"fixture")
        with self.assertRaises(ValueError): self.prepare()

    def test_missing_jar_is_rejected(self):
        self.jar.unlink()
        with self.assertRaises(ValueError): self.prepare()

    def test_commit_must_be_full_literal_sha(self):
        with self.assertRaises(ValueError): self.prepare(commit="main")


if __name__ == "__main__":
    unittest.main()
