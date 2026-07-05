package com.modlint.core.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modlint.core.model.MixinInjection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads a mod's Mixin config JSONs and the mixin classes they list, extracting the
 * intrusive injections via ASM. Nothing is ever loaded or executed; only bytecode
 * structure is read.
 */
public final class MixinScanner {

    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    private static final Map<String, String> INTRUSIVE_INJECTORS = Map.of(
            "Lorg/spongepowered/asm/mixin/Overwrite;", "@Overwrite",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;", "@Redirect",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", "@ModifyConstant",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "@ModifyArg",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "@ModifyArgs");

    private final JarModReader reader;

    public MixinScanner(JarModReader reader) {
        this.reader = reader;
    }

    /** Extracts every intrusive injection declared by {@code configEntries} of the jar. */
    public List<MixinInjection> scan(Path jar, List<String> configEntries) throws IOException {
        List<MixinInjection> injections = new ArrayList<>();
        for (String configEntry : configEntries) {
            Optional<byte[]> config = reader.readEntry(jar, configEntry);
            if (config.isEmpty()) {
                continue;
            }
            JsonObject root;
            try {
                root = JsonParser.parseString(new String(config.get(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                continue; // A malformed config never fails the scan.
            }
            String pkg = root.has("package") ? root.get("package").getAsString() : "";
            for (String listName : List.of("mixins", "client", "server")) {
                if (!root.has(listName)) {
                    continue;
                }
                for (JsonElement entry : root.getAsJsonArray(listName)) {
                    String className = (pkg.isEmpty() ? "" : pkg + ".") + entry.getAsString();
                    String classEntry = className.replace('.', '/') + ".class";
                    reader.readEntry(jar, classEntry)
                            .ifPresent(bytes -> collectInjections(bytes, className, injections));
                }
            }
        }
        return List.copyOf(injections);
    }

    private static void collectInjections(byte[] classBytes, String className, List<MixinInjection> out) {
        ClassNode node = new ClassNode();
        try {
            new ClassReader(classBytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        } catch (RuntimeException e) {
            return; // Unreadable bytecode never fails the scan.
        }
        List<String> targets = mixinTargets(node);
        if (targets.isEmpty()) {
            return;
        }
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : annotations(method)) {
                String injector = INTRUSIVE_INJECTORS.get(annotation.desc);
                if (injector == null) {
                    continue;
                }
                List<String> methodNames = "@Overwrite".equals(injector)
                        ? List.of(method.name)
                        : targetMethodNames(annotation);
                for (String target : targets) {
                    for (String methodName : methodNames) {
                        out.add(new MixinInjection(className, target, methodName, injector));
                    }
                }
            }
        }
    }

    /** The target classes of the {@code @Mixin} annotation: {@code value} classes plus {@code targets} strings. */
    private static List<String> mixinTargets(ClassNode node) {
        for (AnnotationNode annotation : annotations(node)) {
            if (!MIXIN_ANNOTATION.equals(annotation.desc)) {
                continue;
            }
            List<String> targets = new ArrayList<>();
            for (Object value : annotationValue(annotation, "value")) {
                targets.add(((Type) value).getClassName());
            }
            for (Object value : annotationValue(annotation, "targets")) {
                targets.add(value.toString().replace('/', '.'));
            }
            return targets;
        }
        return List.of();
    }

    /** The {@code method} member of an injector annotation, reduced to bare method names. */
    private static List<String> targetMethodNames(AnnotationNode annotation) {
        List<String> names = new ArrayList<>();
        for (Object value : annotationValue(annotation, "method")) {
            String selector = value.toString();
            int paren = selector.indexOf('(');
            if (paren >= 0) {
                selector = selector.substring(0, paren);
            }
            int owner = selector.lastIndexOf(';');
            if (owner >= 0) {
                selector = selector.substring(owner + 1);
            }
            if (!selector.isEmpty()) {
                names.add(selector);
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> annotationValue(AnnotationNode annotation, String name) {
        List<Object> values = annotation.values == null ? List.of() : annotation.values;
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (name.equals(values.get(i)) && values.get(i + 1) instanceof List) {
                return (List<Object>) values.get(i + 1);
            }
        }
        return List.of();
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        return concat(node.visibleAnnotations, node.invisibleAnnotations);
    }

    private static List<AnnotationNode> annotations(MethodNode node) {
        return concat(node.visibleAnnotations, node.invisibleAnnotations);
    }

    private static List<AnnotationNode> concat(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> all = new ArrayList<>();
        if (a != null) {
            all.addAll(a);
        }
        if (b != null) {
            all.addAll(b);
        }
        return all;
    }
}
