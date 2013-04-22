package sdfs;

import com.google.common.base.Throwables;
import com.typesafe.config.Config;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class SSLConfigurator {

    final Config config;
    final IdentityCollection identityCollection;

    public SSLConfigurator(Config config, IdentityCollection identityCollection) {
        this.config = config;
        this.identityCollection = identityCollection;
    }

    public SSLContext sslContext(String identityName) {
        try {
            CN cn = new CN(config.getConfig("sdfs.identity").getString(identityName));
            IdentityCollection.Identity identity = identityCollection.byCN.get(cn);

            if (identity == null) {
                throw new RuntimeException("No pki information for " + cn);
            }

            KeyManagerFactory kmf;
            {
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                KeyStore clientStore = KeyStore.getInstance("PKCS12");
                clientStore.load(new FileInputStream(identity.file), "storepass".toCharArray());
                kmf.init(clientStore, config.getConfig("sdfs.password").getString(cn.name).toCharArray());
            }

            TrustManagerFactory tmf;
            {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null);
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                File caCertFile = new File(config.getString("sdfs.cert-path"), "ca.pem");
                X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(new FileInputStream(caCertFile));
                trustStore.setCertificateEntry(identity.x509.getSubjectX500Principal().getName(), identity.x509);
                trustStore.setCertificateEntry(caCert.getSubjectX500Principal().getName(), caCert);
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

            System.out.println(kmf.getKeyManagers()[0]);
            System.out.println(tmf.getTrustManagers()[0]);
            System.out.println(sslContext);

            return sslContext;

        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | KeyManagementException
                | CertificateException | UnrecoverableKeyException e) {
            throw new RuntimeException(Throwables.getStackTraceAsString(e), e);
        }
    }

}
