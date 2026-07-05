package com.modlint.testutil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Builds synthetic mod jars carrying deep content: mixin classes, resources, access wideners. */
public final class DeepJars {

    private DeepJars() {
    }

    /** A builder for one synthetic mod jar with arbitrary extra entries. */
    public static final class ModJar {

        private final String id;
        private final String version;
        private final Map<String, byte[]> entries = new LinkedHashMap<>();
        private final StringBuilder extraMetadata = new StringBuilder();

        public ModJar(String id, String version) {
            this.id = id;
            this.version = version;
        }

        /** Adds a mixin config plus one mixin class with one intrusive injector method. */
        public ModJar withMixin(String mixinClassName, String targetClass, String injectorDesc,
                                String mixinMethodName, String targetMethodSelector) {
            String configName = id + ".mixins.json";
            int lastDot = mixinClassName.lastIndexOf('.');
            String pkg = mixinClassName.substring(0, lastDot);
            String simpleName = mixinClassName.substring(lastDot + 1);
            entries.put(configName, ("{ \"package\": \"" + pkg + "\", \"mixins\": [\"" + simpleName + "\"] }")
                    .getBytes(StandardCharsets.UTF_8));
            entries.put(mixinClassName.replace('.', '/') + ".class",
                    mixinClass(mixinClassName, targetClass, injectorDesc, mixinMethodName, targetMethodSelector));
            extraMetadata.append(", \"mixins\": [\"").append(configName).append("\"]");
            return this;
        }

        /** Adds a plain file entry (a resource, for instance). */
        public ModJar withEntry(String path, String content) {
            entries.put(path, content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /** Adds an access widener file and declares it in the metadata. */
        public ModJar withAccessWidener(String content) {
            String name = id + ".accesswidener";
            entries.put(name, content.getBytes(StandardCharsets.UTF_8));
            extraMetadata.append(", \"accessWidener\": \"").append(name).append("\"");
            return this;
        }

        public void write(Path jar) throws IOException {
            String metadata = "{ \"schemaVersion\": 1, \"id\": \"" + id + "\", \"version\": \"" + version + "\""
                    + extraMetadata + " }";
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new JarEntry("fabric.mod.json"));
                out.write(metadata.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    out.putNextEntry(new JarEntry(entry.getKey()));
                    out.write(entry.getValue());
                    out.closeEntry();
                }
            }
        }
    }

    /**
     * Generates a mixin class: {@code @Mixin(targets = targetClass)} on the class, and the
     * given injector annotation on one method. For {@code @Overwrite} the method name is the
     * target; the other injectors carry {@code method = [targetMethodSelector]}.
     */
    private static byte[] mixinClass(String className, String targetClass, String injectorDesc,
                                     String mixinMethodName, String targetMethodSelector) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className.replace('.', '/'), null, "java/lang/Object", null);
        AnnotationVisitor mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor targets = mixin.visitArray("targets");
        targets.visit(null, targetClass);
        targets.visitEnd();
        mixin.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, mixinMethodName, "()V", null, null);
        AnnotationVisitor injector = method.visitAnnotation(injectorDesc, false);
        if (!injectorDesc.endsWith("/Overwrite;")) {
            AnnotationVisitor methods = injector.visitArray("method");
            methods.visit(null, targetMethodSelector);
            methods.visitEnd();
        }
        injector.visitEnd();
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
