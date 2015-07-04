/*
Copyright Michael Kocherov, 2015
http://develorium.com
*/

package com.develorium.metracer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;

import javassist.*;
import javassist.bytecode.*;

public class TracerAdder implements ClassFileTransformer {
	public byte[] transform(ClassLoader loader, String className,
				Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
				byte[] classfileBuffer) throws IllegalClassFormatException {
		final String canonicalClassName = className.replaceAll("/", ".");
		ClassPool cp = ClassPool.getDefault();
		cp.insertClassPath(new ByteArrayClassPath(canonicalClassName, classfileBuffer));
		boolean wasInstrumented = false;
	
		try {
			CtClass cc = cp.get(canonicalClassName);
		
			for(CtMethod method : cc.getDeclaredMethods()) {
				Object[] annotations = null;
				try {
					annotations = method.getAnnotations();
				} catch (ClassNotFoundException e) {
					System.err.format("Failed to get annotations of method %1$s: %2$s\n", 
							method.getLongName(), e.toString());
					continue;
				}
			
				for (final Object ann : annotations) {
					if (ann instanceof Traced) {
						try {
							addMethodTracing(cc, method);
							wasInstrumented = true;
						} catch (Exception e) {
							System.err.format("Failed to add tracing to method %1$s: %2$s\n", 
									method.getLongName(), e.toString());
						}
					}
				}
			}
		
			if(wasInstrumented) {
				try {
					return cc.toBytecode();
				} catch (Exception e) {
					System.err.format("Failed to compile instrumented class %1$s: %2$s\n",
							canonicalClassName, e.toString());
				}
			}
		} catch (NotFoundException e) {
			System.err.format("Failed to register class %1$s in a javaassist ClassPool: %2$s\n",
					   canonicalClassName, e.toString());
		}
	
		return classfileBuffer;
	}
	private static String getArgumentPrintingCode(CtMethod theMethod, String theResultHolderName) {
		StringBuilder rv = new StringBuilder();
		// Generated code tries to avoid unnecessary objects' creation. 
		// Also we don't use generics due to javaassist limitations
		rv.append("java.lang.StringBuilder resultHolderName = null;");
		rv.append("java.util.HashMap argumentNames = null;");
		rv.append("if($args.length > 0) {");
		rv.append(" resultHolderName = new java.lang.StringBuilder();");
		rv.append(" argumentNames = new java.util.HashMap();");

		MethodInfo methodInfo = theMethod.getMethodInfo();
		LocalVariableAttribute table = (LocalVariableAttribute)methodInfo.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);

		if(table != null) {
			// this is to skip a 'this' argument in case of a non-static method
			int offset = Modifier.isStatic(theMethod.getModifiers()) ? 0 : 1;
		
			for(int i = 0; i < table.tableLength(); ++i) {
				int argumentIndex = table.index(i) - offset;
		
				if(argumentIndex < 0) continue;
				
				String argumentName = table.variableName(i);
				rv.append(" argumentNames.put(new Integer(");
				rv.append(argumentIndex);
				rv.append("), \"");
				rv.append(argumentName);
				rv.append("\");");
			}
		}

		rv.append("}");
		rv.append("for(int i = 0; i < $args.length; ++i) {");
		rv.append(" Object rawN = argumentNames.get(new Integer(i));");
		rv.append(" String n = rawN == null ? \"<unk>\" : (String)rawN;");
		rv.append(" Object o = $args[i];");
		rv.append(" String v = o == null ? \"null\" : o.toString();");
		rv.append(" if(v.length() > 128) v = v.substring(0, 128) + \"...\";");
		rv.append(" if(i > 0) resultHolderName.append(\", \");");
		rv.append(" resultHolderName.append(n);");
		rv.append(" resultHolderName.append(\" = \");");
		rv.append(" resultHolderName.append(v);");
		rv.append("}");
		return rv.toString().replaceAll("resultHolderName", theResultHolderName);
	}
	private static void addMethodTracing(CtClass theClass, CtMethod theMethod) throws NotFoundException, CannotCompileException {
		final String VarType = "com.develorium.metracer.TracingState";
		final String VarName = "__com_develorium_tracing_state";
		String methodName = String.format("%s.%s", theClass.getName(), theMethod.getName());

		StringBuilder prolog = new StringBuilder();
		prolog.append("{");
		prolog.append(String.format(" %1$s %2$s = (%1$s)%3$s.get();", VarType, VarName, TracingStateFieldName));
		prolog.append(getArgumentPrintingCode(theMethod, "argumentValuesRaw"));
		prolog.append("String argumentValues = argumentValuesRaw == null ? \"\" : argumentValuesRaw.toString();");
		prolog.append(String.format("System.out.println(\"+++[\" + %1$s.submerge() + \"] %2$s(\" + argumentValues + \")\");", 
					    VarName, methodName));
		prolog.append("}");

		StringBuilder epilog = new StringBuilder();
		epilog.append("{");
		epilog.append(String.format("%1$s %2$s = (%1$s)%3$s.get();", VarType, VarName, TracingStateFieldName));
		epilog.append(String.format("System.out.println(\"---[\" + %1$s.getCallDepth() + \"] %2$s\");", VarName, methodName));
		epilog.append(String.format("%1$s.emerge();", VarName));
		epilog.append("}");

		StringBuilder epilogFinally = new StringBuilder();
		epilogFinally.append("{");
		epilogFinally.append(String.format("%1$s %2$s = (%1$s)%3$s.get();", VarType, VarName, TracingStateFieldName));
		epilogFinally.append(String.format("if(%1$s.isException()) { System.out.println(\"---[\" + %1$s.getCallDepth() + \"] %2$s (by exception)\"); }", 
						   VarName, methodName));
		epilogFinally.append(String.format("%1$s.commitEmerge();", VarName));
		epilogFinally.append("}");

		theMethod.insertBefore(prolog.toString());
		theMethod.insertAfter(epilog.toString());
		theMethod.insertAfter(epilogFinally.toString(), true);
	}
	private static final String TracingStateFieldName = "com.develorium.metracer.TracingStateThreadLocal.instance";
}
