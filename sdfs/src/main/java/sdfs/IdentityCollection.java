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

public class IdentityCollection {

    public static class Identity {
        public CN cn;
        public X509Certificate x509;
        public Key key;
        public File file;
    }

    public final Map<CN, Identity> byCN = new HashMap<>();

    public final List<String> errors = new ArrayList<>();

    public void load(File dir) {
        try {
            for (String filename : dir.list(new PatternFilenameFilter(".*\\.p12"))) {
                try {
                    Identity identity = new Identity();
                    identity.file = new File(dir, filename);
                    KeyStore store;
                    try (InputStream in = new FileInputStream(identity.file)) {
                        store = KeyStore.getInstance("PKCS12");
                        store.load(in, "storepass".toCharArray()); // TODO real store password
                    }
                    String alias = store.aliases().nextElement(); // TODO import all aliases
                    identity.x509 = (X509Certificate) store.getCertificate(alias);
                    identity.key = store.getKey(alias, "keypass".toCharArray()); // TODO real key password
                    identity.cn = CN.fromLdapPrincipal(identity.x509.getSubjectX500Principal());
                    byCN.put(identity.cn, identity);
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
            Identity identity = byCN.get(cn);
            str.append(String.format("%s, %s\n", cn.name, identity.file.getName()));
        }
        return str.toString();
    }

}
