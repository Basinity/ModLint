package com.modlint.core.model;

/**
 * One intrusive Mixin injection found in a mod's bytecode: which mixin class instruments
 * which target class and method, and with which injector annotation ({@code Overwrite},
 * {@code Redirect}, {@code ModifyConstant}, {@code ModifyArg(s)}). Cooperative injectors
 * like {@code @Inject} are deliberately not collected; they stack safely.
 * {@code targetMethod} is the bare method name, descriptors stripped.
 */
public record MixinInjection(String mixinClass, String targetClass, String targetMethod, String injector) {
}
