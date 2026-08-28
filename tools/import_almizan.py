#!/usr/bin/env python3
"""Turns the Arabic al-Mīzān into the assets the app reads.

`al-Mīzān fī tafsīr al-Qurʾān` is ʿAllāma al-Ṭabāṭabāʾī's tafsīr, twenty volumes of it, and it
is the commentary this app reads because it is the one that belongs beside the rest of what is
here — a Shīʿī mushaf, a Shīʿī translation at the gate, and Mafātīḥ al-Jinān in the hub.

The source is the Arabic original as the Furqān app publishes it: a SQLite file of the book in
Markdown, with a table saying which of its passages every ayah in the book belongs to. That
second table is the reason for taking it from there rather than scraping a website. Al-Mīzān
does not comment ayah by ayah — it prints a run of āyāt, then a *bayān* on the run as a whole,
then what the traditions say about it — so "the tafsīr of 2:3" is only meaningful as "the
passage 2:1-5 that 2:3 is inside", and a source that has already drawn those boundaries is
worth more than one that leaves them to be guessed.

What this writes:

  * `assets/almizan/index.json` — the 594 passages, each with the run of āyāt it covers, and
    the headings inside it so the reader can offer them without opening the file.
  * `assets/almizan/pNNN.md` — one passage, exactly as the source has it. The Markdown is not
    parsed here and not rewritten: the app parses it when it opens one. Nothing is summarised,
    shortened or reordered, so what is on the screen is the book.

Split into a file per passage for the same reason Mafātīḥ is: the whole book is 26 MB, and
opening the tafsīr of one ayah should not read the other five hundred passages. Stored as text
rather than as the SQLite file it came in so that nothing has to be unpacked onto the device
before the first passage can be read.

The checks are the point of the script, and it writes nothing if any of them fails: all 6,236
āyāt must be accounted for, every passage must cover a single unbroken run inside one sūrah,
the runs must tile the book in order with no gap and no overlap, and every passage must carry
the *bayān* heading that makes it a passage of al-Mīzān rather than an empty row.

    python3 tools/import_almizan.py

Source: https://github.com/app-furqan/quran-app-data — `data/tafsir_almizan_ar.db.tar.xz`,
published under CC BY-ND 4.0. The text is redistributed here unchanged, which is what that
licence asks for; the attribution travels with it, into `index.json` and onto the screen that
shows the tafsīr.
"""

from __future__ import annotations

import io
import json
import os
import re
import shutil
import sqlite3
import sys
import tarfile
import tempfile
import urllib.request

SOURCE_URL = (
    "https://raw.githubusercontent.com/app-furqan/quran-app-data/main/"
    "data/tafsir_almizan_ar.db.tar.xz"
)

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "assets", "almizan")

AYAT = 6236
PASSAGES = 594

TITLE = "al-Mīzān fī tafsīr al-Qurʾān"
ARABIC_TITLE = "الميزان في تفسير القرآن"
ATTRIBUTION = "ʿAllāma al-Sayyid Muḥammad Ḥusayn al-Ṭabāṭabāʾī"
SOURCE = (
    "النص العربي كاملًا · app-furqan/quran-app-data · CC BY-ND 4.0"
)

# How many āyāt each sūrah has, so the passages can be checked against the book itself.
SURAH_AYAT = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12,
    12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26,
    30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]


def fetch() -> bytes:
    print(f"  fetching {SOURCE_URL}")
    with urllib.request.urlopen(SOURCE_URL) as response:
        return response.read()


def database(path: str) -> sqlite3.Connection:
    """The tafsīr database, unpacked from the archive the source publishes."""
    archive = fetch()
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:xz") as tar:
        member = next((m for m in tar.getmembers() if m.name.endswith(".db")), None)
        if member is None:
            sys.exit("the archive holds no database")
        extracted = tar.extractfile(member)
        if extracted is None:
            sys.exit("the archive's database could not be read")
        with open(path, "wb") as out:
            shutil.copyfileobj(extracted, out)
    return sqlite3.connect(path)


def passages(db: sqlite3.Connection) -> list[dict]:
    """Every passage, with the run of āyāt it covers — checked as it is read."""
    mapping = db.execute(
        "SELECT surah_number, ayah_number, content_id FROM ayah_mapping "
        "ORDER BY surah_number, ayah_number"
    ).fetchall()
    if len(mapping) != AYAT:
        sys.exit(f"the source maps {len(mapping)} āyāt, not {AYAT}")

    runs: dict[int, list[tuple[int, int]]] = {}
    for surah, ayah, content_id in mapping:
        runs.setdefault(content_id, []).append((surah, ayah))

    if len(runs) != PASSAGES:
        sys.exit(f"the source has {len(runs)} passages, not {PASSAGES}")

    ordered = sorted(runs.items(), key=lambda item: min(item[1]))
    out: list[dict] = []
    for index, (content_id, covered) in enumerate(ordered, start=1):
        surahs = {surah for surah, _ in covered}
        if len(surahs) != 1:
            sys.exit(f"passage {content_id} spans more than one sūrah: {sorted(surahs)}")
        surah = surahs.pop()
        numbers = sorted(ayah for _, ayah in covered)
        if numbers != list(range(numbers[0], numbers[-1] + 1)):
            sys.exit(f"passage {content_id} covers a broken run in sūrah {surah}")

        text = db.execute(
            "SELECT content FROM content WHERE content_id = ?", (content_id,)
        ).fetchone()
        if text is None or not text[0].strip():
            sys.exit(f"passage {content_id} has no text")
        body = text[0]
        if "بيان" not in body:
            sys.exit(f"passage {content_id} carries no bayān and is not a passage of al-Mīzān")

        out.append({
            "id": f"p{index:03d}",
            "surah": surah,
            "from": numbers[0],
            "to": numbers[-1],
            "headings": headings(body),
            "text": body,
        })

    tile(out)
    return out


def headings(body: str) -> list[str]:
    """The `## …` headings of a passage — bayān, the traditions, and what else it has."""
    found = []
    for line in body.split("\n"):
        stripped = line.strip()
        if stripped.startswith("##"):
            heading = stripped.lstrip("#").strip()
            heading = heading.strip("()（） ").strip("«» ").strip()
            if heading and heading not in found:
                found.append(heading)
    return found


def tile(found: list[dict]) -> None:
    """The passages must lay the whole book end to end: no gap, no overlap, nothing twice."""
    expected: list[tuple[int, int]] = []
    for surah, count in enumerate(SURAH_AYAT, start=1):
        expected += [(surah, ayah) for ayah in range(1, count + 1)]

    covered: list[tuple[int, int]] = []
    for passage in found:
        covered += [(passage["surah"], ayah)
                    for ayah in range(passage["from"], passage["to"] + 1)]

    if covered != expected:
        first = next((i for i, pair in enumerate(covered) if i >= len(expected)
                      or pair != expected[i]), len(covered))
        sys.exit(
            f"the passages do not tile the book: they part company at {covered[first]} "
            f"where the mushaf has {expected[first] if first < len(expected) else 'nothing'}"
        )


def write(found: list[dict]) -> None:
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)

    for passage in found:
        with open(os.path.join(OUT, f"{passage['id']}.md"), "w", encoding="utf-8") as out:
            out.write(passage["text"])

    index = {
        "title": TITLE,
        "arabicTitle": ARABIC_TITLE,
        "attribution": ATTRIBUTION,
        "source": SOURCE,
        "passages": [
            {
                "id": passage["id"],
                "s": passage["surah"],
                "a": passage["from"],
                "b": passage["to"],
                "h": passage["headings"],
            }
            for passage in found
        ],
    }
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as out:
        json.dump(index, out, ensure_ascii=False, separators=(",", ":"))
        out.write("\n")

    total = sum(os.path.getsize(os.path.join(OUT, name)) for name in os.listdir(OUT))
    print(f"  wrote {len(found)} passages to {OUT} ({total / 1024 / 1024:.1f} MB of text)")


def main() -> None:
    with tempfile.TemporaryDirectory() as workspace:
        db = database(os.path.join(workspace, "almizan.db"))
        found = passages(db)
        db.close()
    write(found)


if __name__ == "__main__":
    main()
