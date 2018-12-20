// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.actors;

import static io.vlingo.common.compiler.DynaFile.GeneratedSources;
import static io.vlingo.common.compiler.DynaFile.GeneratedTestSources;
import static io.vlingo.common.compiler.DynaFile.RootOfMainClasses;
import static io.vlingo.common.compiler.DynaFile.RootOfTestClasses;
import static io.vlingo.common.compiler.DynaFile.toFullPath;
import static io.vlingo.common.compiler.DynaFile.toPackagePath;
import static io.vlingo.common.compiler.DynaNaming.classnameFor;
import static io.vlingo.common.compiler.DynaNaming.fullyQualifiedClassnameFor;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vlingo.common.compiler.DynaFile;
import io.vlingo.common.compiler.DynaType;

public class ProxyGenerator implements AutoCloseable {
  public static class Result {
    public final String classname;
    public final String fullyQualifiedClassname;
    public final String source;
    public final File sourceFile;

    private Result(final String fullyQualifiedClassname, final String classname, final String source, final File sourceFile) {
      this.fullyQualifiedClassname = fullyQualifiedClassname;
      this.classname = classname;
      this.source = source;
      this.sourceFile = sourceFile;
    }
  }

  private final boolean persist;
  private final String rootOfGenerated;
  private final File targetClassesPath;
  private final DynaType type;
  private final URLClassLoader urlClassLoader;

  public static ProxyGenerator forMain(final boolean persist) throws Exception {
    final String root = Properties.properties.getProperty("proxy.generated.classes.main", RootOfMainClasses);
    return new ProxyGenerator(root, DynaType.Main, persist);
  }

  public static ProxyGenerator forTest(final boolean persist) throws Exception {
    final String root = Properties.properties.getProperty("proxy.generated.classes.test", RootOfTestClasses);
    return new ProxyGenerator(root, DynaType.Test, persist);
  }

  @Override
  public void close() throws Exception {
    urlClassLoader.close();
  }

  public Result generateFor(final String actorProtocol) {
    System.out.println("vlingo/actors: Generating proxy for " + (type == DynaType.Main ? "main":"test") + ": " + actorProtocol);

    try {
      final Class<?> protocolInterface = readProtocolInterface(actorProtocol);
      final String proxyClassSource = proxyClassSource(protocolInterface);
      final String fullyQualifiedClassname = fullyQualifiedClassnameFor(protocolInterface, "__Proxy");
      final String relativeTargetFile = toFullPath(fullyQualifiedClassname);
      final File sourceFile = persist ? persistProxyClassSource(actorProtocol, relativeTargetFile, proxyClassSource) : new File(relativeTargetFile);
      return new Result(fullyQualifiedClassname, classnameFor(protocolInterface, "__Proxy"), proxyClassSource, sourceFile);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot generate proxy class for: " + actorProtocol, e);
    }
  }

  DynaType type() {
    return type;
  }

  URLClassLoader urlClassLoader() {
    return urlClassLoader;
  }

  private ProxyGenerator(final String rootOfClasses, final DynaType type, final boolean persist) throws Exception {
    this.rootOfGenerated = rootOfGeneratedSources(type);
    this.type = type;
    this.persist = persist;
    this.targetClassesPath = new File(rootOfClasses);
    this.urlClassLoader = initializeClassLoader(targetClassesPath);
  }

  private String classStatement(final Class<?> protocolInterface) {
    return GenericParser.implementsInterfaceTemplateOf(classnameFor(protocolInterface, "__Proxy"), protocolInterface) + " {\n";
  }

  private String constructor(final Class<?> protocolInterface) {
    final StringBuilder builder = new StringBuilder();

    final String signature = MessageFormat.format("  public {0}(final Actor actor, final Mailbox mailbox)", classnameFor(protocolInterface, "__Proxy"));

    builder
      .append(signature).append("{\n")
      .append("    this.actor = actor;").append("\n")
      .append("    this.mailbox = mailbox;").append("\n")
      .append("  }\n");

    return builder.toString();
  }

  private String importStatements(final Class<?> protocolInterface) {
    final StringBuilder builder = new StringBuilder();

    builder
      .append("import io.vlingo.actors.Actor;").append("\n")
      .append("import io.vlingo.actors.DeadLetter;").append("\n")
      .append("import io.vlingo.actors.LocalMessage;").append("\n")
      .append("import io.vlingo.actors.Mailbox;").append("\n")
      .append("import io.vlingo.common.BasicCompletes;").append("\n")
      .append("import ").append(protocolInterface.getCanonicalName()).append(";\n");

    GenericParser.dependenciesOf(protocolInterface)
            .map(type1 -> "import " + type1 + ";\n")
            .collect(Collectors.toSet()).forEach(builder::append);
    return builder.toString();
  }

  private URLClassLoader initializeClassLoader(final File targetClassesPath) throws MalformedURLException {
    final String classpath = "file://" + targetClassesPath.getAbsolutePath() + "/";
    final URL url = new URL(classpath);
    final URLClassLoader urlClassLoader = new URLClassLoader(new URL[] { url });
    return urlClassLoader;
  }

  private String instanceVariables(final Class<?> protocolInterface) {
    final StringBuilder builder = new StringBuilder();

    builder
      .append("  private final Actor actor;").append("\n")
      .append("  private final Mailbox mailbox;").append("\n");

    return builder.toString();
  }

  private String methodDefinition(final Class<?> protocolInterface, final Method method, final int count) {
    final StringBuilder builder = new StringBuilder();

    final String genericTemplate = GenericParser.genericTemplateOf(method);
    final String parameterTemplate = GenericParser.parametersTemplateOf(method);
    final String signatureReturnType = GenericParser.returnTypeOf(method);
    final boolean isACompletes = signatureReturnType.startsWith("io.vlingo.common.Completes");

    final String methodSignature = MessageFormat.format("  public {0}{1} {2}{3}", genericTemplate, signatureReturnType, method.getName(), parameterTemplate);
    final String throwsExceptions = throwsExceptions(method);
    final String ifNotStopped = "    if (!actor.isStopped()) {";
    final String consumerStatement = MessageFormat.format("      final java.util.function.Consumer<{0}> consumer = (actor) -> actor.{1}{2};", protocolInterface.getSimpleName(), method.getName(), parameterNamesFor(method));
    final String completesStatement = isACompletes ? MessageFormat.format("      final {0} completes = new BasicCompletes<>(actor.scheduler());\n", signatureReturnType) : "";
    final String representationName = MessageFormat.format("{0}Representation{1}", method.getName(), count);
    final String preallocatedMailbox =  MessageFormat.format("      if (mailbox.isPreallocated()) '{' mailbox.send(actor, {0}.class, consumer, {1}{2}); '}'", protocolInterface.getSimpleName(), isACompletes ? "completes, ":"null, ", representationName);
    final String mailboxSendStatement = MessageFormat.format("      else '{' mailbox.send(new LocalMessage<{0}>(actor, {0}.class, consumer, {1}{2})); '}'", protocolInterface.getSimpleName(), isACompletes ? "completes, ":"", representationName);
    final String completesReturnStatement = isACompletes ? "      return completes;\n" : "";
    final String elseDead = MessageFormat.format("      actor.deadLetters().failedDelivery(new DeadLetter(actor, {0}));", representationName);
    final String returnValue = returnValue(method.getReturnType());
    final String returnStatement = returnValue.isEmpty() ? "" : MessageFormat.format("    return {0};\n", returnValue);

    builder
      .append(methodSignature).append(throwsExceptions).append(" {\n")
      .append(ifNotStopped).append("\n")
      .append(consumerStatement).append("\n")
      .append(completesStatement)
      .append(preallocatedMailbox).append("\n")
      .append(mailboxSendStatement).append("\n")
      .append(completesReturnStatement)
      .append("    } else {\n")
      .append(elseDead).append("\n")
      .append("    }\n")
      .append(returnStatement)
      .append("  }\n");

    return builder.toString();
  }

  private String methodDefinitions(final Class<?> protocolInterface, final Method[] methods) {
    final StringBuilder builder = new StringBuilder();

    int count = 0;

    for (final Method method : methods) {
      if (!Modifier.isStatic(method.getModifiers())) {
        builder.append(methodDefinition(protocolInterface, method, ++count));
      }
    }

    return builder.toString();
  }

  private String packageStatement(final Class<?> protocolInterface) {
    return MessageFormat.format("package {0};", protocolInterface.getPackage().getName());
  }

  private String parameterNamesFor(final Method method) {
    return Arrays.stream(method.getParameters()).map(Parameter::getName).collect(Collectors.joining(", ", "(", ")"));
  }

  private String parameterTypesFor(final Method method) {
    final StringBuilder builder = new StringBuilder();

    String separator = ", ";
    int parameterIndex = 0;
    final Parameter[] parameters = method.getParameters();

    for (final Parameter parameter : parameters) {
      final Type type = parameter.getParameterizedType();
      builder.append(type.getTypeName().replace('$', '.'));
      if (++parameterIndex < parameters.length) {
        builder.append(separator);
      }
    }

    return builder.toString();
  }

  private File persistProxyClassSource(final String actorProtocol, final String relativePathToClass, final String proxyClassSource) throws Exception {
    final String pathToGeneratedSource = toPackagePath(actorProtocol);
    new File(rootOfGenerated + pathToGeneratedSource).mkdirs();
    final String pathToSource = rootOfGenerated + relativePathToClass + ".java";

    return DynaFile.persistDynaClassSource(pathToSource, proxyClassSource);
  }

  private String proxyClassSource(final Class<?> protocolInterface) {
    final Method[] methods = protocolInterface.getMethods();

    final StringBuilder builder = new StringBuilder();

    builder
      .append(packageStatement(protocolInterface)).append("\n\n")
      .append(importStatements(protocolInterface)).append("\n")
      .append(classStatement(protocolInterface)).append("\n")
      .append(representationStatements(methods)).append("\n")
      .append(instanceVariables(protocolInterface)).append("\n")
      .append(constructor(protocolInterface)).append("\n")
      .append(methodDefinitions(protocolInterface, methods))
      .append("}").append("\n");

    return builder.toString();
  }

  private Class<?> readProtocolInterface(final String actorProtocol) throws Exception {
    return urlClassLoader.loadClass(actorProtocol);
  }

  private String representationStatements(final Method[] methods) {
    final StringBuilder builder = new StringBuilder();

    int count = 0;

    for (final Method method : methods) {
      if (!Modifier.isStatic(method.getModifiers())) {
        final String statement =
                MessageFormat.format(
                        "  private static final String {0}Representation{1} = \"{0}({2})\";\n",
                        method.getName(),
                        ++count,
                        parameterTypesFor(method));

        builder.append(statement);
      }
    }

    return builder.toString();
  }

  private String returnValue(final Class<?> returnType) {
    if (returnType.getName().equals("void")) {
      return "";
    }
    if (returnType.isPrimitive()) {
      switch (returnType.getName()) {
      case "boolean":
        return "false";
      case "int":
      case "long":
      case "byte":
      case "double":
      case "float":
      case "short":
        return "0";
      case "char":
        return "'\\0'";
      }
    }
    return "null";
  }

  private String throwsExceptions(final Method method) {
    final StringBuilder builder = new StringBuilder();

    boolean first = true;

    for (final Class<?> exceptionType : method.getExceptionTypes()) {
      if (first) {
        builder.append(" throws ");
      } else {
        builder.append(", ");
      }

      first = false;

      builder.append(exceptionType.getName());
    }

    return builder.toString();
  }

  private String rootOfGeneratedSources(final DynaType type) {
    return type == DynaType.Main ?
            Properties.properties.getProperty("proxy.generated.sources.main", GeneratedSources) :
            Properties.properties.getProperty("proxy.generated.sources.test", GeneratedTestSources);
  }

  public static final class GenericParser {
    private static final Map<String, Boolean> PRIMITIVES = new HashMap<String, Boolean>() {
      private static final long serialVersionUID = 1L;
    {
        put("byte", true);
        put("short", true);
        put("int", true);
        put("long", true);
        put("char", true);
        put("float", true);
        put("double", true);
        put("boolean", true);
        put("void", true);
    }};

    private GenericParser() {
    }

    public static Stream<String> genericReferencesOf(final Method method) {
        return Stream.concat(
                Stream.concat(
                        Stream.of(method.getGenericReturnType()),
                        Arrays.stream(method.getGenericParameterTypes())
                ),
                Stream.of(method.getClass())
        ).flatMap(GenericParser::genericReferencesOf);
    }

    public static Stream<String> dependenciesOf(final Class<?> classRef) {
        return Arrays.stream(classRef.getMethods())
                .flatMap(GenericParser::dependenciesOf)
                .filter(GenericParser::onlyNotPrimitives)
                .map(GenericParser::normalizeTypeName);
    }

    public static Stream<String> dependenciesOf(final Method method) {
        final Set<String> genericTypeAlias = genericReferencesOf(method).collect(Collectors.toSet());

        return Stream.concat(Arrays.stream(method.getGenericParameterTypes()), Stream.of(method.getGenericReturnType()))
                .flatMap(GenericParser::typeNameToTypeStream)
                .filter(type -> !genericTypeAlias.contains(normalizeTypeAlias(type)))
                .filter(GenericParser::onlyNotPrimitives)
                .map(GenericParser::normalizeTypeName);
    }

    public static String genericTemplateOf(final Method method) {
        final Set<String> knownAlias = Arrays.stream(method.getDeclaringClass().getTypeParameters())
                .flatMap(GenericParser::genericReferencesOf)
                .collect(Collectors.toSet());

        return allTypesOfMethodSignature(method)
                .filter(type -> type instanceof TypeVariable || type instanceof ParameterizedType)
                .flatMap(type -> typeToGenericString(knownAlias, type))
                .distinct()
                .sorted()
                .map(GenericParser::normalizeTypeName)
                .collect(Collectors.joining(", ", "<", ">"))
                .replace("<>", "");
    }

    public static String parametersTemplateOf(final Method method) {
        return Arrays.stream(method.getParameters())
                .map(param -> String.format("%s %s", normalizeTypeName(param.getParameterizedType().getTypeName()), param.getName()))
                .collect(Collectors.joining(", ", "(", ")"));
    }

    public static String implementsInterfaceTemplateOf(final String newClassName, final Class<?> classToExtend) {
        final StringBuilder template = new StringBuilder("public class ").append(newClassName);

        template.append(
                Arrays.stream(classToExtend.getTypeParameters())
                        .flatMap(type -> typeToGenericString(new HashSet<>(), type))
                        .collect(Collectors.joining(", ", "<", ">"))
                        .replace("<>", "")
        );

        template.append(" implements ").append(classToExtend.getCanonicalName());

        template.append(
                Arrays.stream(classToExtend.getTypeParameters())
                        .flatMap(GenericParser::genericReferencesOf)
                        .collect(Collectors.joining(", ", "<", ">"))
                        .replace("<>", "")
        );

        return template.toString();
    }

    public static String returnTypeOf(final Method method) {
        return normalizeTypeName(method.getGenericReturnType().getTypeName());
    }

    @SuppressWarnings("rawtypes")
    private static Stream<String> typeToGenericString(final Set<String> classAlias, final Type type) {
        if (type instanceof TypeVariable) {
            final TypeVariable typeVariable = (TypeVariable) type;
            final String boundaryType = typeVariable.getBounds()[0].getTypeName();
            final String genericAlias = typeVariable.getTypeName();

            if (classAlias.contains(normalizeTypeAlias(genericAlias))) {
                return Stream.empty();
            }

            if (boundaryType.equals("java.lang.Object")) {
                return Stream.of(genericAlias);
            }

            return Stream.of(String.format("%s extends %s", genericAlias, normalizeTypeName(boundaryType)));
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType paramType = (ParameterizedType) type;
            return Arrays.stream(paramType.getActualTypeArguments())
                    .flatMap(arg -> typeToGenericString(classAlias, arg));
        }

        return Stream.empty();
    }

    @SuppressWarnings("rawtypes")
    private static Stream<String> genericReferencesOf(final Type type) {
        if (type instanceof TypeVariable) {
            final TypeVariable variable = (TypeVariable) type;
            return Stream.of(variable.getName());
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType paramType = (ParameterizedType) type;
            return Arrays.stream(paramType.getActualTypeArguments())
                    .flatMap(GenericParser::genericReferencesOf);
        }

        return Stream.empty();
    }

    private static Stream<Type> allTypesOfMethodSignature(final Method method) {
        return Stream.concat(
                Stream.concat(
                        Arrays.stream(method.getGenericParameterTypes()),
                        Stream.of(method.getGenericReturnType())
                ),
                Arrays.stream(method.getGenericExceptionTypes())
        );
    }

    private static String normalizeTypeAlias(final String typeName) {
        return typeName.replace("[]", "");
    }

    private static String normalizeTypeName(final String typeName) {
        return typeName.replace("$", ".");
    }

    private static boolean onlyNotPrimitives(final String type) {
        return !PRIMITIVES.getOrDefault(normalizeTypeAlias(type), false);
    }

    @SuppressWarnings("rawtypes")
    private static Stream<String> typeNameToTypeStream(final Type type) {
        if (type instanceof TypeVariable) {
            return Arrays.stream(((TypeVariable) type).getBounds())
                    .flatMap(GenericParser::typeNameToTypeStream);
        } else if (type instanceof ParameterizedType) {
            final ParameterizedType paramType = (ParameterizedType) type;
            return Stream.concat(
                    Arrays.stream(paramType.getActualTypeArguments()).flatMap(GenericParser::typeNameToTypeStream),
                    typeNameToTypeStream(paramType.getRawType())
            );
        }

        return Arrays.stream(type.getTypeName().replaceAll("[<>]", "==").split("=="));
    }
  }
}
