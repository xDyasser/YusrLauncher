#!/usr/bin/env python3
"""
Turns an Arabic edition of Mafātīḥ al-Jinān into the assets the app reads.

The source is a Word file of the Arabic ‏مفاتيح الجنان‎ — the muḥaqqaq edition, 904 pages,
al-Qummī's book with the editor's apparatus around it. It replaces the Persian scrape this script
used to read. That scrape was an edition of the same book made for Persian readers: the
supplications in it were Arabic, as they are in any edition, but the compiler's guidance and the
headings were Persian, so both had to be thrown away and the 280 titles invented in their place.
None of that is true any more. The book here is Arabic throughout, so the book's own words are
what comes out — its headings as it prints them, and its guidance beside the supplications
instead of a silence where the guidance was.

What this reads and how:

  * `wvHtml` converts the Word file. It is used rather than a text extractor because the styles
    are the structure: `Heading 1` is a bāb, `Heading 2` a faṣl, `Heading 3` a text within one,
    and a heading that runs over two or three lines is two or three paragraphs of the same style
    in a row, joined back here. Nothing about the outline is guessed from the writing.
  * The editor's apparatus is dropped: footnotes (`rfdFootnote0`), the rules above them, the
    printed table of contents and the running feet. What is left is the book.
  * The supplications are told from the prose around them by a diacritic, not by a style: every
    supplication in this edition is fully vocalised and al-Qummī's guidance is not, so a
    paragraph more than a third vowelled is the text and the rest is the note beside it. The
    centred basmala, which is set without vowels, is the one thing the count would get wrong,
    and it is caught by its style instead.
  * Typesetting is undone: the zero-width non-joiner that holds ‏عليه‌السلام‎ together becomes the
    space it stands for, the joiner inside ‏شي‌ء‎ goes, and the marks that only steer the line
    direction go with it. None of that is a variant reading.
  * The book ends where its appendices do. The same volume prints al-Qummī's *al-Bāqiyāt
    al-Ṣāliḥāt* after it, a second book with a second set of bābs; it is left alone.
  * It is split. One 5 MB asset would be parsed in full to read four lines of a taʿqīb, so the
    book becomes an index of what is in it and one file per text, opened when that text is.

The English titles below are a second, shorter list, covering only the texts that have a settled
English name. A made-up English title on a supplication would be worse than none, so the rest
show their Arabic — which is now the book's own.

    python3 tools/import_mafatih.py mafatihaljanan.doc app/src/main/assets/mafatih
"""

import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

# Texts whose names are settled in English, keyed by the book's own heading. Everything else is
# shown in Arabic: these are the ones a reader would go looking for by name.
ENGLISH_TITLES = {
    "الفصل الأول: في التعقيبات العامّة عَن كتاب (مصباح المُتهجّد) وغيره": "Taʿqībāt · after every prayer",
    "تعقيب صلاة العصر ـ نقلا عَن المُتهجد": "Taʿqīb of ʿasr",
    "تعقيب صلاة المغرب ـ عن مصباح المُتهجد": "Taʿqīb of maghrib",
    "تعقيب صلاة العشاء ـ نقلا عن المتهجد": "Taʿqīb of ʿishāʾ",
    "تعقيب صلاة الصبح ـ عن مصباح المتهجد": "Taʿqīb of fajr",
    "((دُعاء يوم الأحد))": "Sunday",
    "دُعاء يوم الاثنين": "Monday",
    "دُعاء يوم الثلاثاء": "Tuesday",
    "دُعاء يوم الأربعاء": "Wednesday",
    "دُعاء يوم الخميس": "Thursday",
    "دُعاء يوم الجمعة": "Friday",
    "دُعاء يوم السبت": "Saturday",
    "امّا أعمال ليلة الجُمعة فكثيرة وهنا نقتصر على عدّة منها": "Thursday night",
    "واما اعمال نهار الجُمعة فكثيرة ونحن هنا نقتصر على عدةٍ منها": "Friday",
    "دُعاءُ الصَّباح لأمير المؤمنين عليه السلام": "Duʿāʾ al-Ṣabāḥ",
    "دُعاء كميل بن زياد (رض)": "Duʿāʾ Kumayl",
    "دُعاء العشرات": "Duʿāʾ al-ʿAsharāt",
    "دعاء السمات": "Duʿāʾ al-Simāt",
    "دعاء المشلول": "Duʿāʾ al-Mashlūl",
    "الدعاء المعروف بـ يستشير": "Duʿāʾ al-Yastashīr",
    "دعاء المجير": "Duʿāʾ al-Mujīr",
    "دعاء العديلة": "Duʿāʾ al-ʿAdīla",
    "دعاء الجوشن الكبير": "Al-Jawshan al-Kabīr",
    "دعاء الجوشن الصغير": "Al-Jawshan al-Ṣaghīr",
    "المناجاة الأولى : مناجاة التائبين": "Munājāt 1 · of the repentant",
    "المناجاة الثانية : مناجاة الشاكين": "Munājāt 2 · of the complainants",
    "المناجاة الثالثة : مناجاة الخائفين": "Munājāt 3 · of the fearful",
    "الرابعة : مناجاة الراجين": "Munājāt 4 · of the hopeful",
    "الخامسة : مناجاة الراغبين": "Munājāt 5 · of the devoted",
    "السادسة : مناجاة الشاكرين": "Munājāt 6 · of the thankful",
    "السابعة : مناجاة المطيعين للّه": "Munājāt 7 · of the obedient",
    "الثامنة : مناجاة المريدين": "Munājāt 8 · of the willing",
    "التاسعة : مناجاة المحبين": "Munājāt 9 · of the lovers",
    "العاشرة : مناجاة المتوسلين": "Munājāt 10 · of those seeking intercession",
    "الحادية عشرة : مناجاة المفتقرين": "Munājāt 11 · of the needy",
    "الثانية عشرة : مناجاة العارفين": "Munājāt 12 · of the knowers",
    "الثالثة عشرة : مناجاة الذاكرين": "Munājāt 13 · of those who remember",
    "الرابعة عشرة : مناجاة المعتصمين": "Munājāt 14 · of those seeking refuge",
    "الخامسة عشرة : مناجاة الزاهدين": "Munājāt 15 · of the abstinent",
    "الفصل الأوّل: في فضل شهر رجب وأعماله": "Rajab",
    "الفصل الثاني: في فضل شهر شعبان والأعمال الواردة فيه الأعمال العامة": "Shaʿbān",
    "الفصلُ الثّالثُ: في فَضلِ شَهرِ رَمَضان وأعمالهِ خطبة النبي صلى الله عليه وآله": "Ramaḍān · the Prophet's sermon",
    "القسم الثاني: مايُستَحب اتيانه في لَيالي شَهر رَمَضان": "Duʿāʾ al-Iftitāḥ",
    "القسم الثالث: في أعمال أسحار شَهر رَمَضان المبارك": "Duʿāʾ Abū Ḥamza al-Thumālī",
    "الفصل الرابع: في أعمال شَهر شّوال": "Shawwāl and ʿĪd al-Fiṭr",
    "الفصل الخامس: في أعمال شهر ذي القعدة": "Dhū al-Qaʿda",
    "الفصل السادس: في أعمال شهر ذي الحجة": "Dhū al-Ḥijja · ʿArafa and Ghadīr",
    "الفصل السابع: في أعمال شهر محرّم": "Muḥarram and ʿĀshūrāʾ",
    "الفصل الثامن: في أعمال شهر صفر": "Ṣafar",
    "الفصل التاسع: في شهر ربيع الأول": "Rabīʿ al-Awwal",
    "الفصل العاشر: في شهر ربيع الثاني ، وجمادى الأولى ، وجمادى الآخِرة": "Rabīʿ al-Thānī, Jumādā I and II",
    "الفصل الحادي عشر: في اعمال عامة الشهور ، وأعمال النيروز ، وأعمال الأشهر الرومية": "Every month, and Nawrūz",
    "الزيارة الثانية": "Ziyārat Amīn Allāh",
    "السابعة : زيارة عاشوراء": "Ziyārat ʿĀshūrāʾ",
    "الثامنة : زيارة الأربعين": "Ziyārat al-Arbaʿīn",
    "المقام الأول: في الزيارات الجامعة": "Al-Ziyāra al-Jāmiʿa al-Kabīra",
    "الصلاة عليه عليه السلام": "Duʿāʾ al-Nudba and Duʿāʾ al-ʿAhd",
    "دعاء مكارم الأخلاق": "Duʿāʾ Makārim al-Akhlāq",
    "حديث الكساء": "Ḥadīth al-Kisāʾ",
}

# The styles the editor's apparatus is set in. None of it is the book.
APPARATUS = ("rfdFootnote", "rfdLine", "rfdPoemFootnote", "TOC", "Footer")

# A footnote that ran past the foot of its page comes back in the style the body continues in,
# still numbered — «2 ـ محي ـ خ ـ» — and would otherwise land in the middle of a supplication,
# cutting it in two. A numbered line in that style is the note, not the book. The body's own
# numbered lists are set in `Normal` and are left alone.
STRAY_FOOTNOTE = re.compile(r"^\d+\s*ـ")
CONTINUED = "rfdNormal0"

# Styles that are the supplication whatever the vowels say. The centred basmala is printed bare.
ALWAYS_ARABIC = ("rfdCenterBold1",)

# Where a bāb, a faṣl and a text begin.
HEADINGS = {"Heading 1": 1, "Heading 2": 2, "Heading 3": 3}

# A heading that opens with one of these and little else is a label — «الفصل الثاني» — and the
# line under it says what the faṣl is about. The two are printed as one here.
LABEL = re.compile(r"^(الباب|الفصل|الفَصل|المقصد|المطلب|المقام|القسم|الخاتمة|خاتمة|المقدمة|مقدمة|ملحق|الملحق)\b")

DIACRITICS = re.compile(r"[ً-ْٰ]")
ARABIC_LETTERS = re.compile(r"[ء-ي]")

# Where al-Qummī's other book starts in the same volume, on its epigraph.
BAQIYAT = "والباقيات الصالحات خير"


def convert(path):
    """The Word file as HTML, with the paragraph styles still on it."""
    if not shutil.which("wvHtml"):
        sys.exit("wvHtml is not installed: apt-get install wv")
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "book.html")
        subprocess.run(
            ["wvHtml", "--charset=utf-8", path, out],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        with open(out, encoding="utf-8", errors="replace") as f:
            return f.read()


def tidy(text):
    """One paragraph, with the typesetting taken back out of it."""
    text = re.sub(r"<sup>.*?</sup>", "", text, flags=re.S)  # footnote markers
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = text.replace("‌", " ")  # عليه‌السلام, held together for the line breaker
    text = text.replace("‍", "").replace("‎", "").replace("‏", "")
    text = text.replace("\xa0", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def paragraphs(page):
    """The book's paragraphs, in order, each with the style it is set in."""
    found = re.findall(r'<p><div name="([^"]*)"[^>]*>(.*?)</div>', page, flags=re.S)
    out = []
    for style, raw in found:
        if style.startswith(APPARATUS):
            continue
        text = tidy(raw)
        if not text or (style == CONTINUED and STRAY_FOOTNOTE.match(text)):
            continue
        out.append((style, text))
    return out


def mafatih_only(paras):
    """The book, without the editor's introduction in front or the second book behind."""
    first = next(i for i, (style, _) in enumerate(paras) if style.startswith("Heading 1"))
    last = next(i for i, (_, text) in enumerate(paras) if BAQIYAT in text)
    return paras[first:last]


def heading_level(style):
    return next((depth for name, depth in HEADINGS.items() if style.startswith(name)), 0)


def split_headings(lines):
    """
    Consecutive heading lines of one style, cut back into the headings they were.

    A heading that runs over three lines and a faṣl immediately followed by its first maqṣad look
    the same to the styles: several paragraphs of one style in a row. What tells them apart is
    that a new heading names itself — «المقام الأول» — and a continuation does not, so a labelled
    line opens a heading of its own once the one before it has been said in full.
    """
    groups = []
    for line in lines:
        line = line.strip(" :ـ*")
        if not line:
            continue
        opens = LABEL.match(line) and groups and any(not LABEL.match(l) for l in groups[-1])
        if opens or not groups:
            groups.append([])
        groups[-1].append(line)
    return groups


def join_heading(lines):
    """A heading printed over several lines, as one line."""
    if not lines:
        return ""
    if len(lines) > 1 and LABEL.match(lines[0]) and len(lines[0].split()) <= 3:
        return lines[0] + ": " + " ".join(lines[1:])
    return " ".join(lines)


def is_arabic_text(style, text):
    """Whether this paragraph is the supplication or the compiler talking about it."""
    if style.startswith(ALWAYS_ARABIC):
        return True
    letters = len(ARABIC_LETTERS.findall(text))
    return letters > 0 and len(DIACRITICS.findall(text)) / letters > 0.33


def outline(paras):
    """The book as its styles have it: a flat run of headings and the paragraphs under each."""
    nodes = []
    index = 0
    while index < len(paras):
        depth = heading_level(paras[index][0])
        if depth:
            lines = []
            while index < len(paras) and heading_level(paras[index][0]) == depth:
                lines.append(paras[index][1])
                index += 1
            for group in split_headings(lines):
                nodes.append({"depth": depth, "title": join_heading(group), "paras": []})
        else:
            if not nodes:  # nothing precedes the first bāb, but do not lose it if it ever does
                nodes.append({"depth": 3, "title": "", "paras": []})
            nodes[-1]["paras"].append(paras[index])
            index += 1
    return nodes


def structure(nodes):
    """Bābs holding fuṣūl holding texts. What sits above a heading opens the part it is under."""
    chapters = []
    chapter = section = None
    for node in nodes:
        if node["depth"] == 1:
            chapter = {"title": node["title"], "sections": []}
            chapters.append(chapter)
            section = None
            if node["paras"]:
                section = {"title": node["title"], "items": []}
                chapter["sections"].append(section)
                section["items"].append({"title": node["title"], "paras": node["paras"]})
        elif node["depth"] == 2:
            section = {"title": node["title"], "items": []}
            chapter["sections"].append(section)
            if node["paras"]:
                section["items"].append({"title": node["title"], "paras": node["paras"]})
        else:
            if section is None:
                section = {"title": chapter["title"], "items": []}
                chapter["sections"].append(section)
            section["items"].append({"title": node["title"], "paras": node["paras"]})
    return [c for c in chapters if any(s["items"] for s in c["sections"])]


def is_preamble(blocks):
    """
    A line of the book that is not a text to open.

    A bāb often opens by saying what is in it — «ويحتوي على عدة فصول» — or by handing straight
    over to the faṣl under it — «مِن تِلكَ الدَّعوات» — and the styles put that under the heading
    like anything else. A line of it is not an entry a reader should be able to tap. Two
    sentences are not one either unless a supplication is in them; the prose that is the book, a
    faḍl or the ādāb of a ziyāra, is longer than that and stays.
    """
    length = sum(len(b["t"]) for b in blocks)
    return length < 60 or (length < 300 and not any(b["k"] == "a" for b in blocks))


def blocks_of(paras):
    """A text as the app reads it: runs of supplication, runs of the note beside them."""
    blocks = []
    for style, text in paras:
        kind = "a" if is_arabic_text(style, text) else "n"
        if blocks and blocks[-1]["k"] == kind:
            blocks[-1]["t"] += "\n" + text
        else:
            blocks.append({"k": kind, "t": text})
    return blocks


def write(chapters, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for stale in os.listdir(out_dir):
        if re.fullmatch(r"m\d+\.json", stale):
            os.remove(os.path.join(out_dir, stale))

    index = {
        "title": "Mafātīḥ al-Jinān",
        "arabicTitle": "مفاتيح الجنان",
        "attribution": "Shaykh ʿAbbās al-Qummī · complete",
        "source": "The Arabic edition, its own headings and its own notes. Footnotes are not here.",
        "chapters": [],
    }

    number = 0
    for chapter in chapters:
        sections = []
        for section in chapter["sections"]:
            items = []
            for item in section["items"]:
                blocks = blocks_of(item["paras"])
                if not blocks or is_preamble(blocks):
                    continue
                number += 1
                entry_id = "m%03d" % number
                arabic = sum(len(b["t"]) for b in blocks if b["k"] == "a")
                with open(os.path.join(out_dir, entry_id + ".json"), "w", encoding="utf-8") as f:
                    json.dump({"id": entry_id, "blocks": blocks}, f, ensure_ascii=False)
                entry = {"id": entry_id, "title": item["title"], "arabic": arabic}
                english = ENGLISH_TITLES.get(item["title"])
                if english:
                    entry["english"] = english
                items.append(entry)
            if items:
                sections.append({"title": section["title"], "items": items})
        if sections:
            index["chapters"].append({"title": chapter["title"], "sections": sections})

    with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    return number


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__.strip().splitlines()[-1].strip())
    paras = mafatih_only(paragraphs(convert(sys.argv[1])))
    chapters = structure(outline(paras))

    # The first bāb is printed under its subject alone; the table of contents is where the book
    # numbers it. The other two number themselves and are left as they are.
    if not chapters[0]["title"].startswith("الباب"):
        chapters[0]["title"] = "الباب الأول: " + chapters[0]["title"]

    count = write(chapters, sys.argv[2])
    named = sum(
        1 for c in chapters for s in c["sections"] for i in s["items"]
        if i["title"] in ENGLISH_TITLES
    )
    print("%d bābs, %d parts, %d texts, %d of them named in English"
          % (len(chapters), sum(len(c["sections"]) for c in chapters), count, named))


if __name__ == "__main__":
    main()
