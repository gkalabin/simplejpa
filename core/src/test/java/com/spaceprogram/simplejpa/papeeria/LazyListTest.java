package com.spaceprogram.simplejpa.papeeria;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.DeleteDomainRequest;
import com.spaceprogram.simplejpa.EntityManagerFactoryImpl;
import com.spaceprogram.simplejpa.EntityManagerSimpleJPA;
import com.spaceprogram.simplejpa.papeeria.cache.TestCacheFactory;
import com.spaceprogram.simplejpa.papeeria.models.PapeeriaTestObject;
import com.spaceprogram.simplejpa.papeeria.models.PapeeriaTestSubObject;
import junit.framework.TestCase;

import javax.persistence.EntityManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author gkalabin@bardsoftware.com
 */
public class LazyListTest extends TestCase {

    public static final String PERSISTENCE_UNIT_NAME = "papeeriatestunit";
    private EntityManagerFactoryImpl myEntityManagerFactory;

    private static final List<Class<?>> CLASSES = new ArrayList<Class<?>>();

    static {
        CLASSES.add(PapeeriaTestObject.class);
        CLASSES.add(PapeeriaTestSubObject.class);
    }


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myEntityManagerFactory = new EntityManagerFactoryImpl(PERSISTENCE_UNIT_NAME, null, null, getStringClassNames());

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        EntityManagerSimpleJPA em = (EntityManagerSimpleJPA) myEntityManagerFactory.createEntityManager();
        for (Class<?> aClass : CLASSES) {
            AmazonSimpleDB db = em.getSimpleDb();
            String domainName = em.getDomainName(aClass);

            System.out.println("deleting domain: " + domainName);
            DeleteDomainRequest deleteDomainRequest = new DeleteDomainRequest(domainName);
            db.deleteDomain(deleteDomainRequest);
        }
        em.close();
    }

    public void testDelete() {
        EntityManager em = myEntityManagerFactory.createEntityManager();

        {
            // create initial structure
            PapeeriaTestObject obj = new PapeeriaTestObject("foo", "42");
            obj.getObjects().add(new PapeeriaTestSubObject("o1_1", obj));
            obj.getObjects().add(new PapeeriaTestSubObject("o1_2", obj));
            obj.getObjects().add(new PapeeriaTestSubObject("o1_3", obj));
            em.persist(obj);
            em.close();
        }

        em = myEntityManagerFactory.createEntityManager();
        PapeeriaTestObject obj2 = em.find(PapeeriaTestObject.class, "foo");
        em.close();
        assertEquals(3, obj2.getObjects().size());

        PapeeriaTestSubObject objToDelete = null;
        for (PapeeriaTestSubObject subObject : obj2.getObjects()) {
            if (subObject.getStr().equals("o1_2")) {
                objToDelete = subObject;
            }
        }
        List<PapeeriaTestSubObject> objects = obj2.getObjects();
        objects.remove(objToDelete);
        int size = objects.size();
        assertEquals(2, size);
        em = myEntityManagerFactory.createEntityManager();
        em.remove(objToDelete);
        em.persist(obj2);
        em.close();

        em = myEntityManagerFactory.createEntityManager();
        PapeeriaTestObject obj3 = em.find(PapeeriaTestObject.class, "foo");
        em.close();
        assertEquals(2, obj3.getObjects().size());
    }

    public void testRemovedFromCache() throws IOException {
        // load properties from config
        Properties props = new Properties();
        String propsFileName = "/simplejpa.properties";
        InputStream stream = this.getClass().getResourceAsStream(propsFileName);
        if (stream == null) {
            throw new FileNotFoundException(propsFileName + " not found on classpath. Could not initialize SimpleJPA.");
        }
        props.load(stream);
        // override cache factory in order to use our own implementation of the cache
        props.setProperty("cacheFactory", "com.spaceprogram.simplejpa.papeeria.cache.TestCacheFactory");
        stream.close();

        EntityManagerFactoryImpl entityManagerFactory = new EntityManagerFactoryImpl(PERSISTENCE_UNIT_NAME, props, null, getStringClassNames());
        // create initial structure
        {
            System.out.println("[TEST] create initial storage structure");
            EntityManager em = entityManagerFactory.createEntityManager();
            PapeeriaTestObject obj = new PapeeriaTestObject("foo", "42");
            obj.getObjects().add(new PapeeriaTestSubObject("o1_1", obj));
            obj.getObjects().add(new PapeeriaTestSubObject("o1_2", obj));
            obj.getObjects().add(new PapeeriaTestSubObject("o1_3", obj));
            em.persist(obj);
            em.close();
            System.out.println("[TEST] initial storage structure created");
        }

        System.out.println("[TEST] clear cache");
        TestCacheFactory.cacheMap.clear();

        {
            // find entity
            EntityManager em = entityManagerFactory.createEntityManager();
            PapeeriaTestObject obj = em.find(PapeeriaTestObject.class, "foo");
            System.out.println("[TEST] find called");
            em.close();

            // clear cache
            System.out.println("[TEST] clear cache");
            TestCacheFactory.cacheMap.clear();

            // try to update founded entity
            PapeeriaTestSubObject newSubObj = new PapeeriaTestSubObject("new", obj);
            obj.getObjects().add(newSubObj);
            // persist updated entity
            em = entityManagerFactory.createEntityManager();
            em.persist(newSubObj);
            em.persist(obj);
            em.close();
            System.out.println("[TEST] updated");
        }

        // check that persisted
        EntityManager em = entityManagerFactory.createEntityManager();
        PapeeriaTestObject obj = em.find(PapeeriaTestObject.class, "foo");
        System.out.println("[TEST] find called");
        System.out.println(Arrays.toString(obj.getObjects().toArray()));
        em.close();
        assertEquals("42", obj.getMsg());
        assertEquals(4, obj.getObjects().size());
        assertEquals("o1_1", obj.getObjects().get(0).getStr());
        assertEquals("o1_2", obj.getObjects().get(1).getStr());
        assertEquals("o1_3", obj.getObjects().get(2).getStr());
        assertEquals("new", obj.getObjects().get(3).getStr());

        System.out.println("[TEST] shutdown");
        entityManagerFactory.close();
        System.out.println("[TEST] shutdown finished");
    }

    private Set<String> getStringClassNames() {
        Set<String> classNames = new HashSet<String>(CLASSES.size());
        for (Class aClass : CLASSES) {
            classNames.add(aClass.getName());
        }
        return classNames;
    }
}
