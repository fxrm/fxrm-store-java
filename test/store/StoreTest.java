package store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 *
 * @author Nikita
 */
public class StoreTest {
    Store.Backend backend;

    @Before
    public void setUp() {
        backend = mock(Store.Backend.class);
    }

    @After
    public void tearDown() {
        backend = null;
    }

    private static interface ImpliedGet { String getAbc(Object id); }

    @Test
    public void impliedGet() throws Exception {
        Store.create(ImpliedGet.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface ImpliedSet { void setAbc(Object id, String val); }

    @Test
    public void impliedSet() throws Exception {
        Store.create(ImpliedSet.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface BasicFind {
        @Store.Find(by = "abc")
        Object findObject(String str);
    }

    @Test
    public void basicFind() throws Exception {
        Store.create(BasicFind.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }
}