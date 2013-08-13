package edu.rpi.tw.escience.semanteco.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.log4j.Logger;

import edu.rpi.tw.escience.semanteco.Module;

/**
 * ModuleClassLoader is used to load classes from a module's JAR file.
 * It is also responsible for verifying the integrity of the JAR and
 * locating any classes that implement Module so that they can be
 * retrieved by the ModuleManager for later use.
 * 
 * @author ewpatton
 *
 */
public class ModuleClassLoader extends ClassLoader {

	private Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	private Set<Class<? extends Module>> modules = new HashSet<Class<? extends Module>>();
	private static final int BUFSIZE = 8192;
	private Logger log = Logger.getLogger(ModuleClassLoader.class);
	private Map<String, byte[]> clazzBytes = new HashMap<String, byte[]>();
	
	/**
	 * Constructs a ModuleClassLoader from the JAR at the specified
	 * path.
	 * @param path
	 */
	@SuppressWarnings("unchecked")
	public ModuleClassLoader(String path) {
		super(Thread.currentThread().getContextClassLoader());
		log.trace("ModuleClassLoader");
		/* a static reference to the module class*/
		final Class<?> module = Module.class;
		try {
			final JarFile file = new JarFile(path);
			final Enumeration<JarEntry> entries = file.entries();
			while(entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				log.debug("Found entry in jar: "+entry.getName());
				if(entry.getName().endsWith(".class")) {
					loadClassBytes(file, entry);
				}
			}
			/* creates a list of all classes that are also modules in the particular jar, we instantiate one module class loader per jar */
			for(String i : clazzBytes.keySet()) {
				Class<?> cls = registerClass(i, clazzBytes.get(i));
				if(cls == null) {
					continue;
				}
				/* checks is the class cls is a extends or implements module */
				if(module.isAssignableFrom(cls)) {
					modules.add((Class<? extends Module>) cls);
				}
			}
			if(modules.size() == 0) {
				throw new IllegalArgumentException("Not a valid module: "+path);
			}
		}
		catch(IOException e) {
			throw new IllegalArgumentException("Not a valid module: "+path, e);
		}
	}
	
	protected Class<?> findClass(String name) {
		if(!classes.containsKey(name) && clazzBytes.containsKey(name)) {
			registerClass(name, clazzBytes.get(name));
		}
		return classes.get(name);
	}
	
	/**
	 * This method writes a jar file into an array of byte.
	 * @param file
	 * @param entry
	 * @throws IOException
	 */
	protected final void loadClassBytes(JarFile file, JarEntry entry) throws IOException {
		final String clsName = entry.getName().replaceAll("/", ".").substring(0, entry.getName().length()-".class".length());
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final InputStream is = file.getInputStream(entry);
		final byte[] buffer = new byte[BUFSIZE];
		int read = 0;
		while((read = is.read(buffer))>0) {
			baos.write(buffer, 0, read);
		}
		is.close();
		final byte[] byteCode = baos.toByteArray();
		clazzBytes.put(clsName, byteCode);
	}
	
	protected final Class<?> registerClass(String name, byte[] byteCode) {
		Class<?> cls = null;
		if(classes.containsKey(name)) {
			return classes.get(name);
		}
		try {
			cls = defineClass(name, byteCode, 0, byteCode.length);
		}
		catch(ClassFormatError e) {
			log.warn("Invalid class file", e);
		}
		catch(NoClassDefFoundError e) {
			log.warn("No class definition", e);
		}
		if(cls != null) {
			classes.put(name, cls);
		}
		return cls;
	}
	
	/**
	 * Gets the set of module classes found in the JAR file
	 * loaded by this ModuleClassLoader.
	 * @return
	 */
	public Set<Class<? extends Module>> getModules() {
		return Collections.unmodifiableSet(modules);
	}
	
}
