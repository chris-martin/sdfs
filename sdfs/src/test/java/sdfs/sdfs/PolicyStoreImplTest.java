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
        CN charlie = new CN("charlie");

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

    @Test(expected = AccessControlException.class)
    public void getGrantGetWithoutStar() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.Get, new Instant(10));
        policy.delegate(bob, charlie, apple, Right.Get, new Instant(10));

    }}; }

    @Test public void getGrantGetStar() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.GetStar, new Instant(10));
        policy.delegate(bob, charlie, apple, Right.Get, new Instant(10));

        Assert.assertEquals(
            true,
            policy.hasAccess(charlie, apple, AccessType.Get)
        );

    }}; }

    @Test public void pingPongExpirationAttack() throws Exception { new Fixture() {{

        policy.grantOwner(alice, apple);
        policy.delegate(alice, bob, apple, Right.GetStar, new Instant(10));
        chronos.now = new Instant(8);
        policy.delegate(bob, charlie, apple, Right.GetStar, new Instant(15));
        chronos.now = new Instant(12);

        Assert.assertEquals(
            false,
            policy.hasAccess(bob, apple, AccessType.Get)
        );
        Assert.assertEquals(
            false,
            policy.hasAccess(charlie, apple, AccessType.Get)
        );

    }}; }

}
