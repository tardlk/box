from pathlib import Path
import hashlib
import json
import struct
import zipfile
import zlib

root = Path(__file__).resolve().parents[1]
dex = (root / "build/dex/classes.dex").read_bytes()
assert dex.startswith(b"dex\n")
assert struct.unpack_from("<I", dex, 8)[0] == zlib.adler32(dex[12:]) & 0xffffffff

# Read class definitions, rather than testing string references to compile-only stubs.
strings_count, strings_offset = struct.unpack_from("<II", dex, 56)
types_count, types_offset = struct.unpack_from("<II", dex, 64)
classes_count, classes_offset = struct.unpack_from("<II", dex, 96)
strings = []
for index in range(strings_count):
    offset = struct.unpack_from("<I", dex, strings_offset + index * 4)[0]
    while dex[offset] & 128:
        offset += 1
    offset += 1
    strings.append(dex[offset:dex.index(b"\0", offset)].decode("utf-8", "replace"))
types = [strings[struct.unpack_from("<I", dex, types_offset + index * 4)[0]] for index in range(types_count)]
classes = {types[struct.unpack_from("<I", dex, classes_offset + index * 32)[0]] for index in range(classes_count)}
assert "Lcom/github/catvod/spider/Ikanbot;" in classes
assert "Lcom/github/catvod/spider/Ivdy;" in classes
assert "Lcom/github/catvod/spider/Douban;" in classes
assert "Lcom/github/catvod/spider/Proxy;" in classes
assert "Lcom/github/catvod/crawler/Spider;" not in classes
assert not any(value.startswith("Lorg/json/") for value in classes)
assert any(value.startswith("Lorg/jsoup/") for value in classes)

target = root / "dist/ikanbot.jar"
with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("classes.dex", dex)
    archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
    with zipfile.ZipFile(root / "lib/jsoup.jar") as dependency:
        archive.writestr("META-INF/JSOUP-LICENSE.txt", dependency.read("META-INF/LICENSE"))
digest = hashlib.md5(target.read_bytes()).hexdigest()
(root / "dist/ikanbot.jar.md5").write_text(digest + "\n")
config = {
    "spider": "./ikanbot.jar;md5;" + digest,
    # 站点顺序即首页 Tab 顺序，第一个是默认打开的分类页。
    # 豆瓣放第一是仓库所有者的要求（2026-10-29）；它 searchable=0，不影响点卡片后的同名搜索取哪个站。
    "sites": [{"key": "douban", "name": "豆瓣导航", "type": 3, "api": "csp_Douban",
               "searchable": 0, "quickSearch": 0, "filterable": 1, "changeable": 1,
               # indexs=1 让宿主把这一站当"导航站"：点卡片先进搜索/聚合页，而不是直接播第一个同名结果。
               # 手机版走 SearchActivity，电视版走 CollectActivity（都带 keyword）。不要删。
               "indexs": 1, "timeout": 30},
              {"key": "ikanbot_fresh", "name": "爱看机器人", "type": 3,
               "api": "csp_Ikanbot", "searchable": 1, "quickSearch": 1,
               "filterable": 1, "changeable": 1, "timeout": 120,
               "ext": {"webview": True}},
              {"key": "ivdy", "name": "爱电影", "type": 3, "api": "csp_Ivdy", "searchable": 1,
               "quickSearch": 1, "filterable": 1, "changeable": 1, "timeout": 60}],
    "parses": []
}
(root / "dist/config.json").write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n")
report = {"jar_bytes": target.stat().st_size, "md5": digest, "dex_version": dex[4:7].decode(),
          "class_count": len(classes), "entry_class": "com.github.catvod.spider.Ikanbot",
          "compile_stub_excluded": True, "jsoup_included": True,
          "additional_entries": ["com.github.catvod.spider.Ivdy", "com.github.catvod.spider.Douban"]}
(root / "dist/build-report.json").write_text(json.dumps(report, indent=2) + "\n")
with zipfile.ZipFile(root / "dist/ikanbot-fongmi5.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
    for name in ["ikanbot.jar", "ikanbot.jar.md5", "config.json", "build-report.json", "verification.json", "media-check.json", "poster-check.json", "ivdy-verification.json", "douban-verification.json"]:
        path = root / "dist" / name
        if path.exists():
            bundle.write(path, name)
    for name in ["README.md", "IVDY.md"]:
        if (root / name).exists():
            bundle.write(root / name, name)
print(json.dumps(report, ensure_ascii=False))
