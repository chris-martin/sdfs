package sdfs.server.policy;

import org.joda.time.Instant;
import org.junit.Assert;
import org.junit.Test;
import sdfs.CN;
import sdfs.MockChronos;
import sdfs.store.MockFilesystem;
import sdfs.rights.AccessType;
import sdfs.rights.Right;

public class PolicyStoreImplTest {

    static class Fixture {

        MockFilesystem filesystem = new MockFilesystem();
        MockChronos chronos = new MockChronos();
        PolicyStoreImpl store = new PolicyStoreImpl(chronos, filesystem);

        CN alice = new CN("alice");
        CN bob = new CN("bob");

        String apple = "apples.pdf";

    }

    @Test public void ownerHasPutAccess() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);

        Assert.assertEquals(
            true,
            store.hasAccess(alice, apple, AccessType.Put)
        );

    }}; }

    @Test public void nonOwnerDoesNotHaveGetAccess() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

    @Test public void bobCanGetAfterAliceGrantsGet() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, Right.Get, new Instant(10));

        Assert.assertEquals(
            true,
            store.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

    @Test public void bobCannotPutAfterAliceGrantsGet() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, Right.Get, new Instant(10));

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, AccessType.Put)
        );

    }}; }

    @Test public void getGrantExpires() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, Right.Get, new Instant(10));

        chronos.now = new Instant(11);

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

}
