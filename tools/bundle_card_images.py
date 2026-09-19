#!/usr/bin/env python3
"""
Regenerates src/main/resources/cards/ from the imageUrl of every card in Card.json.

Each card image is stored as <sha256 of imageUrl>.png, the same key
WikiImageCacheService uses, so the runtime lookup is a straight resource read.

Images are quantized to a 256-colour palette, which costs nothing visible on
OSRS item renders but keeps the jar under the Plugin Hub's 10 MiB limit.

Run after adding or changing cards:  python tools/bundle_card_images.py
"""
import hashlib
import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARD_JSON = os.path.join(ROOT, "src", "main", "resources", "Card.json")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "cards")
# Reuse the client's own on-disk cache when present so a rebuild does not
# re-download what this machine already fetched.
RUNELITE_CACHE = os.path.join(
    os.path.expanduser("~"), ".runelite", "Bronzeman-PVP-TCG", "images-v2")
USER_AGENT = "bronzeman-pvp-tcg image bundler (github.com/0xG3r4rD/bronzeman-pvp-tcg)"
PALETTE_COLORS = 256


def sha256_hex(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _get(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def _api_urls(url):
    """Ask the wiki where a file actually lives, following renames/redirects."""
    if "/thumb/" in url:
        filename = url.split("/thumb/", 1)[1].split("/")[0]
    else:
        filename = url.rsplit("/", 1)[-1]
    title = "File:" + urllib.parse.unquote(filename)
    api = ("https://oldschool.runescape.wiki/api.php?action=query&format=json"
           "&prop=imageinfo&iiprop=url&iiurlwidth=130&titles="
           + urllib.parse.quote(title))
    data = json.loads(_get(api).decode("utf-8"))
    out = []
    for page in data.get("query", {}).get("pages", {}).values():
        for info in page.get("imageinfo") or []:
            for key in ("thumburl", "url"):
                if info.get(key):
                    out.append(info[key])
    return out


def _quantity_variants(url):
    """Stackables are filed under a quantity suffix: Foo_detail.png -> Foo_1_detail.png."""
    if "_detail.png" not in url:
        return []
    return [url.replace("_detail.png", "_%d_detail.png" % n) for n in (1, 4)]


def _case_insensitive_match(url):
    """Wiki filenames are case-sensitive after the first letter (Eldritch_Nightmare_staff)."""
    if "/thumb/" in url:
        filename = url.split("/thumb/", 1)[1].split("/")[0]
    else:
        filename = url.rsplit("/", 1)[-1]
    target = urllib.parse.unquote(filename).replace("_", " ").lower()
    prefix = urllib.parse.unquote(filename).split("_")[0]
    api = ("https://oldschool.runescape.wiki/api.php?action=query&format=json"
           "&list=allimages&ailimit=500&aiprefix=" + urllib.parse.quote(prefix))
    data = json.loads(_get(api).decode("utf-8"))
    for item in data.get("query", {}).get("allimages", []):
        if item.get("name", "").replace("_", " ").lower() == target:
            return [u for u in (item.get("url"),) if u]
    return []


def fetch(url):
    """A cold thumbnail can 404 while the wiki generates it, so retry, then ask the API."""
    last = None
    for attempt in range(3):
        try:
            return _get(url)
        except Exception as ex:
            last = ex
            time.sleep(1.5 * (attempt + 1))

    candidates = list(_api_urls(url))
    for variant in _quantity_variants(url):
        candidates.append(variant)
        candidates.extend(_api_urls(variant))
    candidates.extend(_case_insensitive_match(url))
    for candidate in candidates:
        try:
            return _get(candidate)
        except Exception as ex:
            last = ex
    raise RuntimeError("could not fetch %s (%s)" % (url, last))


def main():
    with open(CARD_JSON, encoding="utf-8") as fh:
        cards = json.load(fh)

    urls = []
    seen = set()
    for card in cards:
        url = (card.get("imageUrl") or "").strip()
        if not url:
            print("WARN no imageUrl for card %r" % card.get("name"), file=sys.stderr)
            continue
        if url not in seen:
            seen.add(url)
            urls.append(url)

    os.makedirs(OUT_DIR, exist_ok=True)
    keep = set()
    downloaded = reused = 0
    unresolved = []
    total = 0

    for index, url in enumerate(urls, 1):
        name = sha256_hex(url) + ".png"
        keep.add(name)
        out_path = os.path.join(OUT_DIR, name)
        if os.path.isfile(out_path):
            total += os.path.getsize(out_path)
            continue

        cached = os.path.join(RUNELITE_CACHE, name)
        if os.path.isfile(cached):
            with open(cached, "rb") as fh:
                raw = fh.read()
            reused += 1
        else:
            try:
                raw = fetch(url)
            except Exception as ex:
                unresolved.append((url, str(ex)))
                continue
            downloaded += 1
            time.sleep(0.25)  # be polite to the wiki

        image = Image.open(io.BytesIO(raw)).convert("RGBA")
        image.quantize(colors=PALETTE_COLORS, method=Image.FASTOCTREE).save(
            out_path, "PNG", optimize=True)
        total += os.path.getsize(out_path)

        if index % 100 == 0:
            print("  %d/%d" % (index, len(urls)))

    # Drop images for cards that no longer exist.
    removed = 0
    for existing in os.listdir(OUT_DIR):
        if existing.endswith(".png") and existing not in keep:
            os.remove(os.path.join(OUT_DIR, existing))
            removed += 1

    print("cards with images: %d" % len(urls))
    print("reused from client cache: %d   downloaded: %d   removed stale: %d"
          % (reused, downloaded, removed))
    print("bundle size: %.2f MB" % (total / 1e6))
    if unresolved:
        print(file=sys.stderr)
        print('UNRESOLVED (%d) - these cards have no wiki image and will render'
              ' blank in the plugin:' % len(unresolved), file=sys.stderr)
        for url, err in unresolved:
            print('  ' + url, file=sys.stderr)
            print('      ' + err, file=sys.stderr)


if __name__ == "__main__":
    main()
