#!/usr/bin/env bash
# Generates or imports the development PKI for airports-routes-service (TLS 1.3 / encryption in transit).
#
#   certs/ca.crt           dev CA certificate
#   certs/truststore.p12   truststore containing the CA certificate for outgoing calls
#   certs/airports.p12     private key and certificate for airports-routes-service
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$ROOT_DIR/certs"
SHARED_CERTS_FO="$ROOT_DIR/../flight-operations-service/certs"
SHARED_CERTS_AC="$ROOT_DIR/../aircraft-maintenance-service/certs"
PASS="${SSL_PASSWORD:-changeit}"

# Ensure keytool is available in PATH
if ! command -v keytool >/dev/null 2>&1; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  elif [[ -x "/home/afonso/Downloads/idea-IU-262.10968.63/jbr/bin/keytool" ]]; then
    export PATH="/home/afonso/Downloads/idea-IU-262.10968.63/jbr/bin:$PATH"
  fi
fi

mkdir -p "$OUT"

# 1. If certificates already exist in sibling flight-operations-service, copy them so both services share the exact same CA:
if [[ -f "$SHARED_CERTS_FO/airports.p12" && -f "$SHARED_CERTS_FO/truststore.p12" ]]; then
  echo "Importing shared certificates from $SHARED_CERTS_FO..."
  cp "$SHARED_CERTS_FO/ca.crt" "$OUT/ca.crt" 2>/dev/null || true
  cp "$SHARED_CERTS_FO/truststore.p12" "$OUT/truststore.p12"
  cp "$SHARED_CERTS_FO/airports.p12" "$OUT/airports.p12"
  echo "Certificates successfully imported into $OUT"
  exit 0
elif [[ -f "$SHARED_CERTS_AC/airports.p12" && -f "$SHARED_CERTS_AC/truststore.p12" ]]; then
  echo "Importing shared certificates from $SHARED_CERTS_AC..."
  cp "$SHARED_CERTS_AC/ca.crt" "$OUT/ca.crt" 2>/dev/null || true
  cp "$SHARED_CERTS_AC/truststore.p12" "$OUT/truststore.p12"
  cp "$SHARED_CERTS_AC/airports.p12" "$OUT/airports.p12"
  echo "Certificates successfully imported into $OUT"
  exit 0
fi

# 2. Otherwise generate fresh standalone certificates
cd "$OUT"
rm -f ./*.p12 ./*.crt ./*.csr

echo "-> Generating AISafe Dev CA..."
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=AISafe Dev CA, O=ISEP SIDIS" -ext bc:c \
  -keystore ca.p12 -storetype PKCS12 -storepass "$PASS"
keytool -exportcert -alias ca -keystore ca.p12 -storepass "$PASS" -rfc -file ca.crt
keytool -importcert -noprompt -alias aisafe-ca -file ca.crt \
  -keystore truststore.p12 -storetype PKCS12 -storepass "$PASS"

echo "-> Generating airports certificate..."
keytool -genkeypair -alias airports -keyalg RSA -keysize 2048 -validity 825 \
  -dname "CN=airports, O=ISEP SIDIS" \
  -keystore airports.p12 -storetype PKCS12 -storepass "$PASS"
keytool -certreq -alias airports -keystore airports.p12 -storepass "$PASS" -file airports.csr
keytool -gencert -alias ca -keystore ca.p12 -storepass "$PASS" -rfc -validity 825 \
  -infile airports.csr -outfile airports.crt \
  -ext "SAN=dns:localhost,ip:127.0.0.1,dns:airports,dns:airports-1,dns:airports-2,dns:airports-3" \
  -ext "KU=digitalSignature,keyEncipherment" -ext "EKU=serverAuth,clientAuth"
keytool -importcert -noprompt -alias aisafe-ca -file ca.crt -keystore airports.p12 -storepass "$PASS"
keytool -importcert -noprompt -alias airports -file airports.crt -keystore airports.p12 -storepass "$PASS"
rm -f airports.csr

echo "Done. Certificates generated in $OUT"
