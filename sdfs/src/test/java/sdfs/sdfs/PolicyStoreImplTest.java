package sdfs.sdfs;

import org.joda.time.Instant;
import org.junit.Assert;
import org.junit.Test;
import sdfs.CN;
import sdfs.MockChronos;
import sdfs.store.MockStore;

public class PolicyStoreImplTest {

    static class Fixture {

        MockStore store = new MockStore();
        MockChronos chronos = new MockChronos();
        PolicyStoreImpl policy = new PolicyStoreImpl(chronos, store);

        CN alice = new CN("alice");
        CN bob = new CN("bob");

        String apple = "apples.pdf";

    }

    @Test public void ownerHasPutAccess() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);

        Assert.assertEquals(
            true,
            policy.hasAccess(alice, apple, AccessType.Put)
        );

    }}; }

    @Test public void nonOwnerDoesNotHaveGetAccess() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);

        Assert.assertEquals(
            false,
            policy.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

    @Test public void bobCanGetAfterAliceGrantsGet() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.Get, new Instant(10));

        Assert.assertEquals(
            true,
            policy.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

    @Test public void bobCannotPutAfterAliceGrantsGet() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.Get, new Instant(10));

        Assert.assertEquals(
            false,
            policy.hasAccess(bob, apple, AccessType.Put)
        );

    }}; }

    @Test public void getGrantExpires() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.Get, new Instant(10));

        chronos.now = new Instant(11);

        Assert.assertEquals(
            false,
            policy.hasAccess(bob, apple, AccessType.Get)
        );

    }}; }

}
