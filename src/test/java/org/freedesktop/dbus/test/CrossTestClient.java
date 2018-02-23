/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/
package org.freedesktop.dbus.test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.Peer;
import org.freedesktop.dbus.types.DBusMapType;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossTestClient implements Binding.TestClient, DBusSigHandler<Binding.TestSignals.Triggered> {

    private static final Logger              LOGGER = LoggerFactory.getLogger(CrossTestClient.class);

    private DBusConnection                   conn;
    public static final Set<String>               PASSED = new TreeSet<>();
    public static final Map<String, List<String>> FAILED = new HashMap<>();

    private static CrossTestClient         CROSS_TEST_CLIENT_INSTANCE;
    static {
        List<String> l = new Vector<>();
        l.add("Signal never arrived");
        FAILED.put("org.freedesktop.DBus.Binding.TestSignals.Triggered", l);
        l = new Vector<>();
        l.add("Method never called");
        FAILED.put("org.freedesktop.DBus.Binding.TestClient.Response", l);
    }

    public CrossTestClient(DBusConnection _conn) {
        this.conn = _conn;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return null;
    }

    @Override
    public void handle(Binding.TestSignals.Triggered t) {
        FAILED.remove("org.freedesktop.DBus.Binding.TestSignals.Triggered");
        if (new UInt64(21389479283L).equals(t.a) && "/Test".equals(t.getPath())) {
            pass("org.freedesktop.DBus.Binding.TestSignals.Triggered");
        } else if (!new UInt64(21389479283L).equals(t.a)) {
            fail("org.freedesktop.DBus.Binding.TestSignals.Triggered", "Incorrect signal content; expected 21389479283 got " + t.a);
        } else if (!"/Test".equals(t.getPath())) {
            fail("org.freedesktop.DBus.Binding.TestSignals.Triggered", "Incorrect signal source object; expected /Test got " + t.getPath());
        }
    }

    @Override
    public void Response(UInt16 a, double b) {
        FAILED.remove("org.freedesktop.DBus.Binding.TestClient.Response");
        if (a.equals(new UInt16(15)) && (b == 12.5)) {
            pass("org.freedesktop.DBus.Binding.TestClient.Response");
        } else {
            fail("org.freedesktop.DBus.Binding.TestClient.Response", "Incorrect parameters; expected 15, 12.5 got " + a + ", " + b);
        }
    }

    public static void pass(String test) {
        PASSED.add(test.replaceAll("[$]", "."));
    }

    public static void fail(String test, String reason) {
        test = test.replaceAll("[$]", ".");
        List<String> reasons = FAILED.get(test);
        if (null == reasons) {
            reasons = new Vector<>();
            FAILED.put(test, reasons);
        }
        reasons.add(reason);
    }

    @SuppressWarnings("unchecked")
    public static void test(Class<? extends DBusInterface> iface, Object proxy, String method, Object rv, Object... parameters) {
        try {
            Method[] ms = iface.getMethods();
            Method m = null;
            for (Method t : ms) {
                if (t.getName().equals(method)) {
                    m = t;
                }
            }
            Object o = m.invoke(proxy, parameters);

            String msg = "Incorrect return value; sent ( ";
            if (null != parameters) {
                for (Object po : parameters) {
                    if (null != po) {
                        msg += collapseArray(po) + ",";
                    }
                }
            }
            msg = msg.replaceAll(".$", ");");
            msg += " expected " + collapseArray(rv) + " got " + collapseArray(o);

            if (null != rv && rv.getClass().isArray()) {
                compareArray(iface.getName() + "." + method, rv, o);
            } else if (rv instanceof Map) {
                if (o instanceof Map) {
                    Map<Object, Object> a = (Map<Object, Object>) o;
                    Map<Object, Object> b = (Map<Object, Object>) rv;
                    if (a.keySet().size() != b.keySet().size()) {
                        fail(iface.getName() + "." + method, msg);
                    } else {
                        for (Object k : a.keySet()) {
                            if (a.get(k) instanceof List) {
                                if (b.get(k) instanceof List) {
                                    if (setCompareLists((List<Object>) a.get(k), (List<Object>) b.get(k))) {
                                        ;
                                    } else {
                                        fail(iface.getName() + "." + method, msg);
                                    }
                                } else {
                                    fail(iface.getName() + "." + method, msg);
                                }
                            } else if (!a.get(k).equals(b.get(k))) {
                                fail(iface.getName() + "." + method, msg);
                                return;
                            }
                        }
                    }
                    pass(iface.getName() + "." + method);
                } else {
                    fail(iface.getName() + "." + method, msg);
                }
            } else {
                if (o == rv || (o != null && o.equals(rv))) {
                    pass(iface.getName() + "." + method);
                } else {
                    fail(iface.getName() + "." + method, msg);
                }
            }
        } catch (DBusExecutionException exDbe) {
            exDbe.printStackTrace();
            fail(iface.getName() + "." + method, "Error occurred during execution: " + exDbe.getClass().getName() + " " + exDbe.getMessage());
        } catch (InvocationTargetException exIt) {
            exIt.printStackTrace();
            fail(iface.getName() + "." + method, "Error occurred during execution: " + exIt.getCause().getClass().getName() + " " + exIt.getCause().getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            fail(iface.getName() + "." + method, "Error occurred during execution: " + e.getClass().getName() + " " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static String collapseArray(Object array) {
        if (null == array) {
            return "null";
        }
        if (array.getClass().isArray()) {
            String s = "{ ";
            for (int i = 0; i < Array.getLength(array); i++) {
                s += collapseArray(Array.get(array, i)) + ",";
            }
            s = s.replaceAll(".$", " }");
            return s;
        } else if (array instanceof List) {
            String s = "{ ";
            for (Object o : (List<Object>) array) {
                s += collapseArray(o) + ",";
            }
            s = s.replaceAll(".$", " }");
            return s;
        } else if (array instanceof Map) {
            String s = "{ ";
            for (Object o : ((Map<Object, Object>) array).keySet()) {
                s += collapseArray(o) + " => " + collapseArray(((Map<Object, Object>) array).get(o)) + ",";
            }
            s = s.replaceAll(".$", " }");
            return s;
        } else {
            return array.toString();
        }
    }

    public static <T> boolean setCompareLists(List<T> a, List<T> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Object v : a) {
            if (!b.contains(v)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static List<Variant<Object>> primitizeRecurse(Object a, Type t) {
        List<Variant<Object>> vs = new Vector<>();
        if (t instanceof ParameterizedType) {
            Class<Object> c = (Class<Object>) ((ParameterizedType) t).getRawType();
            if (List.class.isAssignableFrom(c)) {
                Object os;
                if (a instanceof List) {
                    os = ((List<Object>) a).toArray();
                } else {
                    os = a;
                }
                Type[] ts = ((ParameterizedType) t).getActualTypeArguments();
                for (int i = 0; i < Array.getLength(os); i++) {
                    vs.addAll(primitizeRecurse(Array.get(os, i), ts[0]));
                }
            } else if (Map.class.isAssignableFrom(c)) {
                Object[] os = ((Map<?, ?>) a).keySet().toArray();
                Object[] ks = ((Map<?, ?>) a).values().toArray();
                Type[] ts = ((ParameterizedType) t).getActualTypeArguments();
                for (Object element : ks) {
                    vs.addAll(primitizeRecurse(element, ts[0]));
                }
                for (Object element : os) {
                    vs.addAll(primitizeRecurse(element, ts[1]));
                }
            } else if (Struct.class.isAssignableFrom(c)) {
                Object[] os = ((Struct) a).getParameters();
                Type[] ts = ((ParameterizedType) t).getActualTypeArguments();
                for (int i = 0; i < os.length; i++) {
                    vs.addAll(primitizeRecurse(os[i], ts[i]));
                }

            } else if (Variant.class.isAssignableFrom(c)) {
                vs.addAll(primitizeRecurse(((Variant<?>) a).getValue(), ((Variant<?>) a).getType()));
            }
        } else if (Variant.class.isAssignableFrom((Class<?>) t)) {
            vs.addAll(primitizeRecurse(((Variant<?>) a).getValue(), ((Variant<?>) a).getType()));
        } else if (t instanceof Class && ((Class<?>) t).isArray()) {
            Type t2 = ((Class<?>) t).getComponentType();
            for (int i = 0; i < Array.getLength(a); i++) {
                vs.addAll(primitizeRecurse(Array.get(a, i), t2));
            }
        } else {
            vs.add(new Variant<>(a));
        }

        return vs;
    }

    public static List<Variant<Object>> primitize(Variant<Object> a) {
        return primitizeRecurse(a.getValue(), a.getType());
    }

    public static void primitizeTest(Tests tests, Object input) {
        Variant<Object> in = new Variant<>(input);
        List<Variant<Object>> vs = primitize(in);
        List<Variant<Object>> res;

        try {

            res = tests.Primitize(in);
            if (setCompareLists(res, vs)) {
                pass("org.freedesktop.DBus.Binding.Tests.Primitize");
            } else {
                fail("org.freedesktop.DBus.Binding.Tests.Primitize", "Wrong Return Value; expected " + collapseArray(vs) + " got " + collapseArray(res));
            }

        } catch (Exception e) {
            LOGGER.debug("", e);
            fail("org.freedesktop.DBus.Binding.Tests.Primitize", "Exception occurred during test: (" + e.getClass().getName() + ") " + e.getMessage());
        }
    }

    public static void doTests(Peer peer, Introspectable intro, Introspectable rootintro, Tests tests, Binding.SingleTests singletests) {
        Random r = new Random();
        int i;
        test(Peer.class, peer, "Ping", null);

        try {
            if (intro.Introspect().startsWith("<!DOCTYPE")) {
                pass("org.freedesktop.DBus.Introspectable.Introspect");
            } else {
                fail("org.freedesktop.DBus.Introspectable.Introspect", "Didn't get valid xml data back when introspecting /Test");
            }
        } catch (DBusExecutionException dbee) {

            LOGGER.debug("", dbee);

            fail("org.freedesktop.DBus.Introspectable.Introspect", "Got exception during introspection on /Test (" + dbee.getClass().getName() + "): " + dbee.getMessage());
        }

        try {
            if (rootintro.Introspect().startsWith("<!DOCTYPE")) {
                pass("org.freedesktop.DBus.Introspectable.Introspect");
            } else {
                fail("org.freedesktop.DBus.Introspectable.Introspect", "Didn't get valid xml data back when introspecting /");
            }
        } catch (DBusExecutionException dbee) {

            LOGGER.debug("", dbee);

            fail("org.freedesktop.DBus.Introspectable.Introspect", "Got exception during introspection on / (" + dbee.getClass().getName() + "): " + dbee.getMessage());
        }

        test(Tests.class, tests, "Identity", new Variant<>(new Integer(1)), new Variant<>(new Integer(1)));
        test(Tests.class, tests, "Identity", new Variant<>("Hello"), new Variant<>("Hello"));

        test(Tests.class, tests, "IdentityBool", false, false);
        test(Tests.class, tests, "IdentityBool", true, true);

        test(Tests.class, tests, "Invert", false, true);
        test(Tests.class, tests, "Invert", true, false);

        test(Tests.class, tests, "IdentityByte", (byte) 0, (byte) 0);
        test(Tests.class, tests, "IdentityByte", (byte) 1, (byte) 1);
        test(Tests.class, tests, "IdentityByte", (byte) -1, (byte) -1);
        test(Tests.class, tests, "IdentityByte", Byte.MAX_VALUE, Byte.MAX_VALUE);
        test(Tests.class, tests, "IdentityByte", Byte.MIN_VALUE, Byte.MIN_VALUE);
        i = r.nextInt();
        test(Tests.class, tests, "IdentityByte", (byte) i, (byte) i);

        test(Tests.class, tests, "IdentityInt16", (short) 0, (short) 0);
        test(Tests.class, tests, "IdentityInt16", (short) 1, (short) 1);
        test(Tests.class, tests, "IdentityInt16", (short) -1, (short) -1);
        test(Tests.class, tests, "IdentityInt16", Short.MAX_VALUE, Short.MAX_VALUE);
        test(Tests.class, tests, "IdentityInt16", Short.MIN_VALUE, Short.MIN_VALUE);
        i = r.nextInt();
        test(Tests.class, tests, "IdentityInt16", (short) i, (short) i);

        test(Tests.class, tests, "IdentityInt32", 0, 0);
        test(Tests.class, tests, "IdentityInt32", 1, 1);
        test(Tests.class, tests, "IdentityInt32", -1, -1);
        test(Tests.class, tests, "IdentityInt32", Integer.MAX_VALUE, Integer.MAX_VALUE);
        test(Tests.class, tests, "IdentityInt32", Integer.MIN_VALUE, Integer.MIN_VALUE);
        i = r.nextInt();
        test(Tests.class, tests, "IdentityInt32", i, i);

        test(Tests.class, tests, "IdentityInt64", (long) 0, (long) 0);
        test(Tests.class, tests, "IdentityInt64", (long) 1, (long) 1);
        test(Tests.class, tests, "IdentityInt64", (long) -1, (long) -1);
        test(Tests.class, tests, "IdentityInt64", Long.MAX_VALUE, Long.MAX_VALUE);
        test(Tests.class, tests, "IdentityInt64", Long.MIN_VALUE, Long.MIN_VALUE);
        i = r.nextInt();
        test(Tests.class, tests, "IdentityInt64", (long) i, (long) i);

        test(Tests.class, tests, "IdentityUInt16", new UInt16(0), new UInt16(0));
        test(Tests.class, tests, "IdentityUInt16", new UInt16(1), new UInt16(1));
        test(Tests.class, tests, "IdentityUInt16", new UInt16(UInt16.MAX_VALUE), new UInt16(UInt16.MAX_VALUE));
        test(Tests.class, tests, "IdentityUInt16", new UInt16(UInt16.MIN_VALUE), new UInt16(UInt16.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        test(Tests.class, tests, "IdentityUInt16", new UInt16(i % UInt16.MAX_VALUE), new UInt16(i % UInt16.MAX_VALUE));

        test(Tests.class, tests, "IdentityUInt32", new UInt32(0), new UInt32(0));
        test(Tests.class, tests, "IdentityUInt32", new UInt32(1), new UInt32(1));
        test(Tests.class, tests, "IdentityUInt32", new UInt32(UInt32.MAX_VALUE), new UInt32(UInt32.MAX_VALUE));
        test(Tests.class, tests, "IdentityUInt32", new UInt32(UInt32.MIN_VALUE), new UInt32(UInt32.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        test(Tests.class, tests, "IdentityUInt32", new UInt32(i % UInt32.MAX_VALUE), new UInt32(i % UInt32.MAX_VALUE));

        test(Tests.class, tests, "IdentityUInt64", new UInt64(0), new UInt64(0));
        test(Tests.class, tests, "IdentityUInt64", new UInt64(1), new UInt64(1));
        test(Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MAX_LONG_VALUE), new UInt64(UInt64.MAX_LONG_VALUE));
        test(Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MAX_BIG_VALUE), new UInt64(UInt64.MAX_BIG_VALUE));
        test(Tests.class, tests, "IdentityUInt64", new UInt64(UInt64.MIN_VALUE), new UInt64(UInt64.MIN_VALUE));
        i = r.nextInt();
        i = i > 0 ? i : -i;
        test(Tests.class, tests, "IdentityUInt64", new UInt64(i % UInt64.MAX_LONG_VALUE), new UInt64(i % UInt64.MAX_LONG_VALUE));

        test(Tests.class, tests, "IdentityDouble", 0.0, 0.0);
        test(Tests.class, tests, "IdentityDouble", 1.0, 1.0);
        test(Tests.class, tests, "IdentityDouble", -1.0, -1.0);
        test(Tests.class, tests, "IdentityDouble", Double.MAX_VALUE, Double.MAX_VALUE);
        test(Tests.class, tests, "IdentityDouble", Double.MIN_VALUE, Double.MIN_VALUE);
        i = r.nextInt();
        test(Tests.class, tests, "IdentityDouble", (double) i, (double) i);

        test(Tests.class, tests, "IdentityString", "", "");
        test(Tests.class, tests, "IdentityString", "The Quick Brown Fox Jumped Over The Lazy Dog", "The Quick Brown Fox Jumped Over The Lazy Dog");
        test(Tests.class, tests, "IdentityString", "ひらがなゲーム - かなぶん", "ひらがなゲーム - かなぶん");

        testArray(Tests.class, tests, "IdentityBoolArray", Boolean.TYPE, null);
        testArray(Tests.class, tests, "IdentityByteArray", Byte.TYPE, null);
        testArray(Tests.class, tests, "IdentityInt16Array", Short.TYPE, null);
        testArray(Tests.class, tests, "IdentityInt32Array", Integer.TYPE, null);
        testArray(Tests.class, tests, "IdentityInt64Array", Long.TYPE, null);
        testArray(Tests.class, tests, "IdentityDoubleArray", Double.TYPE, null);

        testArray(Tests.class, tests, "IdentityArray", Variant.class, new Variant<>("aoeu"));
        testArray(Tests.class, tests, "IdentityUInt16Array", UInt16.class, new UInt16(12));
        testArray(Tests.class, tests, "IdentityUInt32Array", UInt32.class, new UInt32(190));
        testArray(Tests.class, tests, "IdentityUInt64Array", UInt64.class, new UInt64(103948));
        testArray(Tests.class, tests, "IdentityStringArray", String.class, "asdf");

        int[] is = new int[0];
        test(Tests.class, tests, "Sum", 0L, is);
        r = new Random();
        int len = (r.nextInt() % 100) + 15;
        len = (len < 0 ? -len : len) + 15;
        is = new int[len];
        long result = 0;
        for (i = 0; i < len; i++) {
            is[i] = r.nextInt();
            result += is[i];
        }
        test(Tests.class, tests, "Sum", result, is);

        byte[] bs = new byte[0];
        test(Binding.SingleTests.class, singletests, "Sum", new UInt32(0), bs);
        len = (r.nextInt() % 100);
        len = (len < 0 ? -len : len) + 15;
        bs = new byte[len];
        int res = 0;
        for (i = 0; i < len; i++) {
            bs[i] = (byte) r.nextInt();
            res += (bs[i] < 0 ? bs[i] + 256 : bs[i]);
        }
        test(Binding.SingleTests.class, singletests, "Sum", new UInt32(res % (UInt32.MAX_VALUE + 1)), bs);

        test(Tests.class, tests, "DeStruct", new Binding.Triplet<>("hi", new UInt32(12), new Short((short) 99)), new Binding.TestStruct("hi", new UInt32(12), new Short((short) 99)));

        Map<String, String> in = new HashMap<>();
        Map<String, List<String>> out = new HashMap<>();
        test(Tests.class, tests, "InvertMapping", out, in);

        in.put("hi", "there");
        in.put("to", "there");
        in.put("from", "here");
        in.put("in", "out");
        List<String> l = new Vector<>();
        l.add("hi");
        l.add("to");
        out.put("there", l);
        l = new Vector<>();
        l.add("from");
        out.put("here", l);
        l = new Vector<>();
        l.add("in");
        out.put("out", l);
        test(Tests.class, tests, "InvertMapping", out, in);

        primitizeTest(tests, new Integer(1));
        primitizeTest(tests, new Variant<>(new Variant<>(new Variant<>(new Variant<>("Hi")))));
        primitizeTest(tests, new Variant<>(in, new DBusMapType(String.class, String.class)));

        test(Tests.class, tests, "Trigger", null, "/Test", new UInt64(21389479283L));

        try {
            CROSS_TEST_CLIENT_INSTANCE.conn.sendMessage(new Trigger("/Test", new UInt16(15), 12.5));
        } catch (DBusException dbe) {

            LOGGER.debug("", dbe);

            throw new DBusExecutionException(dbe.getMessage());
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
        }

        test(Tests.class, tests, "Exit", null);
    }

    public static void testArray(Class<? extends DBusInterface> iface, Object proxy, String method, Class<? extends Object> arrayType, Object content) {
        Object array = Array.newInstance(arrayType, 0);
        test(iface, proxy, method, array, array);
        Random r = new Random();
        int l = (r.nextInt() % 100);
        array = Array.newInstance(arrayType, (l < 0 ? -l : l) + 15);
        if (null != content) {
            Arrays.fill((Object[]) array, content);
        }
        test(iface, proxy, method, array, array);
    }

    public static void compareArray(String test, Object a, Object b) {
        if (!a.getClass().equals(b.getClass())) {
            fail(test, "Incorrect return type; expected " + a.getClass() + " got " + b.getClass());
            return;
        }
        boolean pass = false;

        if (a instanceof Object[]) {
            pass = Arrays.equals((Object[]) a, (Object[]) b);
        } else if (a instanceof byte[]) {
            pass = Arrays.equals((byte[]) a, (byte[]) b);
        } else if (a instanceof boolean[]) {
            pass = Arrays.equals((boolean[]) a, (boolean[]) b);
        } else if (a instanceof int[]) {
            pass = Arrays.equals((int[]) a, (int[]) b);
        } else if (a instanceof short[]) {
            pass = Arrays.equals((short[]) a, (short[]) b);
        } else if (a instanceof long[]) {
            pass = Arrays.equals((long[]) a, (long[]) b);
        } else if (a instanceof double[]) {
            pass = Arrays.equals((double[]) a, (double[]) b);
        }

        if (pass) {
            pass(test);
        } else {
            String s = "Incorrect return value; expected ";
            s += collapseArray(a);
            s += " got ";
            s += collapseArray(b);
            fail(test, s);
        }
    }

    public static void main(String[] args) {
        try {
            /* init */
            DBusConnection conn = DBusConnection.getConnection(DBusBusType.SESSION);
            CROSS_TEST_CLIENT_INSTANCE = new CrossTestClient(conn);
            conn.exportObject("/Test", CROSS_TEST_CLIENT_INSTANCE);
            conn.addSigHandler(Binding.TestSignals.Triggered.class, CROSS_TEST_CLIENT_INSTANCE);
            Tests tests = conn.getRemoteObject("org.freedesktop.DBus.Binding.TestServer", "/Test", Tests.class);
            Binding.SingleTests singletests = conn.getRemoteObject("org.freedesktop.DBus.Binding.TestServer", "/Test", Binding.SingleTests.class);
            Peer peer = conn.getRemoteObject("org.freedesktop.DBus.Binding.TestServer", "/Test", Peer.class);
            Introspectable intro = conn.getRemoteObject("org.freedesktop.DBus.Binding.TestServer", "/Test", Introspectable.class);

            Introspectable rootintro = conn.getRemoteObject("org.freedesktop.DBus.Binding.TestServer", "/", Introspectable.class);

            doTests(peer, intro, rootintro, tests, singletests);

            /* report results */
            for (String s : PASSED) {
                System.out.println(s + " pass");
            }
            int i = 1;
            for (String s : FAILED.keySet()) {
                for (String r : FAILED.get(s)) {
                    System.out.println(s + " fail " + i);
                    System.out.println("report " + i + ": " + r);
                    i++;
                }
            }

            conn.disconnect();
        } catch (DBusException exDbe) {
            exDbe.printStackTrace();
            System.exit(1);
        }
    }
}
