package sdfs.sdfs;

import org.junit.Test;
import sdfs.CN;
import sdfs.MockChronos;
import sdfs.store.MockStore;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SDFSImplTest {

    static class Fixture {

        MockStore store = new MockStore();
        MockChronos chronos = new MockChronos();
        PolicyStoreImpl policy = new PolicyStoreImpl(chronos, store);
        SDFSImpl sdfs = new SDFSImpl(store, store, policy);

        CN alice = new CN("alice");
        CN bob = new CN("bob");

        String apple = "apples.pdf";

    }

    @Test
    public void testPutAndGet() throws Exception { new Fixture() {{

        SDFS.Put put = sdfs.put(alice, apple);
        put.contentByteSink().write(new byte[] { 1, 2, 3 });
        put.metaByteSink().write(new byte[] { 4, 5, 6 });
        put.release();

        assertTrue(policy.isOwner(alice, apple));

        SDFS.Get get = sdfs.get(alice, apple);

        assertArrayEquals(
            new byte[]{1, 2, 3},
            get.contentByteSource().read()
        );

        assertArrayEquals(
            new byte[]{4, 5, 6},
            get.metaByteSource().read()
        );

        assertEquals(
            true,
            sdfs.locks.containsKey(apple)
        );

        get.release();

        assertEquals(
            false,
            sdfs.locks.containsKey(apple)
        );

    }}; }

    @Test(expected = AccessControlException.class)
    public void testPutAndFailGet1() throws Exception { new Fixture() {{

        SDFS.Put put = sdfs.put(alice, apple);
        put.contentByteSink().write(new byte[] { 1, 2, 3 });
        put.metaByteSink().write(new byte[] { 4, 5, 6 });
        put.release();

        SDFS.Get get = sdfs.get(bob, apple);

    }}; }

    @Test
    public void testPutAndFailGet2() throws Exception { new Fixture() {{

        SDFS.Put put = sdfs.put(alice, apple);
        put.contentByteSink().write(new byte[] { 1, 2, 3 });
        put.metaByteSink().write(new byte[] { 4, 5, 6 });
        put.release();

        try {
            sdfs.get(bob, apple);
        } catch (AccessControlException ignored) { }

        assertEquals(
            false,
            sdfs.locks.containsKey(apple)
        );

    }}; }

}
