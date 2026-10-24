#!/usr/bin/env bash
# Creates YOUR release signing key (one time). Keep the files it prints safe:
# if you lose this key you can never update the installed app (users must uninstall first).
set -euo pipefail

ALIAS="${1:-wosux}"
OUT="wosux-release.keystore"

if [ -e "$OUT" ]; then echo "$OUT already exists - refusing to overwrite."; exit 1; fi

read -r -s -p "Choose a keystore password (min 6 chars): " PASS; echo
keytool -genkeypair -v \
  -keystore "$OUT" -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Wosux, O=Wosux, C=PK"

base64 -w0 "$OUT" > keystore.b64 2>/dev/null || base64 "$OUT" | tr -d '\n' > keystore.b64

cat <<MSG

Done. Now add these 4 GitHub secrets (repo -> Settings -> Secrets and variables -> Actions):

  KEYSTORE_BASE64    = contents of keystore.b64
  KEYSTORE_PASSWORD  = the password you just typed
  KEY_ALIAS          = $ALIAS
  KEY_PASSWORD       = the password you just typed

Then BACK UP $OUT somewhere private (password manager / drive). Do NOT commit it.
MSG
