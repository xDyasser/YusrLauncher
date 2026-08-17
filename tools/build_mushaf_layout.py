#!/usr/bin/env python3
"""Builds the mushaf layout asset: where the printed page breaks every line.

The app downloads the Uthmani Ḥafṣ text as a flat list of āyāt. That is the *words* of the
book and none of its typography — nothing in it says that al-Baqara 2:25 ends halfway down
page 5, which is the one fact a reader has to know before it can put a page on a screen.

This script writes that fact down, once, as `app/src/main/assets/mushaf_layout.json`, from
the King Fahd Complex's own 15-line layout. The output says, for each of the 604 pages, what
its fifteen lines hold — in āyāt and words of the very text the app downloads, so the two fit
together on the device with nothing left to work out at runtime.

Run it when the layout needs rebuilding; the asset it writes is committed, and the app never
fetches any of this itself.

    python3 tools/build_mushaf_layout.py

Sources, all fetched over plain HTTPS:

  * The layout — the Quranic Universal Library's export of the KFGQPC **V1 (1405 print)**
    15-line mushaf: 604 pages × 15 lines, each line a range of word ids, with sūrah headings
    and basmalas marked and short lines flagged as centred.

    V1 rather than V2 (1421H) deliberately. The two prints break 26 pages differently, and V1
    is the one whose page numbering agrees with everybody else's — it matches the page column
    of an independent Qur'an database ayah for ayah, all 6,236 of them, where V2 differs in 56.
    A page number that means what it means in every other mushaf is worth more here than the
    newer print's line breaks.

  * The text — the same Uthmani Ḥafṣ edition the app itself downloads, so that word 4 of a
    line is word 4 of the string the device will actually be holding.

  * Juz, ḥizb, rubʿ and sajda positions — the Ḥafṣ division tables from quran-meta.

The join between layout and text is word counting, and it is checked rather than trusted: the
script asserts that its own count of the whole book comes to the layout's last word id exactly,
and that all 114 sūrahs begin on the word the layout says they begin on. If either fails it
writes nothing.
"""

from __future__ import annotations

import io
import json
import os
import re
import sqlite3
import sys
import tempfile
import urllib.request
import zipfile

LAYOUT_URL = (
    "https://raw.githubusercontent.com/blueheron786/"
    "quranic-universal-library-mushaf-layouts/main/qpc-v1-15-lines.db.zip"
)
TEXT_URL = (
    "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/"
    "editions/ara-quranuthmanihaf.json"
)
DIVISIONS_URL = (
    "https://raw.githubusercontent.com/quran-center/quran-meta/master/src/lists/HafsLists.ts"
)

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "mushaf_layout.json",
)

PAGES = 604
LINES_PER_PAGE = 15
AYAT = 6236

# The two framed pages at the front of the book, and how many lines they hold.
OPENING_PAGES = (1, 2)
OPENING_LINES = 8

# The one repair the downloaded text needs, kept identical to UthmaniText.kt: that edition puts
# the space *before* the alif carrying an open tanwīn, splitting one word into two. Words are
# what this script counts, so it has to close them up exactly as the app does.
SPLIT_TANWIN = ["ࣰ ا", "ࣰ ى", "ࣱ ا", "ࣱ ى", "ࣲ ا", "ࣲ ى"]

# The four places where the downloaded text and the mushaf's word count disagree, because the
# two write the same phrase joined and separate (maqṭūʿ and mawṣūl). Nothing is changed in the
# text; these only say how many words the line data is counting there.
#
#   ٱلۡ + يَاسِينَ at 37:130 is one word in the mushaf and two in the text — hence a join.
#   لَّوۡ مَا, مَا لِيَ and وَمَا لِيَ are two in the mushaf and one in the text — hence splits.
JOINED_IN_MUSHAF = {(37, 130): 1}
SPLIT_IN_MUSHAF = {(15, 7): 1, (27, 20): 1, (36, 22): 1}


def fetch(url: str) -> bytes:
    print(f"  fetching {url}")
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def word_counts() -> dict[tuple[int, int], int]:
    """How many words the mushaf sets each ayah in, keyed by (sūrah, ayah)."""
    text = json.loads(fetch(TEXT_URL))["quran"]
    if len(text) != AYAT:
        sys.exit(f"the text came to {len(text)} āyāt, not {AYAT}")

    counts: dict[tuple[int, int], int] = {}
    for verse in text:
        key = (verse["chapter"], verse["verse"])
        body = verse["text"]
        for split in SPLIT_TANWIN:
            body = body.replace(split, split.replace(" ", ""))
        counts[key] = (
            len(body.split())
            - JOINED_IN_MUSHAF.get(key, 0)
            + SPLIT_IN_MUSHAF.get(key, 0)
        )
    return counts


def tokens(counts: dict[tuple[int, int], int]) -> tuple[dict[int, tuple[int, int, int]], int]:
    """The layout's word ids, worked out from the text.

    The layout numbers every word of the book from 1, and numbers each ayah's closing marker —
    the ۝ with the ayah's number in it — as a word of its own. So an ayah of n words occupies
    n + 1 ids, the last of which is its marker. Walking the whole book in order gives every id
    a meaning: which ayah it is in, and which word of it.
    """
    ids: dict[int, tuple[int, int, int]] = {}
    running = 0
    for surah, ayah in sorted(counts):
        for index in range(1, counts[(surah, ayah)] + 2):
            running += 1
            ids[running] = (surah, ayah, index)
    return ids, running


def divisions() -> dict[str, list[str]]:
    """Juz, ḥizb, rubʿ and sajda, as "sūrah:ayah" strings."""
    source = fetch(DIVISIONS_URL).decode("utf-8")

    def numbers(name: str) -> list[int]:
        match = re.search(rf"{name}: AyahId\[\] = \[(.*?)\]", source, re.S)
        if not match:
            sys.exit(f"{name} is not in the divisions file any more")
        return [int(n) for n in re.findall(r"\d+", match.group(1))]

    # The lists are 1-indexed with a leading 0 and a trailing sentinel one past the last ayah.
    quarters = [n for n in numbers("HizbQuarterList") if 1 <= n <= AYAT]
    juz = [n for n in numbers("JuzList") if 1 <= n <= AYAT]
    sajda = [n for n in numbers("SajdaList") if 1 <= n <= AYAT]
    if (len(quarters), len(juz), len(sajda)) != (240, 30, 15):
        sys.exit(f"divisions came to {len(quarters)}/{len(juz)}/{len(sajda)}, not 240/30/15")

    return {
        # Every rubʿ, every fourth of which starts a ḥizb and every eighth a juz. The ḥizbs are
        # not listed separately: sixty of them are exactly the quarters at 0, 4, 8, … and a list
        # that can disagree with itself is worse than one that cannot.
        "rub": [reference(n) for n in quarters],
        "juz": [reference(n) for n in juz],
        "sajda": [reference(n) for n in sajda],
    }


def reference(ayah_id: int) -> str:
    """"2:255" for the 262nd ayah of the book."""
    return f"{ORDER[ayah_id - 1][0]}:{ORDER[ayah_id - 1][1]}"


def encode(surah: int, ayah: int, index: int, count: int) -> str:
    """One position, as the asset writes it: "2:25:4", or "2:25:e" for an ayah's closing marker."""
    return f"{surah}:{ayah}:{'e' if index > count else index}"


def main() -> None:
    print("mushaf layout")
    counts = word_counts()

    global ORDER
    ORDER = sorted(counts)

    ids, total = tokens(counts)

    archive = zipfile.ZipFile(io.BytesIO(fetch(LAYOUT_URL)))
    name = next(n for n in archive.namelist() if n.endswith(".db"))
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as handle:
        handle.write(archive.read(name))
        database = handle.name

    connection = sqlite3.connect(database)
    print("  layout:", connection.execute("select * from info").fetchone())
    rows = list(
        connection.execute(
            "select page_number, line_number, line_type, is_centered, "
            "first_word_id, last_word_id, surah_number from pages "
            "order by page_number, line_number"
        )
    )
    os.unlink(database)

    # Checked, not trusted. If the word count is out anywhere, every line after it holds the
    # wrong words — silently — so the two ways of catching that are both run before anything
    # is written: the whole book has to come to the same total, and every sūrah has to begin
    # where the layout says it begins.
    last = max(int(row[5]) for row in rows if row[2] == "ayah")
    if last != total:
        sys.exit(f"counted {total} words, layout ends at {last} — the editions have drifted")

    starts = {}
    for position, row in enumerate(rows):
        if row[2] != "surah_name":
            continue
        following = next(r for r in rows[position + 1:] if r[2] == "ayah")
        starts[int(row[6])] = int(following[4])
    for surah in range(1, 115):
        expected = ids[starts[surah]]
        if expected != (surah, 1, 1):
            sys.exit(f"sūrah {surah} begins at {expected}, not its first word")
    print(f"  checked: {total} words, all 114 sūrahs align")

    pages = []
    for number in range(1, PAGES + 1):
        lines = [row for row in rows if row[0] == number]
        # The first two pages are the framed ones — al-Fātiḥa and the opening of al-Baqara, set
        # large and centred inside a border, eight lines to the page. Every other page is fifteen.
        expected = OPENING_LINES if number in OPENING_PAGES else LINES_PER_PAGE
        if len(lines) != expected:
            sys.exit(f"page {number} has {len(lines)} lines, not {expected}")

        first = min(int(row[4]) for row in lines if row[2] == "ayah")
        surah, ayah, index = ids[first]
        encoded = []
        for _, _, kind, centred, _, end, surah_number in lines:
            if kind == "surah_name":
                encoded.append(f"h{int(surah_number)}")
            elif kind == "basmallah":
                encoded.append("b")
            else:
                at = ids[int(end)]
                position = encode(at[0], at[1], at[2], counts[(at[0], at[1])])
                encoded.append(("c" if centred else "") + position)
        pages.append(
            {
                "p": number,
                "s": encode(surah, ayah, index, counts[(surah, ayah)]),
                "l": encoded,
            }
        )

    asset = {
        "version": 1,
        "layout": "KFGQPC 15-line, V1 (1405 print), via the Quranic Universal Library",
        "pages": PAGES,
        "linesPerPage": LINES_PER_PAGE,
        "words": total,
        **divisions(),
        "page": pages,
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as out:
        json.dump(asset, out, ensure_ascii=False, separators=(",", ":"))
        out.write("\n")
    print(f"  wrote {OUT} ({os.path.getsize(OUT) / 1024:.0f} KB)")


ORDER: list[tuple[int, int]] = []

if __name__ == "__main__":
    main()
