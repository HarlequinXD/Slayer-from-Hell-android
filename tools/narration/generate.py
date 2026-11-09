#!/usr/bin/env python3
"""Records Slayer's narration of the story with OpenAI's expressive text-to-speech (gpt-4o-mini-tts).

Reads every chapter from Story.java, splits it into paragraphs exactly as Narrator.java does, and records each
paragraph to app/src/main/assets/narration/<crc32 of the paragraph>.mp3 (mono, 24 kHz, 48 kb/s). The game finds a
paragraph's recording by that name, so editing a paragraph simply makes it get re-recorded on the next run; clips for
text that no longer exists are deleted. Run by the "Narrate the story" GitHub workflow, with OPENAI_API_KEY set.

    python3 tools/narration/generate.py [--voice marin] [--force] [--only CHAPTER_INDEX]
"""
import argparse, json, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.request, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STORY = os.path.join(ROOT, "app/src/main/java/com/harlequin/slayer/Story.java")
OUT = os.path.join(ROOT, "app/src/main/assets/narration")
INDEX = os.path.join(OUT, "index.json")
MODEL = "gpt-4o-mini-tts"
PUNCH = 90  # as Narrator.PUNCH: short paragraphs are the punchlines

DIRECTION = """Voice: Slayer, an ancient demon warden - a woman - telling her own story as the voice-over of a dark \
fantasy movie trailer. Low, husky and controlled, with gravel at the edges of the voice.
Tone: weary, bitter and dangerous. Ten thousand years of fury held under a calm surface.
Emotion: follow the meaning of every line - contempt for those who betrayed her, cold grief, dread, quiet menace, \
grim dark humour where it is there. Real feeling, never cheerful, never shouting or hammy.
Pacing: slow and deliberate. Weighty pauses at full stops and line breaks, and a beat before the words that matter. \
Let short sentences land like hammer blows.
Pronunciation: clear and crisp, every word distinct."""


def chapters():
    src = open(STORY, encoding="utf-8").read()
    body = src[src.index("CHAPTERS={"):]
    starts = [m.start() for m in re.finditer(r"new Chapter\(", body)] + [len(body)]
    out = []
    for a, b in zip(starts, starts[1:]):
        lits = [json.loads('"' + s + '"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"', body[a:b])]
        if len(lits) >= 3:
            out.append((lits[0], lits[1], "".join(lits[2:])))
    return out


def paragraphs(text):
    return [p for p in text.split("\n") if p.strip()]


def clip_name(paragraph):
    return "%08x.mp3" % (zlib.crc32(paragraph.encode("utf-8")) & 0xFFFFFFFF)


def direction(title, heading, para, i, n):
    d = DIRECTION + f"\nScene: {title.title()}, \"{heading}\" - paragraph {i + 1} of {n}."
    if len(para) <= PUNCH:
        d += "\nThis is a punchline: deliver it slowly, low and cold, and let it hang in the silence after."
    if i == 0:
        d += "\nThis opens the chapter: draw the listener in, quiet and heavy."
    if i == n - 1:
        d += "\nThis closes the chapter: end on a dark, resolute note."
    if '"' in para or " said" in para or " says" in para:
        d += "\nWhen she repeats what others said, colour those words with how she feels about them."
    return d


def speak(text, instructions, voice, key, wav_path):
    req = urllib.request.Request(
        "https://api.openai.com/v1/audio/speech",
        data=json.dumps({"model": MODEL, "voice": voice, "input": text,
                         "instructions": instructions, "response_format": "wav"}).encode(),
        headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=180) as r, open(wav_path, "wb") as f:
                f.write(r.read())
            return
        except urllib.error.HTTPError as e:
            msg = e.read().decode("utf-8", "replace")[:400]
            if e.code in (429, 500, 502, 503, 504) and attempt < 4:
                time.sleep(5 * 2 ** attempt); continue
            sys.exit(f"OpenAI said {e.code}: {msg}")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < 4:
                time.sleep(5 * 2 ** attempt); continue
            sys.exit(f"Couldn't reach OpenAI: {e}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="marin")
    ap.add_argument("--force", action="store_true", help="re-record every paragraph")
    ap.add_argument("--only", type=int, help="only this chapter (0 = prologue)")
    a = ap.parse_args()
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        sys.exit("No OPENAI_API_KEY. Add it under the repo's Settings > Secrets and variables > Actions.")
    os.makedirs(OUT, exist_ok=True)
    index = json.load(open(INDEX)) if os.path.exists(INDEX) else {}
    wanted, todo = set(), []
    chs = chapters()
    for c, (title, heading, text) in enumerate(chs):
        ps = paragraphs(text)
        for i, p in enumerate(ps):
            name = clip_name(p); wanted.add(name)
            have = os.path.exists(os.path.join(OUT, name)) and index.get(name, {}).get("voice") == a.voice
            if (a.only is None or a.only == c) and (a.force or not have):
                todo.append((c, title, heading, p, i, len(ps), name))
    print(f"{len(chs)} chapters, {len(wanted)} paragraphs, {len(todo)} to record with voice '{a.voice}'")
    with tempfile.TemporaryDirectory() as tmp:
        for k, (c, title, heading, p, i, n, name) in enumerate(todo):
            print(f"[{k + 1}/{len(todo)}] {title} ¶{i + 1}: {p[:60]}...", flush=True)
            wav = os.path.join(tmp, "p.wav")
            speak(p, direction(title, heading, p, i, n), a.voice, key, wav)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-ar", "24000",
                            "-codec:a", "libmp3lame", "-b:a", "48k", os.path.join(OUT, name)], check=True)
            index[name] = {"voice": a.voice, "model": MODEL, "chapter": c, "paragraph": i}
            json.dump(index, open(INDEX, "w"), indent=1, sort_keys=True)   # keep progress if a later one fails
    if a.only is None:
        for f in os.listdir(OUT):
            if f.endswith(".mp3") and f not in wanted:
                os.remove(os.path.join(OUT, f)); index.pop(f, None); print("removed stale", f)
    json.dump({k: v for k, v in index.items() if k in wanted or a.only is not None}, open(INDEX, "w"), indent=1, sort_keys=True)
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT) if f.endswith(".mp3"))
    print(f"done: {len([f for f in os.listdir(OUT) if f.endswith('.mp3')])} clips, {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
