#!/usr/bin/env bash
# Fase B: ambil shot pembanding di sudut kamera style frame (Art Bible) lewat AnimDev, lalu susun
# berdampingan dengan style frame. Pakai: tools/artbible/shots.sh PREFIX [extra AnimDev commands...]
# Client harus sudah jalan dengan -Panimtest=true (dan biasanya -Pworld=open).
set -e
cd "$(dirname "$0")/../.."
PREFIX=${1:-cmp}
shift || true
RUN=run
rm -f $RUN/animtest/done.txt
{
  for extra in "$@"; do echo "$extra"; done
  echo "hud on"
  echo "camoff"
  echo "wait 6"
  echo "shot ${PREFIX}_pov"
  echo "cam 3 4.4 5 2 30 -0.1"
  echo "wait 6"
  echo "shot ${PREFIX}_liar"
  echo "cam 2 3.0 -15 8 40 -0.15"
  echo "wait 6"
  echo "shot ${PREFIX}_rt"
  echo "cam 2 3.4 -28 26 42 -0.3"
  echo "wait 6"
  echo "shot ${PREFIX}_win"
  echo "camoff"
} > $RUN/animtest/command.txt
until [ -f $RUN/animtest/done.txt ]; do sleep 1; done
sleep 1
python tools/artbible/compare.py "$PREFIX"
