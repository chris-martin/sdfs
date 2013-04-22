package sdfs.store;

import org.joda.time.Instant;
import org.junit.Assert;
import org.junit.Test;
import sdfs.CN;
import sdfs.MockChronos;
import sdfs.MockFilesystem;
import sdfs.store.StoreImpl;

public class StoreTest {

    static class Fixture {

        MockFilesystem filesystem = new MockFilesystem();
        MockChronos chronos = new MockChronos();
        StoreImpl store = new StoreImpl(chronos, filesystem);

        CN alice = new CN("alice");
        CN bob = new CN("bob");

        String apple = "apples.pdf";

    }

    @Test public void ownerHasPutAccess() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);

        Assert.assertEquals(
            true,
            store.hasAccess(alice, apple, StoreImpl.AccessType.Put)
        );

    }}; }

    @Test public void nonOwnerDoesNotHaveGetAccess() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, StoreImpl.AccessType.Get)
        );

    }}; }

    @Test public void bobCanGetAfterAliceGrantsGet() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, StoreImpl.Right.Get, new Instant(10));

        Assert.assertEquals(
            true,
            store.hasAccess(bob, apple, StoreImpl.AccessType.Get)
        );

    }}; }

    @Test public void bobCannotPutAfterAliceGrantsGet() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, StoreImpl.Right.Get, new Instant(10));

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, StoreImpl.AccessType.Put)
        );

    }}; }

    @Test public void getGrantExpires() throws Exception { new Fixture() {{

        store.grantOwner(alice, apple);
        store.delegate(alice, bob, apple, StoreImpl.Right.Get, new Instant(10));

        chronos.now = new Instant(11);

        Assert.assertEquals(
            false,
            store.hasAccess(bob, apple, StoreImpl.AccessType.Get)
        );

    }}; }

}
