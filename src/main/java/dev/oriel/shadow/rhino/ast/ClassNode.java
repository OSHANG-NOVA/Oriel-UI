/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.oriel.shadow.rhino.ast;

import dev.oriel.shadow.rhino.Token;

/**
 * A class declaration or expression. Node type is {@link Token#CLASS}.
 *
 * <p>The members are kept as two object literals: one holds the constructor, methods and accessors
 * of the prototype, the other the static methods and accessors. Field initializers and static
 * blocks are gathered into methods of those literals, which the class runs on each new instance and
 * once on the class.
 */
public class ClassNode extends AstNode {

    private Name className;
    private AstNode heritage;
    private ObjectLiteral prototypeMembers;
    private ObjectLiteral staticMembers;
    private boolean hasConstructor;
    private Scope innerScope;

    {
        type = Token.CLASS;
    }

    public ClassNode() {}

    public ClassNode(int pos) {
        super(pos);
    }

    public ClassNode(int pos, int len) {
        super(pos, len);
    }

    /** Returns the class name, or {@code null} for an anonymous class expression. */
    public Name getClassName() {
        return className;
    }

    public void setClassName(Name className) {
        this.className = className;
        if (className != null) className.setParent(this);
    }

    /** Returns the expression after {@code extends}, or {@code null}. */
    public AstNode getHeritage() {
        return heritage;
    }

    public void setHeritage(AstNode heritage) {
        this.heritage = heritage;
        if (heritage != null) heritage.setParent(this);
    }

    public ObjectLiteral getPrototypeMembers() {
        return prototypeMembers;
    }

    public void setPrototypeMembers(ObjectLiteral prototypeMembers) {
        assertNotNull(prototypeMembers);
        this.prototypeMembers = prototypeMembers;
        prototypeMembers.setParent(this);
    }

    public ObjectLiteral getStaticMembers() {
        return staticMembers;
    }

    public void setStaticMembers(ObjectLiteral staticMembers) {
        assertNotNull(staticMembers);
        this.staticMembers = staticMembers;
        staticMembers.setParent(this);
    }

    /** Whether the prototype members include an explicit {@code constructor} method. */
    public boolean hasConstructor() {
        return hasConstructor;
    }

    public void setHasConstructor(boolean hasConstructor) {
        this.hasConstructor = hasConstructor;
    }

    /**
     * Returns the scope that binds a class expression's own name inside the class, or {@code null}.
     */
    public Scope getInnerScope() {
        return innerScope;
    }

    public void setInnerScope(Scope innerScope) {
        this.innerScope = innerScope;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("class");
        if (className != null) {
            sb.append(" ").append(className.toSource(0));
        }
        if (heritage != null) {
            sb.append(" extends ").append(heritage.toSource(0));
        }
        sb.append(" { ").append(prototypeMembers.toSource(0));
        sb.append(" static ").append(staticMembers.toSource(0)).append(" }");
        return sb.toString();
    }

    /** Visits this node, the name, the heritage, then the member literals. */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (className != null) className.visit(v);
            if (heritage != null) heritage.visit(v);
            prototypeMembers.visit(v);
            staticMembers.visit(v);
        }
    }
}
