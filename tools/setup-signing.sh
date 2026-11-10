#!/usr/bin/env bash
# One-time, LOCAL setup on your own Ubuntu PC. Private key never goes into Git.
set -euo pipefail
umask 077
REPO="alemencik/aralikli-tv"
ROOT="$HOME/.config/araliklitv"
KEYSTORE="$ROOT/signing.jks"
PASSWORD_FILE="$ROOT/signing-password"
ALIAS="araliklitv"
command -v gh >/dev/null || { echo 'GitHub CLI (gh) yok: sudo apt install gh'; exit 1; }
command -v keytool >/dev/null || { echo 'Java keytool kurulu degil'; exit 1; }
command -v openssl >/dev/null || { echo 'openssl kurulu degil'; exit 1; }
gh auth status >/dev/null || { echo 'Once gh auth login komutunu calistir'; exit 1; }
mkdir -p "$ROOT"
if [[ -e "$KEYSTORE" || -e "$PASSWORD_FILE" ]]; then
  [[ -f "$KEYSTORE" && -f "$PASSWORD_FILE" ]] || { echo 'Eski anahtar/şifre eksik; yeniden olusturma guvenli degil.'; exit 1; }
else
  openssl rand -hex 24 > "$PASSWORD_FILE"
  PASSWORD="$(cat "$PASSWORD_FILE")"
  keytool -genkeypair -noprompt -storetype PKCS12 \
    -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 3072 \
    -validity 15000 -dname 'CN=Aralikli TV,O=Daylak Dijital,C=TR' \
    -storepass "$PASSWORD" -keypass "$PASSWORD"
fi
chmod 600 "$KEYSTORE" "$PASSWORD_FILE"
PASSWORD="$(cat "$PASSWORD_FILE")"
base64 -w0 "$KEYSTORE" | gh secret set ANDROID_SIGNING_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set ANDROID_SIGNING_STORE_PASSWORD -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set ANDROID_SIGNING_KEY_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set ANDROID_SIGNING_KEY_ALIAS -R "$REPO"
gh variable set ANDROID_SIGNING_ENABLED --body true -R "$REPO"
gh workflow run publish-apk.yml -R "$REPO"
echo
echo 'Signing secrets ayarlandi, APK yayinlama islemi tetiklendi.'
echo 'Bu iki dosyayi GUVENLI ve OZEL yedekle:'
echo "  $KEYSTORE"
echo "  $PASSWORD_FILE"
echo 'Anahtari kaybedersen gelecek APK guncellemeleri mevcut kurulumun ustune kurulamaz.'
