package dev.oriel.shadow.rhino.xml;

import dev.oriel.shadow.rhino.ScriptableObject;

/** This interface is used to load the XML implementation using the ServiceLoader pattern. */
public interface XMLLoader {
    void load(ScriptableObject scope, boolean sealed);

    XMLLib.Factory getFactory();
}
