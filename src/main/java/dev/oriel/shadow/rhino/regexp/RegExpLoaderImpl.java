package dev.oriel.shadow.rhino.regexp;

import dev.oriel.shadow.rhino.RegExpLoader;
import dev.oriel.shadow.rhino.RegExpProxy;

/** This class loads the default RegExp implementation. */
public class RegExpLoaderImpl implements RegExpLoader {
    @Override
    public RegExpProxy newProxy() {
        return new RegExpImpl();
    }
}
