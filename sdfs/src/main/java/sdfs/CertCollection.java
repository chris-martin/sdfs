package sdfs;

import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertCollection {

    public static class Cert {
        public CN cn;
        public X509Certificate x509;
        public Key key;
        public File file;
    }

    public final Map<CN, Cert> byCN = new HashMap<>();

    public final List<String> errors = new ArrayList<>();

    public void load(File dir) {
        try {
            for (String filename : dir.list(new PatternFilenameFilter(".*\\.p12"))) {
                try {
                    Cert cert = new Cert();
                    cert.file = new File(dir, filename);
                    KeyStore store;
                    try (InputStream in = new FileInputStream(cert.file)) {
                        store = KeyStore.getInstance("PKCS12");
                        store.load(in, "storepass".toCharArray()); // TODO real store password
                    }
                    String alias = store.aliases().nextElement(); // TODO import all aliases
                    cert.x509 = (X509Certificate) store.getCertificate(alias);
                    cert.key = store.getKey(alias, "keypass".toCharArray()); // TODO real key password
                    cert.cn = CN.fromLdapPrincipal(cert.x509.getSubjectX500Principal());
                    byCN.put(cert.cn, cert);
                } catch (Exception e) {
                    errors.add(filename + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        List<CN> names = Lists.newArrayList(byCN.keySet());
        Collections.sort(names);
        for (CN cn : names) {
            Cert cert = byCN.get(cn);
            str.append(String.format("%s, %s\n", cn.name, cert.file.getName()));
        }
        return str.toString();
    }

}
