"""Check staged or downloaded config and JAR bytes. Does not test site playback."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import urlopen
import zipfile


def inspect(config, jar, checksum):
    jar_url, separator, expected = config['spider'].partition(';md5;')
    if not separator or expected != checksum:
        raise ValueError('Config and expected MD5 disagree')
    if hashlib.md5(jar).hexdigest() != expected:
        raise ValueError('Downloaded JAR has a different MD5')
    apis = [site['api'] for site in config['sites']]
    keys = [site['key'] for site in config['sites']]
    if not {'csp_Ikanbot', 'csp_Ivdy'}.issubset(apis) or len(keys) != len(set(keys)):
        raise ValueError('Existing sites missing or site keys duplicated')
    with zipfile.ZipFile(io.BytesIO(jar)) as archive:
        dex = archive.read('classes.dex')
        if not dex.startswith(b'dex\n037\0'):
            raise ValueError('Expected DEX 037')
    return {'md5': expected, 'jar_bytes': len(jar), 'sites': apis, 'jar_url': jar_url, 'validated': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--directory', type=Path, help='Staging directory from prepare_release.py')
    group.add_argument('--config-url', help='Published HTTPS config URL')
    parser.add_argument('--expected-md5', help='Required for remote verification; take from the new build')
    args = parser.parse_args()
    if args.directory:
        folder = args.directory
        manifest = json.loads((folder / 'release-manifest.json').read_text())
        config = json.loads((folder / 'config.json').read_text())
        jar = (folder / 'ikanbot.jar').read_bytes()
        result = inspect(config, jar, args.expected_md5 or manifest['md5'])
        versioned = folder / ('jar/box-' + result['md5'] + '.jar')
        if versioned.read_bytes() != jar or (folder / 'ikanbot.jar.md5').read_text().strip() != result['md5']:
            raise ValueError('Staged files disagree')
        if any(not (folder / name).is_file() for name in manifest['files']):
            raise ValueError('Staging manifest contains a missing file')
    else:
        if not args.expected_md5:
            parser.error('--expected-md5 is required so stale config + stale JAR cannot both pass')
        if urlsplit(args.config_url).scheme != 'https':
            parser.error('Use an HTTPS config URL')
        with urlopen(args.config_url, timeout=30) as response:
            config = json.load(response)
        jar_url = config['spider'].split(';md5;', 1)[0]
        if urlsplit(jar_url).scheme != 'https':
            raise ValueError('Published JAR must use HTTPS')
        with urlopen(jar_url, timeout=30) as response:
            jar = response.read(8 * 1024 * 1024 + 1)
        if len(jar) > 8 * 1024 * 1024:
            raise ValueError('JAR is unexpectedly large')
        result = inspect(config, jar, args.expected_md5)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
