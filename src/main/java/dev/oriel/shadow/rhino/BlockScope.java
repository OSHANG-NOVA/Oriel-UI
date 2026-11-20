/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.oriel.shadow.rhino;

/**
 * The scope of a block that declares let, const or class bindings, when they cannot be kept in
 * local variables. It works like a {@code with} scope over an object holding the bindings, except
 * that the object has no prototype, so names such as {@code toString} are not found in it, and a
 * function called through one of its bindings does not get the object as {@code this}.
 */
final class BlockScope extends NativeWith {
    private static final long serialVersionUID = 1L;

    BlockScope(Scriptable parent, Scriptable bindings) {
        super(parent, bindings);
    }

    @Override
    public String getClassName() {
        return "Block";
    }

    /**
     * Enters a block scope over {@code bindings}, an object literal holding the block's bindings.
     * The names in {@code constNames}, separated by spaces, become uninitialized constants.
     */
    static Scriptable enter(Object bindings, String constNames, Scriptable parent) {
        ScriptableObject obj = (ScriptableObject) bindings;
        obj.setPrototype(null);
        if (!constNames.isEmpty()) {
            for (String name : constNames.split(" ", -1)) {
                obj.delete(name);
                obj.defineConst(name, obj);
            }
        }
        return new BlockScope(parent, obj);
    }

    /** Makes a block scope with the same parent and a copy of this scope's bindings. */
    BlockScope copy() {
        ScriptableObject from = (ScriptableObject) getPrototype();
        NativeObject to = new NativeObject();
        to.setParentScope(from.getParentScope());
        for (Object id : from.getAllIds()) {
            String name = (String) id;
            int attributes = from.getAttributes(name);
            Object value = from.get(name, from);
            if ((attributes & ScriptableObject.UNINITIALIZED_CONST) != 0) {
                to.defineConst(name, to);
            } else {
                to.defineProperty(name, value, attributes);
            }
        }
        to.setPrototype(null);
        return new BlockScope(getParentScope(), to);
    }
}
