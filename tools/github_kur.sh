#!/usr/bin/env bash
# Creates a new PRIVATE GitHub repository and pushes this folder. Never uses an existing repository.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="${1:-aralikli-tv}"
if [[ ! "$REPO" =~ ^[a-zA-Z0-9._-]+$ ]]; then
  echo 'Repo adı yalnızca harf, rakam, nokta, tire veya alt çizgi içerebilir.' >&2
  exit 2
fi
command -v git >/dev/null || { echo 'Git yüklü değil' >&2; exit 2; }
command -v gh >/dev/null || { echo 'GitHub CLI (gh) yüklü değil' >&2; exit 2; }
gh auth status >/dev/null || { echo 'Önce gh auth login yapın' >&2; exit 2; }
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo 'Bu klasörde Git deposu zaten mevcut. Otomatik üzerine yazmıyorum.' >&2
  exit 2
fi
if gh repo view "$REPO" >/dev/null 2>&1; then
  echo 'Aynı isimli GitHub deposu zaten var. Otomatik üzerine yazmıyorum.' >&2
  exit 2
fi
git init -b main
git add .
git commit -m "Initial Android TV study app with data QA and Codex handoff"
gh repo create "$REPO" --private --source . --remote origin --push
echo 'Tamamlandı: gizli GitHub deposu oluşturuldu.'
