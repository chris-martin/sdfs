openssl x509 -req -CA ca-cert.pem -CAkey ca-key.pem -in "$1.csr" -out "$1.cer" -days 365 -CAcreateserial -passin pass:asdfgh
