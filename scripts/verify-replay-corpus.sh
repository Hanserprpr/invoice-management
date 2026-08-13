#!/usr/bin/env bash
set -euo pipefail

corpus="${1:?usage: verify-replay-corpus.sh /absolute/path/to/desensitized-corpus}"
case "$corpus" in /*) ;; *) echo "corpus path must be absolute" >&2; exit 2 ;; esac
test -f "$corpus/manifest.sha256"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$corpus" && sha256sum -c manifest.sha256)
else
  (cd "$corpus" && shasum -a 256 -c manifest.sha256)
fi
sample_count=$(find "$corpus" -type f ! -name 'manifest.sha256' | wc -l | tr -d ' ')
test "$sample_count" -ge 20
test "$sample_count" -le 30
if rg -l -i '(姓名|身份证|手机号|银行卡|开户行|住址)' "$corpus" --glob '*.json' --glob '*.txt' | head -1 | grep -q .; then
  echo "possible personal information found; manual review required" >&2
  exit 3
fi
echo "replay corpus verified: $sample_count files"
