/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.oriel.shadow.rhino;

import java.util.EnumSet;

/**
 * The constructor of a class. It can only be called with {@code new}. Its body, the class's {@code
 * constructor} method, runs with a {@link Construction} as {@code this}, which every read of {@code
 * this} resolves to the instance; a derived class gets its instance from {@code super(...)}.
 */
final class ClassFunction extends BaseFunction {
    private static final long serialVersionUID = 1L;

    /** The key under which a class body passes its field initializers to {@link #create}. */
    static final String FIELDS_KEY = "\u0000fields";

    static final int EXTENDS = 1;
    static final int HAS_CONSTRUCTOR = 2;

    private final String name;
    private final JSFunction body;
    private final boolean derived;
    private final Callable fieldInitializer;

    private ClassFunction(
            String name, JSFunction body, boolean derived, Callable fieldInitializer) {
        this.name = name;
        this.body = body;
        this.derived = derived;
        this.fieldInitializer = fieldInitializer;
    }

    /** What a class constructor's {@code this} refers to while it runs. */
    static final class Construction extends ScriptableObject {
        private static final long serialVersionUID = 1L;

        final ClassFunction cls;
        final Scriptable newTarget;
        Scriptable instance;

        Construction(ClassFunction cls, Scriptable newTarget) {
            this.cls = cls;
            this.newTarget = newTarget;
        }

        @Override
        public String getClassName() {
            return "Object";
        }
    }

    /**
     * Creates a class from the object literals holding its prototype members and static members.
     * The prototype literal becomes the class's prototype, so that its methods' home object, used
     * by {@code super}, is right.
     */
    static ClassFunction create(
            Object heritage,
            Object protoLiteral,
            Object staticLiteral,
            int flags,
            String name,
            Context cx,
            Scriptable scope) {
        Scriptable top = ScriptableObject.getTopLevelScope(scope);
        boolean hasHeritage = (flags & EXTENDS) != 0;
        Scriptable protoParent = ScriptableObject.getObjectPrototype(top);
        Scriptable constructorParent = ScriptableObject.getFunctionPrototype(top);
        if (hasHeritage) {
            if (heritage == null) {
                protoParent = null;
            } else {
                if (!AbstractEcmaObjectOperations.isConstructor(cx, heritage)) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.bad.extends", ScriptRuntime.toString(heritage));
                }
                Object parentProto =
                        ScriptableObject.getProperty((Scriptable) heritage, "prototype");
                if (parentProto == null) {
                    protoParent = null;
                } else if (parentProto instanceof Scriptable
                        && !ScriptRuntime.isSymbol(parentProto)
                        && !Undefined.isUndefined(parentProto)) {
                    protoParent = (Scriptable) parentProto;
                } else {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.bad.extends", ScriptRuntime.toString(heritage));
                }
                constructorParent = (Scriptable) heritage;
            }
        }

        ScriptableObject proto = (ScriptableObject) protoLiteral;
        ScriptableObject statics = (ScriptableObject) staticLiteral;
        JSFunction body = null;
        if ((flags & HAS_CONSTRUCTOR) != 0) {
            body = (JSFunction) proto.get("constructor", proto);
            proto.delete("constructor");
        }
        Callable fields = take(proto, FIELDS_KEY);
        Callable staticInitializer = take(statics, FIELDS_KEY);

        ClassFunction cls = new ClassFunction(name, body, hasHeritage, fields);
        ScriptRuntime.setFunctionProtoAndParent(cls, cx, scope, false);
        cls.setPrototype(constructorParent);

        proto.setPrototype(protoParent);
        for (Object id : allIds(proto)) {
            hide(proto, id);
        }
        proto.defineProperty("constructor", cls, DONTENUM);
        cls.setPrototypeProperty(proto);
        cls.setPrototypePropertyAttributes(DONTENUM | READONLY | PERMANENT);

        for (Object id : allIds(statics)) {
            DescriptorInfo d = statics.getOwnPropertyDescriptor(cx, id);
            cls.defineOwnProperty(
                    cx,
                    id,
                    new DescriptorInfo(
                            Boolean.FALSE,
                            d.writable,
                            d.configurable,
                            d.getter,
                            d.setter,
                            d.value));
        }
        // static methods find the parent's statics through super
        statics.setPrototype(constructorParent);

        if (staticInitializer != null) {
            staticInitializer.call(cx, scope, cls, ScriptRuntime.emptyArgs);
        }
        return cls;
    }

    private static Object[] allIds(ScriptableObject obj) {
        try (var map = obj.startCompoundOp(false)) {
            return obj.getIds(map, true, true);
        }
    }

    private static Callable take(ScriptableObject literal, String key) {
        Object value = literal.get(key, literal);
        if (value == NOT_FOUND) {
            return null;
        }
        literal.delete(key);
        return (Callable) value;
    }

    private static void hide(ScriptableObject obj, Object id) {
        if (id instanceof Symbol) {
            obj.setAttributes((Symbol) id, obj.getAttributes((Symbol) id) | DONTENUM);
        } else if (id instanceof Integer) {
            obj.setAttributes((Integer) id, obj.getAttributes((Integer) id) | DONTENUM);
        } else {
            String key = (String) id;
            obj.setAttributes(key, obj.getAttributes(key) | DONTENUM);
        }
    }

    @Override
    public String getClassName() {
        return "Function";
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    @Override
    String decompile(int indent, EnumSet<DecompilerFlag> flags) {
        return name.isEmpty() ? "class { }" : "class " + name + " { }";
    }

    @Override
    public int getLength() {
        return body == null ? 0 : body.getLength();
    }

    @Override
    public int getArity() {
        return getLength();
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        throw ScriptRuntime.typeErrorById("msg.class.not.new", name);
    }

    @Override
    public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
        return construct(cx, scope, args, this);
    }

    /** Runs the constructor for {@code new.target} {@code newTarget}. */
    Scriptable construct(Context cx, Scriptable scope, Object[] args, Scriptable newTarget) {
        Construction construction = new Construction(this, newTarget);
        if (!derived) {
            construction.instance = createInstance(newTarget);
            initializeFields(cx, scope, construction.instance);
        }
        if (body == null) {
            if (derived) {
                superCall(cx, scope, construction, args);
            }
        } else {
            Callable code =
                    (c, s, thisObj, a) ->
                            body.getDescriptor()
                                    .getCode()
                                    .execute(c, body, newTarget, s, thisObj, a);
            Object result =
                    ScriptRuntime.hasTopCall(cx)
                            ? code.call(cx, scope, construction, args)
                            : ScriptRuntime.doTopCall(code, cx, scope, construction, args, true);
            if (ScriptRuntime.isObject(result)) {
                return (Scriptable) result;
            }
            if (derived && !Undefined.isUndefined(result)) {
                throw ScriptRuntime.typeErrorById("msg.class.ctor.result", name);
            }
        }
        if (construction.instance == null) {
            throw referenceError("msg.class.no.super", name);
        }
        return construction.instance;
    }

    private Scriptable createInstance(Scriptable newTarget) {
        Scriptable obj = new NativeObject();
        Object proto = ScriptableObject.getProperty(newTarget, "prototype");
        if (!(proto instanceof Scriptable) || ScriptRuntime.isSymbol(proto)) {
            proto = ScriptableObject.getObjectPrototype(newTarget);
        }
        obj.setPrototype((Scriptable) proto);
        obj.setParentScope(ScriptableObject.getTopLevelScope(this));
        return obj;
    }

    private void initializeFields(Context cx, Scriptable scope, Scriptable instance) {
        if (fieldInitializer != null) {
            fieldInitializer.call(cx, scope, instance, ScriptRuntime.emptyArgs);
        }
    }

    /** {@code super(...args)} in the constructor of a derived class. */
    static Scriptable superCall(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!(thisObj instanceof Construction)) {
            throw ScriptRuntime.syntaxErrorById("msg.class.bad.super");
        }
        Construction construction = (Construction) thisObj;
        if (construction.instance != null) {
            throw referenceError("msg.class.super.twice");
        }
        Scriptable parent = construction.cls.getPrototype();
        if (!AbstractEcmaObjectOperations.isConstructor(cx, parent)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.class.bad.extends", ScriptRuntime.toString(parent));
        }
        Scriptable instance = construct(cx, scope, parent, args, construction.newTarget);
        if (construction.instance != null) {
            throw referenceError("msg.class.super.twice");
        }
        construction.instance = instance;
        construction.cls.initializeFields(cx, scope, instance);
        return instance;
    }

    /** Constructs {@code target} as if by {@code Reflect.construct(target, args, newTarget)}. */
    static Scriptable construct(
            Context cx, Scriptable scope, Scriptable target, Object[] args, Scriptable newTarget) {
        if (target instanceof ClassFunction) {
            return ((ClassFunction) target).construct(cx, scope, args, newTarget);
        }
        if (target instanceof JSFunction && ((JSFunction) target).getConstructor() != null) {
            JSFunction f = (JSFunction) target;
            Scriptable obj = f.createObject(cx, scope);
            if (newTarget != target) {
                Object proto = ScriptableObject.getProperty(newTarget, "prototype");
                if (proto instanceof Scriptable && !ScriptRuntime.isSymbol(proto)) {
                    obj.setPrototype((Scriptable) proto);
                }
            }
            Object result = f.getConstructor().execute(cx, f, newTarget, scope, obj, args);
            return ScriptRuntime.isObject(result) ? (Scriptable) result : obj;
        }
        Scriptable obj = ((Constructable) target).construct(cx, scope, args);
        if (newTarget != target) {
            Object proto = ScriptableObject.getProperty(newTarget, "prototype");
            if (proto instanceof Scriptable && !ScriptRuntime.isSymbol(proto)) {
                obj.setPrototype((Scriptable) proto);
            }
        }
        return obj;
    }

    /** The value of {@code this}: inside a class constructor, the instance being made. */
    static Scriptable resolveThis(Scriptable thisObj) {
        if (thisObj instanceof Construction) {
            Construction construction = (Construction) thisObj;
            if (construction.instance == null) {
                throw referenceError("msg.class.this.before.super");
            }
            return construction.instance;
        }
        return thisObj;
    }

    private static EcmaError referenceError(String messageId, Object... args) {
        return ScriptRuntime.referenceError(ScriptRuntime.getMessageById(messageId, args));
    }

    /** The value of {@code new.target}. */
    static Object newTarget(Scriptable thisObj) {
        if (thisObj instanceof Construction) {
            return ((Construction) thisObj).newTarget;
        }
        return Undefined.instance;
    }
}
