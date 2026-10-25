"""Transcribe support-call recordings with Sarvam's batch speech-to-text (saaras:v4, 2-speaker diarization).

    .venv/bin/python tools/sarvam/transcribe_calls.py tools/sarvam/data/audio tools/sarvam/data/transcripts

Sends 20 files per job (the API limit), skips recordings that already have a transcript, and writes
one JSON per recording (transcript, diarized_transcript, language_code) into the output folder.
Reads SARVAM_API_KEY from the environment or the repo's .env (never printed).
"""
import os
import sys
from pathlib import Path

from sarvamai import SarvamAI

from first_call import load_env

KEYTERMS = ["MyLaundry", "subscription", "AutoPay", "UPI", "khata", "bill", "Play Store", "499"]
BATCH = 20


def main():
    load_env()
    audio_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    done = {p.stem.split(".")[0] for p in out_dir.glob("*.json")}
    todo = sorted(p for p in audio_dir.iterdir() if p.is_file() and p.stem not in done)
    print(f"{len(todo)} to transcribe, {len(done)} already done", flush=True)
    client = SarvamAI(api_subscription_key=os.environ["SARVAM_API_KEY"])
    for i in range(0, len(todo), BATCH):
        files = todo[i:i + BATCH]
        job = client.speech_to_text_job.create_job(
            model="saaras:v4", language_code="unknown", with_diarization=True, num_speakers=2, keyterms=KEYTERMS,
        )
        job.upload_files(file_paths=[str(f) for f in files])
        job.start()
        job.wait_until_complete()
        results = job.get_file_results()
        for f in results["failed"]:
            print(f"  failed {f['file_name']}: {f['error_message']}", flush=True)
        if results["successful"]:
            job.download_outputs(output_dir=str(out_dir))
        print(f"job {job.job_id}: {len(results['successful'])} ok, {len(results['failed'])} failed", flush=True)


if __name__ == "__main__":
    main()
