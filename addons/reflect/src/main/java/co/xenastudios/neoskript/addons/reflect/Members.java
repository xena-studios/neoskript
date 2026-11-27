/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A class's members as skript-reflect looks them up: its public ones, its superclasses' public ones, and its own
 * non-public ones, made accessible where Java allows. A member Java won't open (in a module that isn't open) falls back
 * to the same method on a superclass or interface, as in skript-reflect.
 */
final class Members {

    private Members() {}

    static List<Field> fields(Class<?> type) {
        Set<Field> all = new LinkedHashSet<>(Arrays.asList(type.getFields()));
        all.addAll(Arrays.asList(type.getDeclaredFields()));
        return new ArrayList<>(all);
    }

    static List<Method> methods(Class<?> type) {
        Set<Method> all = new LinkedHashSet<>(Arrays.asList(type.getMethods()));
        all.addAll(Arrays.asList(type.getDeclaredMethods()));
        return new ArrayList<>(all);
    }

    static List<Constructor<?>> constructors(Class<?> type) {
        return Arrays.asList(type.getDeclaredConstructors());
    }

    static boolean isStatic(Member member) {
        return Modifier.isStatic(member.getModifiers());
    }

    /** {@code member}, made accessible, or the same method on a supertype if Java won't open it, or {@code null}. */
    @SuppressWarnings("unchecked")
    static <T extends AccessibleObject> T access(T member) {
        try {
            member.setAccessible(true);
            return member;
        } catch (RuntimeException e) {
            if (member instanceof Method method && !Modifier.isStatic(method.getModifiers())) {
                Method up = superMethod(method, method.getDeclaringClass());
                return up == null ? null : (T) access(up);
            }
            return null;
        }
    }

    private static Method superMethod(Method method, Class<?> type) {
        if (method.getDeclaringClass() != type) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(method.getName())
                        && Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes())) {
                    return candidate;
                }
            }
        }
        List<Class<?>> supers = new ArrayList<>();
        if (type.getSuperclass() != null) {
            supers.add(type.getSuperclass());
        }
        supers.addAll(Arrays.asList(type.getInterfaces()));
        for (Class<?> up : supers) {
            Method found = superMethod(method, up);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
