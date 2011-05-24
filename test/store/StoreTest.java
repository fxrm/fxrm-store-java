package store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StoreTest {
    Backend backend;

    @Before
    public void setUp() {
        backend = mock(Backend.class);

        Backend.Identity testId = new Backend.Identity() { };
        when(backend.intern(Object.class, "testId")).thenReturn(testId);
    }

    @After
    public void tearDown() {
        backend = null;
    }

    private static interface ImpliedGet { String getAbc(Object id); String getObjectAbc(Object id); }

    @Test
    public void impliedGet() throws Exception {
        Store.create(ImpliedGet.class, backend);
        verify(backend, times(2)).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface ImpliedSet { void setAbc(Object id, String val); void setObjectAbc(Object id, String val); }

    @Test
    public void impliedSet() throws Exception {
        Store.create(ImpliedSet.class, backend);
        verify(backend, times(2)).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface BasicGet { @Store.Get("abc") String foo(Object inst); }

    @Test
    public void basicGet() throws Exception {
        Store.create(BasicGet.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface BasicSet { @Store.Set("abc") void foo(Object inst, String bar); }

    @Test
    public void basicSet() throws Exception {
        Store.create(BasicSet.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }

    private static interface BasicFind { @Store.Find(by = "abc") Object findObject(String str); }

    @Test
    public void basicFind() throws Exception {
        Store.create(BasicFind.class, backend);
        verify(backend).createColumn(Object.class, "abc", String.class, false);
    }

    @Test
    public void basicIntern() throws Exception {
        BasicSet s = Store.create(BasicSet.class, backend);
        Object inst1 = Store.intern(s, Object.class, "testId");
        Object inst2 = Store.intern(s, Object.class, "testId");

        verify(backend, times(2)).intern(Object.class, "testId");
        assertSame(inst1, inst2);
    }
}