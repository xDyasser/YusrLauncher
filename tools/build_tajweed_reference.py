#!/usr/bin/env python3
"""Writes the fixture the tajwīd engine is tested against.

`Tajweed.kt` works the rules of tajwīd out of the text on the device. Nothing checks that by
looking at it: the only honest test is to run it over the whole book and hold the answer against
somebody else's, letter for letter. This writes that somebody else's answer down.

The fixture is `app/src/test/resources/tajweed_reference.tsv.gz`, one line per ayah:

    surah <TAB> ayah <TAB> text <TAB> RULE:count,RULE:count,…

The text is the Uthmani Ḥafṣ edition the app itself downloads — the test needs the exact string
the engine will meet on a phone, and the book is not bundled in the APK. The counts are
cpfair/quran-tajweed's, an annotation of the same book built from the Dar al-Maʿrifah tajwīd
masaahif and the ReciteQuran tables, i.e. from the printed colour masaahif rather than from any
code in this repository. That independence is the whole value of it.

The two do not agree everywhere, and the test does not pretend they do. They are annotations of
two different printings: cpfair's positions are indices into a 2017 Tanzil text whose
orthography differs from this edition's, so the *positions* cannot be compared at all — only how
many of each rule each finds in each ayah. Where they still differ after that, it is because the
two conventions differ, and `TajweedTest` records the margin rather than hiding it: every rule
has a floor for how many āyāt must agree exactly, and the floors are the numbers this script
prints. Tightening the engine raises them; loosening it fails the build.

    python3 tools/build_tajweed_reference.py

Sources, both fetched over plain HTTPS:

  * https://github.com/fawazahmed0/quran-api — `ara-quranuthmanihaf`, the same edition
    `QuranDownloader` fetches at runtime.
  * https://github.com/cpfair/quran-tajweed — `tajweed.hafs.uthmani-pause-sajdah.json`.
"""

from __future__ import annotations

import collections
import gzip
import json
import os
import sys
import urllib.request

QURAN_URL = (
    "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/ara-quranuthmanihaf.json"
)
TAJWEED_URL = (
    "https://raw.githubusercontent.com/cpfair/quran-tajweed/master/output/"
    "tajweed.hafs.uthmani-pause-sajdah.json"
)

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "test", "resources", "tajweed_reference.tsv.gz",
)

AYAT = 6236

# cpfair's names for the rules against the enum in Tajweed.kt.
RULES = {
    "ghunnah": "GHUNNAH",
    "idghaam_ghunnah": "IDGHAAM_GHUNNAH",
    "idghaam_no_ghunnah": "IDGHAAM_NO_GHUNNAH",
    "idghaam_shafawi": "IDGHAAM_SHAFAWI",
    "idghaam_mutajanisayn": "IDGHAAM_MUTAJAANISAIN",
    "idghaam_mutaqaribayn": "IDGHAAM_MUTAQAARIBAIN",
    "ikhfa": "IKHFA",
    "ikhfa_shafawi": "IKHFA_SHAFAWI",
    "iqlab": "IQLAB",
    "qalqalah": "QALQALAH",
    "hamzat_wasl": "HAMZAT_WASL",
    "lam_shamsiyyah": "LAM_SHAMSIYYAH",
    "silent": "SILENT",
    "madd_2": "MADD_2",
    "madd_246": "MADD_246",
    "madd_muttasil": "MADD_MUTTASIL",
    "madd_munfasil": "MADD_MUNFASIL",
    "madd_6": "MADD_6",
}


def fetch(url: str) -> object:
    print(f"  fetching {url}")
    with urllib.request.urlopen(url) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    quran = fetch(QURAN_URL)["quran"]
    if len(quran) != AYAT:
        sys.exit(f"the edition has {len(quran)} āyāt, not {AYAT}")

    annotations = {
        (entry["surah"], entry["ayah"]): entry["annotations"] for entry in fetch(TAJWEED_URL)
    }
    if len(annotations) != AYAT:
        sys.exit(f"the annotation covers {len(annotations)} āyāt, not {AYAT}")

    totals: collections.Counter = collections.Counter()
    lines = []
    for verse in quran:
        key = (verse["chapter"], verse["verse"])
        found = annotations.get(key)
        if found is None:
            sys.exit(f"the annotation has nothing for {key[0]}:{key[1]}")

        counts: collections.Counter = collections.Counter()
        for annotation in found:
            rule = RULES.get(annotation["rule"])
            if rule is None:
                sys.exit(f"the annotation has a rule this app does not know: {annotation['rule']}")
            counts[rule] += 1
        totals.update(counts)

        text = verse["text"].strip()
        if "\t" in text or "\n" in text:
            sys.exit(f"{key[0]}:{key[1]} has a tab or a newline in it and would break the fixture")
        spelled = ",".join(f"{rule}:{n}" for rule, n in sorted(counts.items()))
        lines.append(f"{key[0]}\t{key[1]}\t{text}\t{spelled}")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    body = ("\n".join(lines) + "\n").encode("utf-8")
    with gzip.open(OUT, "wb", compresslevel=9) as out:
        out.write(body)

    print(f"  wrote {OUT} ({os.path.getsize(OUT) / 1024:.0f} KB, {len(lines)} āyāt)")
    print(f"  {sum(totals.values())} annotations across {len(totals)} rules:")
    for rule, count in sorted(totals.items(), key=lambda item: -item[1]):
        print(f"    {rule:24} {count}")


if __name__ == "__main__":
    main()
