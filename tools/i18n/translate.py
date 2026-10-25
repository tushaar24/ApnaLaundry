#!/usr/bin/env python3
"""
Makes the app's language catalogues (app/src/main/assets/i18n/<code>.json).

Collects every tr("…") / i18n("…") English text from the Kotlin code, and has
Sarvam AI (sarvam-105b-conversations) translate the ones a catalogue doesn't
have yet, in batches. A translation that loses or changes a {placeholder}, or
isn't in the language's script, is dropped — that text shows in English.
Texts no longer in the code are removed from the catalogues.

    SARVAM_API_KEY=… python3 tools/i18n/translate.py           # translate what's missing
    SARVAM_API_KEY=… python3 tools/i18n/translate.py --only hi  # one language
    python3 tools/i18n/translate.py --check                     # list tr() problems, no network

See docs/i18n.md.
"""
import argparse
import concurrent.futures as cf
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java"
OUT = ROOT / "app/src/main/assets/i18n"

# code -> (language, script, unicode range of the script)
LANGS = {
    "hi": ("Hindi", "Devanagari", (0x0900, 0x097F)),
    "mr": ("Marathi", "Devanagari", (0x0900, 0x097F)),
    "gu": ("Gujarati", "Gujarati", (0x0A80, 0x0AFF)),
    "bn": ("Bengali", "Bengali", (0x0980, 0x09FF)),
    "ta": ("Tamil", "Tamil", (0x0B80, 0x0BFF)),
    "te": ("Telugu", "Telugu", (0x0C00, 0x0C7F)),
    "kn": ("Kannada", "Kannada", (0x0C80, 0x0CFF)),
    "ml": ("Malayalam", "Malayalam", (0x0D00, 0x0D7F)),
    "pa": ("Punjabi", "Gurmukhi", (0x0A00, 0x0A7F)),
    "or": ("Odia", "Odia", (0x0B00, 0x0B7F)),
}

# tr("…") / i18n("…") — also written in full (core.tr("…")) — the literal possibly on the next line.
CALL = re.compile(r'(?<![\w])(tr|i18n)\(\s*"((?:[^"\\\n]|\\.)*)"')
PLACEHOLDER = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")
ESCAPES = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "'": "'", "\\": "\\", "$": "$", "b": "\b"}


def unescape(lit: str) -> str:
    out, i = [], 0
    while i < len(lit):
        c = lit[i]
        if c == "\\" and i + 1 < len(lit):
            n = lit[i + 1]
            if n == "u" and i + 5 < len(lit):
                out.append(chr(int(lit[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(ESCAPES.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def collect():
    """{english: [file:line, …]}, and problems ($ templates inside a literal)."""
    keys, problems = {}, []
    for f in sorted(SRC.rglob("*.kt")):
        text = f.read_text(encoding="utf-8")
        for m in CALL.finditer(text):
            lit = m.group(2)
            line = text.count("\n", 0, m.start()) + 1
            where = f"{f.relative_to(ROOT)}:{line}"
            # An unescaped $ means a Kotlin template: the runtime text never matches the catalogue.
            if re.search(r"(?<!\\)\$[{A-Za-z]", lit):
                problems.append(f"{where}: template inside {m.group(1)}(\"{lit}\") — use a {{placeholder}}")
                continue
            s = unescape(lit)
            if s.strip():
                keys.setdefault(s, []).append(where)
    return keys, problems


def prompt(lang, script):
    return f"""You translate the screen text of "MyLaundry", an Android app that small laundry and dry-cleaning shop owners in India (tier 2-3 towns) use every day: take orders, mark them ready / delivered, keep each customer's khata (credit), collect cash or UPI, and send bills and updates to customers on WhatsApp.

Translate every value into {lang}, written in {script} script.
- Use simple everyday words shop owners actually speak — not formal, bookish or Sanskritized words. Short and clear.
- Common English words people use as they are (order, bill, cash, UPI, WhatsApp, plan, app, kg, GST, PDF, QR, OTP, mic, service, khata) may stay as loanwords, written in {script} script. "Baaki" = money a customer still owes; "khata" = their credit account — use the usual local words.
- Keep every {{placeholder}} exactly as written (same name, inside braces) — it is filled in by the app (a name, amount, date, number…). Keep ₹, numbers, emoji, "#", "·", "—", "→", "✓", "*" and line breaks.
- Button labels, tabs and titles must stay short. Messages to customers (they start like "Hi {{name}}, …") stay polite and friendly.
- Brand names stay: MyLaundry, WhatsApp, GPay, PhonePe, Paytm, Razorpay.
Return ONLY a JSON object with the same keys and the translated values."""


def call_sarvam(key, system, payload, tries=4):
    body = json.dumps({
        "model": "sarvam-105b-conversations", "temperature": 0.1, "max_tokens": 6000,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}],
    }).encode()
    for attempt in range(tries):
        try:
            req = urllib.request.Request("https://api.sarvam.ai/v1/chat/completions", body,
                                         {"api-subscription-key": key, "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=120) as r:
                content = json.load(r)["choices"][0]["message"]["content"] or ""
            m = re.search(r"\{.*\}", content, re.S)
            return json.loads(m.group(0)) if m else {}
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as e:
            wait = 3 * (attempt + 1) * (4 if isinstance(e, urllib.error.HTTPError) and e.code == 429 else 1)
            print(f"    retry in {wait}s ({e})", file=sys.stderr)
            time.sleep(wait)
    return {}


def ok(src, dst, rng):
    if not isinstance(dst, str) or not dst.strip():
        return False
    if sorted(PLACEHOLDER.findall(src)) != sorted(PLACEHOLDER.findall(dst)):
        return False
    letters = re.sub(PLACEHOLDER, "", src)
    if sum(ch.isalpha() for ch in letters) >= 3 and dst != src:
        # Something in the target script (a pure loanword like "UPI" may stay as it is).
        if not any(rng[0] <= ord(ch) <= rng[1] for ch in dst):
            return False
    return len(dst) <= max(40, 5 * len(src))


def translate_lang(code, keys, key, batch=40, workers=6):
    lang, script, rng = LANGS[code]
    path = OUT / f"{code}.json"
    have = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    have = {k: v for k, v in have.items() if k in keys}  # drop texts gone from the code
    todo = [k for k in keys if k not in have]
    print(f"{code}: {len(have)} kept, {len(todo)} to translate")
    system = prompt(lang, script)

    def run(chunk):
        payload = {str(i): s for i, s in enumerate(chunk)}
        got = call_sarvam(key, system, payload)
        good, bad = {}, []
        for i, s in enumerate(chunk):
            d = got.get(str(i))
            (good.__setitem__(s, d) if ok(s, d, rng) else bad.append(s))
        return good, bad

    chunks = [todo[i:i + batch] for i in range(0, len(todo), batch)]
    failed = []
    with cf.ThreadPoolExecutor(workers) as ex:
        for good, bad in ex.map(run, chunks):
            have.update(good)
            failed += bad
    # One more go, in small batches, for what didn't pass.
    if failed:
        retry = [failed[i:i + 8] for i in range(0, len(failed), 8)]
        failed = []
        with cf.ThreadPoolExecutor(workers) as ex:
            for good, bad in ex.map(run, retry):
                have.update(good)
                failed += bad
    # Hand corrections (tools/i18n/overrides.json) always win.
    over = json.loads((Path(__file__).parent / "overrides.json").read_text(encoding="utf-8")).get(code, {})
    for k, v in over.items():
        if k in keys:
            have[k] = v
    OUT.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(dict(sorted(have.items())), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{code}: {len(have)}/{len(keys)} translated" + (f", {len(failed)} left in English" if failed else ""))
    return failed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="list tr() problems and counts only")
    ap.add_argument("--only", help="comma-separated language codes")
    args = ap.parse_args()

    keys, problems = collect()
    print(f"{len(keys)} texts in tr()/i18n()")
    for p in problems:
        print("PROBLEM", p)
    if args.check:
        sys.exit(1 if problems else 0)
    key = os.environ.get("SARVAM_API_KEY")
    if not key:
        sys.exit("Set SARVAM_API_KEY")
    codes = args.only.split(",") if args.only else list(LANGS)
    for code in codes:
        left = translate_lang(code, list(keys), key)
        for s in left[:15]:
            print(f"  {code} kept English: {s!r}")


if __name__ == "__main__":
    main()
