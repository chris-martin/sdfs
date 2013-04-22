package sdfs;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;
import com.typesafe.config.Config;

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
        public KeyStore keyStore;
        public X509Certificate x509;
        public Key key;
        public File file;
    }

    public final Map<CN, Identity> byCN = new HashMap<>();

    public final List<String> errors = new ArrayList<>();

    public void load(File dir, final Config password) {
        try {
            for (String filename : dir.list(new PatternFilenameFilter(".+\\.p12"))) {
                try {
                    Identity identity = new Identity();
                    identity.file = new File(dir, filename);
                    try (InputStream in = new FileInputStream(identity.file)) {
                        identity.keyStore = KeyStore.getInstance("PKCS12");
                        identity.keyStore.load(in, "storepass".toCharArray());
                    }
                    String alias = identity.keyStore.aliases().nextElement();
                    identity.x509 = (X509Certificate) identity.keyStore.getCertificate(alias);
                    identity.cn = CN.fromLdapPrincipal(identity.x509.getSubjectX500Principal());
                    identity.key = identity.keyStore.getKey(alias, password.getString(identity.cn.name).toCharArray());
                    byCN.put(identity.cn, identity);
                } catch (Exception e) {
                    errors.add(filename + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            errors.add(Throwables.getStackTraceAsString(e));
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
        for (String error : errors) {
            str.append(error).append("\n");
        }
        return str.toString();
    }

}
