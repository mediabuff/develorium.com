package com.develorium.methtracer;

import java.lang.instrument.Instrumentation;

public class Agent {
	public static void premain(String args, Instrumentation instrumentation) {
		instrumentation.addTransformer(new TracerAdder());
	}
}
