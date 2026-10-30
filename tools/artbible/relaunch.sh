#!/usr/bin/env bash
# Fase B: tutup client dev yang jalan, compile, buka client di world baru (kondisi pemain: terrain normal, siang,
# fullscreen 1920x1080 dari options.txt), lalu pasang meja 4 kursi (kamu + 3 bot) lewat AnimDev.
# Log client: $LOG (bawaan run/logs/animtest-client.log).
set -e
cd "$(dirname "$0")/../.."
LOG=${LOG:-run/logs/animtest-client.log}
if tasklist 2>/dev/null | grep -qi "java.exe" && [ -f run/animtest/command.txt ] || true; then
  rm -f run/animtest/done.txt
  echo quit > run/animtest/command.txt
  sleep 12
fi
rm -f run/animtest/command.txt run/animtest/done.txt
./gradlew compileJava -q
(./gradlew runClient -Panimtest=true -Pworld=open > "$LOG" 2>&1 &)
sleep 20
until grep -qaE "joined the game|BUILD FAILED|Crash report" "$LOG" 2>/dev/null; do sleep 3; done
if grep -qaE "BUILD FAILED|Crash report" "$LOG"; then
  echo "client failed to start, see $LOG"
  exit 1
fi
cat > run/animtest/command.txt <<'EOF'
cmd gamerule sendCommandFeedback false
cmd time set noon
cmd liarsbar create
wait 20
cmd liarsbar join
wait 20
cmd liarsbar bot add 3
wait 300
log
EOF
until [ -f run/animtest/done.txt ]; do sleep 2; done
grep -a "animdev\] state" "$LOG" | tail -1
