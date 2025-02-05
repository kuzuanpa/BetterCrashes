/*
 * This file is from
 * https://github.com/DimensionalDevelopment/VanillaFix/blob/master/src/main/java/org/dimdev/utils/ModIdentifier.java
 * The source file uses the MIT License.
 */

package vfyjxf.bettercrashes.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import vfyjxf.bettercrashes.BetterCrashes;

/**
 * @author Runemoro
 */
public final class ModIdentifier {

    private static final Logger log = LogManager.getLogger();

    public static Set<ModContainer> identifyFromStacktrace(Throwable e) {
        Map<File, Set<ModContainer>> modMap = makeModMap();

        // Get the set of classes
        HashSet<String> classes = new LinkedHashSet<>();
        while (e != null) {
            for (StackTraceElement element : e.getStackTrace()) {
                classes.add(element.getClassName());
            }
            e = e.getCause();
        }

        Set<ModContainer> mods = new LinkedHashSet<>();
        for (String className : classes) {
            Set<ModContainer> classMods = identifyFromClass(className, modMap);
            if (classMods != null) mods.addAll(classMods);
        }
        return mods;
    }

    public static Set<ModContainer> identifyFromClass(String className) {
        return identifyFromClass(className, makeModMap());
    }

    private static Set<ModContainer> identifyFromClass(String className, Map<File, Set<ModContainer>> modMap) {
        // Skip identification for Mixin, one's mod copy of the library is shared with all other mods
        if (className.startsWith("org.spongepowered.asm.mixin.")) return Collections.emptySet();

        // Get the URL of the class
        final String untrasformedName = untransformName(Launch.classLoader, className);
        URL url = Launch.classLoader.getResource(untrasformedName.replace('.', '/') + ".class");
        if (url == null) {
            log.warn("Failed to identify " + className + " (untransformed name: " + untrasformedName + ")");
            return Collections.emptySet();
        }

        // Get the mod containing that class
        try {
            if (url.getProtocol().equals("jar")) {
                url = new URL(url.getFile().substring(0, url.getFile().indexOf('!')));
            }
            URI uri = url.toURI();
            if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")) {
                return modMap.get(new File(uri).getCanonicalFile());
            }
        } catch (URISyntaxException | IOException e) {
            BetterCrashes.logger.error("Error processing URL " + url);
            e.printStackTrace();
        }
        return Collections.emptySet();
    }

    private static Map<File, Set<ModContainer>> makeModMap() {
        Map<File, Set<ModContainer>> modMap = new HashMap<>();
        for (ModContainer mod : Loader.instance().getModList()) {
            Set<ModContainer> currentMods = modMap.getOrDefault(mod.getSource(), new HashSet<>());
            currentMods.add(mod);
            try {
                modMap.put(mod.getSource().getCanonicalFile(), currentMods);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            modMap.remove(Loader.instance().getMinecraftModContainer().getSource()); // Ignore minecraft jar (minecraft)
            modMap.remove(Loader.instance().getIndexedModList().get("FML").getSource()); // Ignore forge jar (FML,
            // forge)
        } catch (NullPointerException ignored) {
            // Workaround for https://github.com/MinecraftForge/MinecraftForge/issues/4919
        }

        return modMap;
    }

    private static String untransformName(LaunchClassLoader launchClassLoader, String className) {
        try {
            Method untransformNameMethod = LaunchClassLoader.class.getDeclaredMethod("untransformName", String.class);
            untransformNameMethod.setAccessible(true);
            return (String) untransformNameMethod.invoke(launchClassLoader, className);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
