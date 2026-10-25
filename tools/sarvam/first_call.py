"""First Sarvam AI call: transcribe one short (≤30 s) audio clip with the sync speech-to-text API.

    .venv/bin/python tools/sarvam/first_call.py path/to/clip.mp3

Reads SARVAM_API_KEY from the environment or the repo's .env (never printed).
"""
import os
import sys
from pathlib import Path

from sarvamai import SarvamAI


def load_env(path=Path(__file__).resolve().parents[2] / ".env"):
    if path.exists():
        for line in path.read_text().splitlines():
            key, sep, value = line.partition("=")
            if sep and key.strip() and not key.startswith("#"):
                os.environ.setdefault(key.strip(), value.strip())


def main():
    load_env()
    client = SarvamAI(api_subscription_key=os.environ["SARVAM_API_KEY"])
    with open(sys.argv[1], "rb") as f:
        r = client.speech_to_text.transcribe(file=f, model="saaras:v4", language_code="unknown")
    print(f"language: {r.language_code} ({r.language_probability})")
    print(r.transcript)


if __name__ == "__main__":
    main()
