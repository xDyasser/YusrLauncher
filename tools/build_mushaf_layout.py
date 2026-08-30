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

  * The layout — the Quranic Universal Library's export of the KFGQPC **V2 (1421H print)**
    15-line mushaf: 604 pages × 15 lines, each line a range of word ids, with sūrah headings
    and basmalas marked and short lines flagged as centred.

    V2 rather than V1 (1405 print), because V2 is the mushaf people are holding. The Complex
    has set the book three times — 1405, 1421 and 1441 — and while all three run to the same
    604 pages, they do not break their lines in the same places: V1 and V2 put a different
    word at the end of 4,650 of the book's 9,046 lines. Nearly every copy printed this century
    is the 1421 setting, so that is the one a reader comparing this page against the page in
    their hands will be comparing it against.

    The page numbering is not the deciding question, because it does not differ: both prints
    open all 114 sūrahs on the same page, and both put every ayah of the book on the page the
    rest of the world cites it by. An earlier version of this script chose V1 believing
    otherwise; the check below is run on whichever layout is fetched, and V2 passes it.

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
import math
import os
import re
import sqlite3
import sys
import tempfile
import urllib.request
import zipfile

LAYOUT_URL = (
    "https://raw.githubusercontent.com/blueheron786/"
    "quranic-universal-library-mushaf-layouts/main/qpc-v2-15-lines.db.zip"
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


def word_counts() -> tuple[dict[tuple[int, int], int], dict[tuple[int, int], list[str]]]:
    """How many words the mushaf sets each ayah in, and the words themselves."""
    text = json.loads(fetch(TEXT_URL))["quran"]
    if len(text) != AYAT:
        sys.exit(f"the text came to {len(text)} āyāt, not {AYAT}")

    counts: dict[tuple[int, int], int] = {}
    words: dict[tuple[int, int], list[str]] = {}
    for verse in text:
        key = (verse["chapter"], verse["verse"])
        body = verse["text"]
        for split in SPLIT_TANWIN:
            body = body.replace(split, split.replace(" ", ""))
        words[key] = body.split()
        counts[key] = (
            len(words[key])
            - JOINED_IN_MUSHAF.get(key, 0)
            + SPLIT_IN_MUSHAF.get(key, 0)
        )
    return counts, words


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


FONT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "res", "font", "uthmanic_hafs.ttf",
)

# Kept identical to UthmaniText.kt: the three open tanwīn this edition writes in the Arabic
# Extended-A block, which the face sets with the ordinary ones.
PRINTED = {0x08F0: 0x064B, 0x08F1: 0x064C, 0x08F2: 0x064D}

# ARABIC START OF RUB EL HIZB. The edition carries it on the word that opens a quarter; the page
# sets it itself, so it is taken off there and put back as a word of its own.
RUB_EL_HIZB = "\u06de"

# Kept identical to Setting.kt.
FACE_SPACE, WORD_SPACE = 0.22, 0.06


def set_line(begins, ends, ids, words):
    """One line as the app sets it: its words, its ayah markers and its rubʿ mark, in order.

    Counted off the text the way MushafPage counts it — by splitting the ayah on its spaces —
    rather than off the layout's own word ids, so that this measures the line the device will
    actually draw. The two agree everywhere but the four maqṭūʿ and mawṣūl āyāt named above.
    """
    out = []
    for word_id in range(begins, ends + 1):
        surah, ayah, index = ids[word_id]
        if index > len(words[(surah, ayah)]):
            # The closing marker. The face draws the digits inside the medallion itself, so the
            # word on the line is the number and nothing else — the same as MushafPage sets.
            out.append("".join(chr(0x0660 + int(d)) for d in str(ayah)))
            continue
        form = words[(surah, ayah)][index - 1]
        # The page sets the rubʿ mark itself, so where the edition carries one it is a word of
        # its own on the line rather than part of the word behind it.
        if form.startswith(RUB_EL_HIZB):
            out.append(RUB_EL_HIZB)
            form = form[len(RUB_EL_HIZB):]
        out.append(form)
    return out


def report_widest(pages):
    """The longest line in the book, which is the number Setting.WIDEST holds.

    The size the whole mushaf is set at is worked back from this one line, so a layout that
    breaks its lines somewhere else has a different longest line and wants a different number.
    Reported rather than written: it belongs beside the rest of the page's arithmetic, in
    Setting.kt, where the reasoning for it is.

    Needs uharfbuzz to shape the Arabic, which is not worth making this script depend on — the
    layout is correct without it, only unmeasured.
    """
    try:
        import uharfbuzz as hb
    except ImportError:
        print("  (install uharfbuzz to measure the longest line for Setting.WIDEST)")
        return

    face = hb.Face(hb.Blob.from_file_path(FONT))
    font = hb.Font(face)

    def ems(text):
        buffer = hb.Buffer()
        buffer.add_str(text)
        buffer.guess_segment_properties()
        hb.shape(font, buffer)
        return sum(p.x_advance for p in buffer.glyph_positions) / face.upem

    trim = FACE_SPACE - WORD_SPACE
    widest, where = 0.0, None
    for page in pages:
        # The two framed pages at the front are fitted to themselves and set larger, so they are
        # not part of the size the rest of the book shares.
        if len(page["l"]) != LINES_PER_PAGE:
            continue
        for number, line in enumerate(page["set"], 1):
            measured = ems(" ".join(line).translate(PRINTED)) - trim * (len(line) - 1)
            if measured > widest:
                widest, where = measured, (page["p"], number)
    print(f"  longest line: {widest:.2f} em, page {where[0]} line {where[1]}"
          f" — Setting.WIDEST should be {math.ceil(widest * 10) / 10:.1f}")


def main() -> None:
    print("mushaf layout")
    counts, words = word_counts()

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
        set_lines = []
        for _, _, kind, centred, begins, end, surah_number in lines:
            if kind == "surah_name":
                encoded.append(f"h{int(surah_number)}")
            elif kind == "basmallah":
                encoded.append("b")
            else:
                at = ids[int(end)]
                position = encode(at[0], at[1], at[2], counts[(at[0], at[1])])
                encoded.append(("c" if centred else "") + position)
                set_lines.append(set_line(int(begins), int(end), ids, words))
        pages.append(
            {
                "p": number,
                "s": encode(surah, ayah, index, counts[(surah, ayah)]),
                "l": encoded,
                "set": set_lines,
            }
        )

    asset = {
        "version": 2,
        "layout": "KFGQPC 15-line, V2 (1421H print), via the Quranic Universal Library",
        "pages": PAGES,
        "linesPerPage": LINES_PER_PAGE,
        "words": total,
        **divisions(),
        "page": pages,
    }

    report_widest(pages)
    for page in pages:
        del page["set"]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as out:
        json.dump(asset, out, ensure_ascii=False, separators=(",", ":"))
        out.write("\n")
    print(f"  wrote {OUT} ({os.path.getsize(OUT) / 1024:.0f} KB)")


ORDER: list[tuple[int, int]] = []

if __name__ == "__main__":
    main()
