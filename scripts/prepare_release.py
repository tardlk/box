"""Stage a release from existing build outputs. Does not build, upload, or change Git."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', default='tardlk/box', help='GitHub owner/repository')
    parser.add_argument('--output', type=Path, default=Path('build/release'))
    parser.add_argument('--snapshot', help='Optional new root config filename, e.g. config-20261029-fix.json')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repo):
        parser.error('Invalid repository name')
    if args.snapshot and (not re.fullmatch(r'config-[A-Za-z0-9_-]+\.json', args.snapshot) or args.snapshot == 'config.json'):
        parser.error('Snapshot must be a new config-*.json filename without directories')
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == root or output == root / 'dist':
        parser.error('Use a staging directory, not the source root or dist/')
    dist = root / 'dist'
    jar = (dist / 'ikanbot.jar').read_bytes()
    md5 = hashlib.md5(jar).hexdigest()
    report = json.loads((dist / 'build-report.json').read_text())
    config = json.loads((dist / 'config.json').read_text())
    if (dist / 'ikanbot.jar.md5').read_text().strip() != md5 or report['md5'] != md5 or report['jar_bytes'] != len(jar):
        raise SystemExit('Build report / MD5 / JAR disagree; build once and retry')
    if config['spider'] != './ikanbot.jar;md5;' + md5:
        raise SystemExit('dist/config.json does not describe this JAR')
    with zipfile.ZipFile(dist / 'ikanbot.jar') as archive:
        if not archive.read('classes.dex').startswith(b'dex\n037\0'):
            raise SystemExit('Expected the current DEX 037 format')
    versioned = 'jar/box-' + md5 + '.jar'
    config['spider'] = 'https://gh-proxy.com/https://raw.githubusercontent.com/' + args.repo + '/main/' + versioned + ';md5;' + md5
    output.mkdir(parents=True, exist_ok=True)
    files = []
    for name in ['ikanbot.jar', versioned]:
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if name == versioned and target.exists() and target.read_bytes() != jar:
            raise SystemExit('Refusing to replace an immutable JAR with different bytes')
        target.write_bytes(jar)
        files.append(name)
    (output / 'ikanbot.jar.md5').write_text(md5 + '\n')
    files.append('ikanbot.jar.md5')
    for name in ['config.json'] + ([args.snapshot] if args.snapshot else []):
        target = output / name
        content = json.dumps(config, ensure_ascii=False, indent=2) + '\n'
        if name != 'config.json' and target.exists() and target.read_text() != content:
            raise SystemExit('Refusing to overwrite an existing snapshot with a different release')
        target.write_text(content)
        files.append(name)
    for name in ['build-report.json', 'verification.json', 'media-check.json', 'poster-check.json', 'ivdy-verification.json', 'douban-verification.json']:
        if (dist / name).is_file():
            shutil.copyfile(dist / name, output / name)
            files.append(name)
    manifest = {'repository': args.repo, 'branch': 'main', 'md5': md5, 'jar_bytes': len(jar), 'files': files,
                'note': 'Staging does not certify live playback; review report dates and limitations before committing.'}
    (output / 'release-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'output': str(output), 'md5': md5, 'files': files}, ensure_ascii=False))


if __name__ == '__main__':
    main()
