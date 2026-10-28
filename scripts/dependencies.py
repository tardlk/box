"""Pinned Maven dependencies; no previous project files are used."""
from pathlib import Path
import hashlib
import urllib.request

root = Path(__file__).resolve().parents[1]
artifacts = {
    "jsoup.jar": ("org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar", "5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302"),
    "json-test.jar": ("org/json/json/20240303/json-20240303.jar", "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"),
}
for name, (path, expected) in artifacts.items():
    target = root / "lib" / name
    if not target.exists():
        target.write_bytes(urllib.request.urlopen("https://repo.maven.apache.org/maven2/" + path, timeout=30).read())
    actual = hashlib.sha256(target.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f"Dependency checksum mismatch: {name}")
