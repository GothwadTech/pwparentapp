#!/usr/bin/env python3
"""Extract still frames (and optionally the audio track) from a video file.

Why: an agent can read images but not video streams, so a screen recording is turned into a
handful of PNG frames that can be inspected, OCR-ish read by eye, and transcribed into a data
file (CSV/MD/JSON).

Usage:
    python3 tools/video_extract.py input.mp4                # 1 frame / 2s  -> frames/
    python3 tools/video_extract.py input.mp4 --every 1      # 1 frame / second
    python3 tools/video_extract.py input.mp4 --max-frames 60 --out /tmp/frames
    python3 tools/video_extract.py input.mp4 --audio        # also writes audio.m4a

Needs ffmpeg. If the system has none, `pip install imageio-ffmpeg` provides a static build
(pure-Python wheel, no root required).
"""
import argparse
import os
import shutil
import subprocess
import sys


def find_ffmpeg() -> str:
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    try:
        import imageio_ffmpeg  # noqa: WPS433 (optional dependency)

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        sys.exit(
            "ffmpeg not found. Install it, or run: pip install imageio-ffmpeg"
        )


def probe_duration(ffmpeg: str, path: str) -> float:
    """Return the video duration in seconds (0.0 when it cannot be determined)."""
    proc = subprocess.run(
        [ffmpeg, "-i", path],
        capture_output=True,
        text=True,
        errors="replace",
    )
    for line in proc.stderr.splitlines():
        line = line.strip()
        if line.startswith("Duration:"):
            stamp = line.split("Duration:")[1].split(",")[0].strip()
            parts = stamp.split(":")
            try:
                hours, minutes, seconds = (float(p) for p in parts)
                return hours * 3600 + minutes * 60 + seconds
            except ValueError:
                return 0.0
    return 0.0


def extract_frames(ffmpeg: str, src: str, out_dir: str, every: float, max_frames: int) -> list[str]:
    os.makedirs(out_dir, exist_ok=True)
    pattern = os.path.join(out_dir, "frame_%04d.png")
    cmd = [
        ffmpeg, "-hide_banner", "-loglevel", "error",
        "-i", src,
        "-vf", f"fps=1/{every}",
        "-frames:v", str(max_frames),
        pattern,
    ]
    subprocess.run(cmd, check=True)
    return sorted(os.path.join(out_dir, f) for f in os.listdir(out_dir) if f.endswith(".png"))


def extract_audio(ffmpeg: str, src: str, out_dir: str) -> str | None:
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "audio.m4a")
    cmd = [
        ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
        "-i", src, "-vn", "-acodec", "aac", "-b:a", "96k", out,
    ]
    try:
        subprocess.run(cmd, check=True)
        return out
    except subprocess.CalledProcessError:
        return None  # no audio track


def main() -> None:
    parser = argparse.ArgumentParser(description="Split a video into inspectable frames.")
    parser.add_argument("video", help="path to the video file")
    parser.add_argument("--out", default="frames", help="output directory (default: frames)")
    parser.add_argument(
        "--every", type=float, default=2.0,
        help="seconds between frames (default: 2, use 0.5-1 for short clips with lots of text)",
    )
    parser.add_argument("--max-frames", type=int, default=40, help="hard cap on frames")
    parser.add_argument("--audio", action="store_true", help="also extract the audio track")
    args = parser.parse_args()

    if not os.path.isfile(args.video):
        sys.exit(f"File not found: {args.video}")

    ffmpeg = find_ffmpeg()
    duration = probe_duration(ffmpeg, args.video)
    frames = extract_frames(ffmpeg, args.video, args.out, args.every, args.max_frames)

    print(f"video    : {args.video}")
    print(f"duration : {duration:.1f}s")
    print(f"frames   : {len(frames)} -> {args.out}/")
    for frame in frames:
        print("  ", frame)

    if args.audio:
        track = extract_audio(ffmpeg, args.video, args.out)
        print("audio    :", track or "no audio track found")


if __name__ == "__main__":
    main()
