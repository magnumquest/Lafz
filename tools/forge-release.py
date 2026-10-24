#!/usr/bin/env python3
"""Creates the Forgejo releases for Lafz: one per tag v0.x.y, with the notes from RELEASE_NOTES.md and the APK from dist/.

A Forgejo "release" is a tag plus a title, a text and attached files. Pushing a tag alone only shows it under "Tags".
The repository is thomas/Lafz on https://git.qnap.haizum.net (its certificate is self-signed: it is fetched once and pinned, see PIN below).

  tools/forge-release.py --dry-run            show what would be created, no token needed
  tools/forge-release.py                      create the missing releases and upload the missing APKs (safe to repeat)
  tools/forge-release.py v0.6.0               only that tag

Token: Forgejo > Settings > Applications > "Generate new token", scope "write:repository" (and "read:user"), saved as the single line of
  ~/.config/lafz-forge-token      (chmod 600; never commit it, never put it on a command line)
"""
import hashlib, json, os, re, ssl, subprocess, sys, urllib.error, urllib.request, uuid

BASE = "https://git.qnap.haizum.net"
REPO = "thomas/Lafz"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOKEN_FILE = os.path.expanduser("~/.config/lafz-forge-token")
PIN = os.path.expanduser("~/.config/lafz-forge.pem")  # the server certificate, trusted for this host only


def notes_by_version():
    """{'0.6.0': 'text', ...}; a heading like '0.4.0 – 0.4.1' applies to both versions"""
    text = open(os.path.join(ROOT, "RELEASE_NOTES.md"), encoding="utf-8").read()
    out = {}
    for head, body in re.findall(r"^## (.+?)\n(.*?)(?=^## |\Z)", text, re.M | re.S):
        for v in re.findall(r"\d+\.\d+\.\d+", head):
            out[v] = body.strip()
    return out


def git_tags():
    r = subprocess.run(["git", "-C", ROOT, "tag", "-l", "v0.*"], capture_output=True, text=True, check=True)
    return sorted(r.stdout.split(), key=lambda t: [int(x) for x in t[1:].split(".")])


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def context():
    if not os.path.exists(PIN):  # first use: fetch and pin the certificate (trust on first use; compare the fingerprint with the forge if in doubt)
        pem = subprocess.run("echo | openssl s_client -connect git.qnap.haizum.net:443 -servername git.qnap.haizum.net 2>/dev/null | openssl x509 -outform PEM",
                             shell=True, capture_output=True, text=True, check=True).stdout
        os.makedirs(os.path.dirname(PIN), exist_ok=True)
        open(PIN, "w").write(pem)
        print("pinned the forge certificate in", PIN)
    return ssl.create_default_context(cafile=PIN)


class Api:
    def __init__(self):
        self.ctx = context()
        self.token = open(TOKEN_FILE).read().strip()

    def call(self, method, path, body=None, raw=None, ctype=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(f"{BASE}/api/v1{path}", data=data, method=method)
        req.add_header("Authorization", "token " + self.token)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", ctype or "application/json")
        try:
            with urllib.request.urlopen(req, context=self.ctx, timeout=600) as r:
                txt = r.read().decode()
                return r.status, (json.loads(txt) if txt else None)
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode(errors="replace")[:300]

    def upload(self, release_id, path):
        name = os.path.basename(path)
        b = uuid.uuid4().hex
        head = f'--{b}\r\nContent-Disposition: form-data; name="attachment"; filename="{name}"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n'.encode()
        raw = head + open(path, "rb").read() + f"\r\n--{b}--\r\n".encode()
        return self.call("POST", f"/repos/{REPO}/releases/{release_id}/assets?name={name}", raw=raw, ctype=f"multipart/form-data; boundary={b}")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    notes = notes_by_version()
    tags = [t for t in git_tags() if not args or t in args]
    api = None if dry else Api()
    for tag in tags:
        v = tag[1:]
        apk = os.path.join(ROOT, "dist", f"Lafz_{v}-release.apk")
        has_apk = os.path.exists(apk)
        body = notes.get(v, f"Lafz {v}")
        if has_apk:
            body += f"\n\n---\n`{os.path.basename(apk)}`  sha256 `{sha256(apk)}`"
        print(f"{tag}: notes {len(notes.get(v, ''))} chars, apk {'%.0f MB' % (os.path.getsize(apk) / 1e6) if has_apk else 'none in dist/'}")
        if dry:
            continue
        status, rel = api.call("GET", f"/repos/{REPO}/releases/tags/{tag}")
        if status == 404:
            status, rel = api.call("POST", f"/repos/{REPO}/releases", {"tag_name": tag, "name": f"Lafz {v}", "body": body, "draft": False, "prerelease": False})
            print("   created" if status == 201 else f"   create failed: {status} {rel}")
            if status != 201:
                continue
        elif status != 200:
            print(f"   lookup failed: {status} {rel}"); continue
        else:
            print("   release exists")
        if has_apk:
            have = {a["name"] for a in rel.get("assets", [])}
            if os.path.basename(apk) in have:
                print("   apk already attached")
            else:
                status, r = api.upload(rel["id"], apk)
                print("   apk uploaded" if status == 201 else f"   upload failed: {status} {r}")


if __name__ == "__main__":
    main()
