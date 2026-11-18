#!/usr/bin/env python3
"""Régénère les trois sons de l'app (app/src/main/res/raw/) par synthèse, avec ffmpeg.

Usage (depuis la racine du dépôt) :  python content/tools/sounds.py

Sons originaux, produits par des formules : aucun fichier téléchargé, aucun droit à céder.
  answer_correct : deux notes montantes (sol, ré), courtes.
  answer_wrong   : une note grave qui descend.
  badge_unlocked : un arpège do-mi-sol-do.
"""
import subprocess
import sys
from pathlib import Path

RAW = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"

SOUNDS = {
    "answer_correct": ("0.35*(sin(2*PI*if(lt(t,0.09),784,1175)*t))*exp(-7*if(lt(t,0.09),t,t-0.09))", 0.42),
    "answer_wrong": ("0.35*sin(2*PI*(262-90*t)*t)*exp(-6*t)", 0.35),
    "badge_unlocked": ("0.3*sin(2*PI*if(lt(t,0.1),523,if(lt(t,0.2),659,if(lt(t,0.3),784,1047)))*t)*exp(-3*mod(t,0.1))*exp(-1.5*t)", 0.7),
}


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    RAW.mkdir(parents=True, exist_ok=True)
    for name, (expression, duration) in SOUNDS.items():
        out = RAW / f"{name}.ogg"
        subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi",
             "-i", f"aevalsrc='{expression}':s=44100:d={duration}",
             "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", str(out)],
            check=True,
        )
        print(f"Écrit : {out.relative_to(RAW.parents[4]).as_posix()} ({out.stat().st_size} octets)")


if __name__ == "__main__":
    main()
