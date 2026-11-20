package dev.oriel.engine.script;

import dev.oriel.engine.dom.Document;
import org.jspecify.annotations.Nullable;

import java.util.ServiceLoader;
import java.util.function.Function;

/** Script runtimes for hosts: {@code createScriptRuntime(doc)} returns {@code Scripting.rhino().apply(doc)}. */
public final class Scripting {
    private Scripting() {}

    /**
     * The sandboxed JavaScript runtime (Mozilla Rhino, relocated): DOM bindings, the {@code oriel} API and templates.
     * See {@code docs/zh/scripting.md}.
     */
    public static Function<Document, ScriptRuntime> rhino() {
        ScriptRuntimeProvider found = find();
        if (found == null) throw new IllegalStateException("No rhino script runtime provider is installed; add oriel-script-rhino");
        return found::create;
    }

    /**
     * Whether a rhino provider is installed, so a host can fall back to rendering pages without scripts instead of
     * failing: a null runtime from {@code Host.createScriptRuntime} makes document loading skip every
     * {@code <script>} element. The 1.20.1 port ships without the rhino and script-rhino modules, so this is false and
     * pages render their markup and styles only.
     */
    public static boolean isRhinoAvailable() {
        return find() != null;
    }

    private static @Nullable ScriptRuntimeProvider find() {
        ScriptRuntimeProvider found = null;
        for (ScriptRuntimeProvider provider : ServiceLoader.load(ScriptRuntimeProvider.class, Scripting.class.getClassLoader())) {
            if (!provider.id().equals("rhino")) continue;
            if (found != null) throw new IllegalStateException("Multiple rhino script runtime providers are installed");
            found = provider;
        }
        return found;
    }
}
