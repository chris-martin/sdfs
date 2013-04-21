package sdfs;

import com.google.common.collect.Lists;
import com.google.common.io.PatternFilenameFilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class CertCollection {

    private static class Cert {
        CN cn;
        X509Certificate x509;
        File file;
    }

    public final Map<CN, Cert> byCN = new HashMap<>();

    public final List<String> errors = new ArrayList<>();

    public void load(File dir) {
        try {
            for (String filename : dir.list(new PatternFilenameFilter(".*\\.cer"))) {
                try {
                    Cert cert = new Cert();
                    cert.file = new File(dir, filename);
                    try (InputStream in = new FileInputStream(cert.file)) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        cert.x509 = (X509Certificate) cf.generateCertificate(in);
                    }
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
