KEYSTORE=$1.jks
STOREPASS=storepass
CA_KEYPASS=keypass
KEYPASS=keypass
PKCS12_STOREPASS=storepass
keytool -keystore $KEYSTORE -storepass $STOREPASS -genkeypair -alias tmp -keyalg RSA -dname CN=$1 -keypass keypass
keytool -keystore $KEYSTORE -storepass $STOREPASS -certreq -alias tmp -keypass "$KEYPASS" | keytool -keystore ca.jks -storetype JKS -storepass storepass -gencert -alias ca -keypass "$CA_KEYPASS" -rfc > $1.pem
keytool -keystore $KEYSTORE -storepass $STOREPASS -importcert -alias ca -file ca.pem -noprompt
keytool -keystore $KEYSTORE -storepass $STOREPASS -importcert -alias tmp -keypass "$KEYPASS" -file $1.pem
keytool -importkeystore -srckeystore $KEYSTORE -srcstorepass $STOREPASS -destkeystore $1.p12 -deststoretype PKCS12 -deststorepass "$PKCS12_STOREPASS" -srcalias tmp -srckeypass "$KEYPASS"
