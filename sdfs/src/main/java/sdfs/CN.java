package sdfs;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.Principal;

public class CN implements Comparable<CN> {

    public final String name;

    public CN(String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        this.name = name;
    }

    public static CN fromLdapPrincipal(Principal principal) throws InvalidNameException {
        return new CN(
            FluentIterable
                .from(new LdapName(principal.getName()).getRdns())
                .firstMatch(new Predicate<Rdn>() {
                    @Override
                    public boolean apply(Rdn input) {
                        return input.getType().equalsIgnoreCase("CN");
                    }
                })
                .get().getValue().toString()
        );
    }

    public int compareTo(CN that) {
        return name.compareTo(that.name);
    }

    public String toString() {
        return String.format("CN=%s", name);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CN cn = (CN) o;
        return name.equals(cn.name);
    }

    public int hashCode() {
        return name.hashCode();
    }

}
