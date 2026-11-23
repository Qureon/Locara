package com.qureon.locara.core.driver;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Shared Java 17+ accessible reflection lookup utility.
 * Resolves public methods declared on accessible public classes or interfaces so
 * dynamic invocations against package-private runtime implementations (e.g. Playwright
 * PageImpl/LocatorImpl or dynamic proxies) do not fail with IllegalAccessException.
 */
public final class ReflectiveAccessUtil {

    private ReflectiveAccessUtil() {}

    public static Method findAccessibleMethod(Class<?> targetClass, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        if (targetClass == null) {
            throw new NoSuchMethodException(name);
        }
        if (Modifier.isPublic(targetClass.getModifiers())) {
            try {
                return targetClass.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }
        Method ifaceMethod = findMethodOnPublicInterfaces(targetClass, name, paramTypes);
        if (ifaceMethod != null) {
            return ifaceMethod;
        }
        for (Class<?> cur = targetClass.getSuperclass(); cur != null; cur = cur.getSuperclass()) {
            if (Modifier.isPublic(cur.getModifiers())) {
                try {
                    return cur.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
            Method superIface = findMethodOnPublicInterfaces(cur, name, paramTypes);
            if (superIface != null) {
                return superIface;
            }
        }
        return targetClass.getMethod(name, paramTypes);
    }

    private static Method findMethodOnPublicInterfaces(Class<?> clazz, String name, Class<?>... paramTypes) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (Modifier.isPublic(iface.getModifiers())) {
                try {
                    return iface.getMethod(name, paramTypes);
                } catch (NoSuchMethodException ignored) {}
            }
            Method nested = findMethodOnPublicInterfaces(iface, name, paramTypes);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
