
STOREPASS=changeit
KEYPASS=keypass

# Generate CA key pair and store it in ca.jks
keytool -keystore ca.jks -storetype JKS -storepass $STOREPASS \
        -genkeypair -alias ca -keyalg RSA -dname CN=ca -keypass $KEYPASS

# Export CA cert to ca.pem
keytool -keystore ca.jks -storetype JKS -storepass $STOREPASS \
        -alias ca -exportcert -rfc > ca.pem

# Import ca.pem into ca-certs.jks
keytool -keystore ca-certs.jks -storepass $STOREPASS \
        -importcert -alias ca -file ca.pem -noprompt

