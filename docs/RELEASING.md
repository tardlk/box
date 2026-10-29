# 构建后怎么发布、检查、回滚

发布仓库是 `tardlk/box` 的 `main`。以当前用户授权为准；文档本身不授予发布权限。
只改文档不需要重建 JAR，也不应顺便更换线上配置的 MD5。

## 1. 两种配置的区别

| 文件/目录 | 用途 |
|---|---|
| 根目录 `config.json` / `ikanbot.jar` / `ikanbot.jar.md5` | GitHub 当前在线版本，配置里的 JAR 是 gh-proxy 加速绝对地址 |
| `jar/box-<md5>.jar` | 已发布的固定内容文件；旧配置可继续引用，禁止覆盖不同字节 |
| 根目录 `config-日期-主题.json` | 发布快照，避开配置加速缓存；历史快照按日期原样保留（有人存过旧链接），**不要删、不要覆盖** |
| `dist/` | 本机构建输出；其中 config 使用相对 JAR 地址，不能直接替换线上配置 |
| `build/release/` | `prepare_release.py` 生成的发布暂存目录，不自动上传 |
| 根目录各 `*-check.json` / `*-verification.json` | 随发布保留的验证证据；注明采样日期和失败，不等于本轮已重新实测 |

从干净克隆执行 `build.sh` 会创建本地 `dist/`。
历史发布文件不反向充当编译源码；不要把旧 JAR 解包再说是从当前源码构建。

## 2. 有代码改动时准备发布

```bash
bash build.sh
bash test.sh
# 根据改动，选择相关实网检查：--live / --posters / --ivdy / --douban
python3 scripts/prepare_release.py --output build/release --snapshot config-20261029-example.json
python3 scripts/verify_release.py --directory build/release
```

上面快照名称仅演示，每次发布换成实际日期/主题，不能复用已有不同版本的名称。
两个脚本只有 Python 标准库依赖；prepare 不重编译、不重新打包、不操作 Git、不上传。
它检查现有 JAR、MD5、build-report 与 dist/config 是否对应，生成加速配置并复制同一份 JAR。

`build/release/release-manifest.json` 列出需要发布的文件。检查报告日期、CDN 错误和设备测试状态后，再随源码一起提交。
保留 `ikanbot_fresh`、`ivdy` key；增加站点时修改 package.py 配置源，不能只手改线上 JSON。

**常见陷阱：** 当前 package.py 的 ZIP 条目带打包时间，同一份 DEX 多次打包可能产生不同 JAR MD5。
先完成最终构建，再准备发布；不要记录 MD5 后又运行 package.py，却继续使用旧校验值。

## 3. 普通 Git 提交

在真正的克隆仓库里，先检查状态和远端更新，不覆盖他人工作：

```bash
git status --short
git fetch origin
git diff --stat
```

然后按暂存清单把发布文件复制到仓库根及 `jar/`。以下从暂存目录复制清单中的文件，不会自动提交：

```bash
python3 - <<'PY'
import json, shutil
from pathlib import Path
stage = Path('build/release')
manifest = json.loads((stage / 'release-manifest.json').read_text())
for name in manifest['files']:
    target = Path(name)
    source = stage / name
    if target.exists() and (name.startswith('jar/') or name.startswith('config-')):
        if target.read_bytes() != source.read_bytes():
            raise SystemExit('Refusing to overwrite immutable file: ' + name)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
PY
```

审查源码、文档和发布文件后，显式 `git add` 这些文件，`git diff --cached` 检查再 commit/push。
不要盲目 `git add .`；`dist/`、本机路径、.publish 暂存、缓存、凭据和临时抓包不属于提交内容。
不要 force push 掩盖远端新提交；若并发修改，先获取并解决冲突。

早先的发布是在一个没有 `.git` 的目录里通过 GitHub 连接器完成的。
有正常 Git 认证时直接 `git push` 即可，但不要假定一定装了 `gh`。

## 4. 只有 GitHub 连接器时

按以下依赖顺序操作，源码和新 JAR/JSON 放在**同一提交**中：

1. 读取 `refs/heads/main`，记住当前 head；读取该 commit 的真实 `tree.sha`。
2. 文本使用 UTF-8 blob；JAR 使用 base64 blob，二进制内容不能当 UTF-8 上传。
3. 用原树作为 base_tree，新建 tree，只更新本次文件；保留其他文件、历史 JAR 和快照。
4. 新建 commit，parent 为刚才 head。
5. 更新 main 时使用 expected_sha=head、force=false。若 lease 不匹配，重新读取并合并，不能直接覆盖。
6. 再读远端 ref 与文件，确认提交已在 main 上。

连接器工具名可能因环境而异；先发现其实际 schema。不要把旧会话里的工具名、变量或存储 key 当作可用凭据。

## 5. 发布后验证：必须对照预期的新 MD5

从这一次 `dist/ikanbot.jar.md5` 读取 MD5，然后运行：

```bash
python3 scripts/verify_release.py \
  --config-url https://gh-proxy.com/https://raw.githubusercontent.com/tardlk/box/main/config.json \
  --expected-md5 ACTUAL_NEW_MD5
```

将 `ACTUAL_NEW_MD5` 换成实际值。脚本下载配置及它引用的 JAR，比对预期 MD5、DEX 文件、既有站点及 key 唯一性。
远程校验强制给预期 MD5，避免“旧配置 + 旧 JAR 一致”也被误记为最新发布成功。
若固定配置被加速服务缓存，用本次新快照 URL 做同样验证，再将可用快照链接给用户。

下载校验只证明发布文件一致，不能代替站点接口和真机播放测试。

## 6. 回滚

优先还原旧版本 `config.json`、`ikanbot.jar`、md5 与对应 build-report，或者把配置重新指向历史固定 JAR。
保持 JSON 引用、JAR 实际字节与 MD5 一致。通过新的回滚提交完成，不改写历史文件或强推重置。

要回滚时，从 [HANDOFF.md](../HANDOFF.md) 第 1 节取上一版 JAR 名（那里按发布时间列了最近几版），
或者按 `config-日期-主题.json` 找当时的快照，它们引用的 JAR 都还在 `jar/` 里。
不要在这里写死版本号——它会随每次发布过期。
