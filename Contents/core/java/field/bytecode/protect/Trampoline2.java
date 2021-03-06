package field.bytecode.protect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import field.core.Platform;
import field.launch.Launcher;
import field.launch.SystemProperties;
import field.launch.iLaunchable;
import field.namespace.generic.ReflectionTools;
import field.util.ANSIColorUtils;
import field.util.MiscNative;

public class Trampoline2 implements iLaunchable {

	public interface ClassLoadedNotification {
		public void notify(Class loaded);
	}

	static public final List<ClassLoadedNotification> notifications = new ArrayList<ClassLoadedNotification>();

	public interface ClassModification {
		public byte[] modify(byte[] c);
	}

	static public final ModificationCache cache = new ModificationCache();

	static public ReloadingSupport reloadingSupport = new ReloadingSupport();

	public class MyClassLoader extends FastClassLoader {

		private java.lang.reflect.Method findLoadedClass_method1;

		HashMap<String, Class> previous = new HashMap<String, Class>();
		HashSet<String> alreadyFailed = new HashSet<String>();

		HashMap<String, Class> already = new HashMap<String, Class>();

		public MyClassLoader(URL[] u, ClassLoader loader) {
			super(u, loader);
		}

		public Class<?> _defineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
			Class<?> name2 = super.defineClass(name, b, off, len);
			name2.getDeclaredMethods();
			already.put(name, name2);
			return name2;

			// callDefineClass(getParent(), name, b, off, l)
		}

		@Override
		public void addURL(URL url) {

			super.addURL(url);

			String oldCP = System.getProperty("java.class.path");
			oldCP += ":" + url.getFile();
			System.setProperty("java.class.path", oldCP);
		}

		public Set<Class> getAllLoadedClasses() {
			try {
				HashSet<Class> al = new HashSet<Class>();
				al.addAll(previous.values());
				al.addAll(already.values());

				Vector vThere = (Vector) ReflectionTools.illegalGetObject(deferTo, "classes");
				al.addAll(vThere);
				return al;
			} catch (ConcurrentModificationException e) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				return getAllLoadedClasses();
			}
		}

		@Override
		protected Class<?> findClass(String arg0) throws ClassNotFoundException {
			// ;//System.out.println("ZZZZZ find class <" + arg0 +
			// ">");
			return super.findClass(arg0);
		}

		private Class callDefineClass(ClassLoader parent, String class_name, byte[] bytes, int i, int length) {

			try {
				java.lang.reflect.Method cc = ClassLoader.class.getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, Integer.TYPE, Integer.TYPE });
				cc.setAccessible(true);
				return (Class) cc.invoke(parent, new Object[] { class_name, bytes, i, length });
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
			return null;

		}

		protected Class checkHasBeenLoaded(String s) {
			try {
				Class c = already.get(s);
				if (c != null)
					return c;

				if (findLoadedClass_method1 == null) {
					findLoadedClass_method1 = java.lang.ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
					findLoadedClass_method1.setAccessible(true);
				}
				java.lang.ClassLoader dt = getParent();
				while (dt != null) {
					Object r = findLoadedClass_method1.invoke(dt, new Object[] { s });
					if (r != null) {
						if (debug)
							;// System.out.println(" class <"
								// + s +
								// "> already loaded in class loader <"
								// + dt + ">");
						return (Class) r;
					}
					dt = dt.getParent();
				}
				return null;
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected String findLibrary(String rawName) {

			// System.out.println("####\n\n looking for <" + rawName
			// + "> \n\n #############");
			if (Platform.isMac()) {
				String name = "lib" + rawName + ".dylib";

				// System.out.println(extendedLibraryPaths);
				for (String s : extendedLibraryPaths) {
					File file = new File(s, name);
					if (file.exists()) {
						;// System.out.println(" found it <"
							// + file + ">");
						return file.getAbsolutePath();
					}
				}
				for (String s : extendedClassPaths) {
					File file = new File(s, name);
					if (file.exists()) {
						;// System.out.println(" found it <"
							// + file + ">");
						return file.getAbsolutePath();
					}
				}
			}
			if (Platform.isLinux()) {
				String name = "lib" + rawName + ".so";

				for (String s : extendedLibraryPaths) {
					File file = new File(s, name);
					if (file.exists()) {
						// System.out.println(" found it <"
						// + file + ">");
						return file.getAbsolutePath();
					}
				}
				for (String s : extendedClassPaths) {
					File file = new File(s, name);
					if (file.exists()) {
						// System.out.println(" found it <"
						// + file + ">");
						return file.getAbsolutePath();
					}
				}
			}
			return super.findLibrary(rawName);
		}

		LinkedHashSet<String> knownPackages = new LinkedHashSet<String>();

		@Override
		synchronized protected Class<?> loadClass(String class_name, boolean resolve) throws ClassNotFoundException {

			// System.out.println(" load :"+class_name);

			if (alreadyFailed.contains(class_name))
				throw new ClassNotFoundException(class_name);

			deferTo = getParent();
			try {
				ClassNotFoundException classNotFound = null;

				loading.push(class_name);
				try {
					if (debug) {
						;// System.out.println(indentation
							// + "? entered " +
							// class_name + " " +
							// resolve);
						indentation += " ";
					}

					Class loaded = previous.get(class_name);
					if (loaded == null)
						if (!shouldLoadLocal(class_name)) {
							try {
								loaded = getParent().loadClass(class_name);
							} catch (ClassNotFoundException ex) {
								classNotFound = ex;
								if (debug)
									;// System.out.println(ANSIColorUtils.red("-- class not found <"
										// +
										// class_name
										// +
										// ">"));
							}
						}
					if (loaded == null) {
						loaded = checkHasBeenLoaded(class_name);
					}

					if (classNotFound == null)
						if (loaded == null) {
							deferTo = getParent();
							byte[] bytes = instrumentClass(this, class_name);

							if (bytes != null) {

								if (class_name.lastIndexOf(".") != -1) {
									String packageName = class_name.substring(0, class_name.lastIndexOf("."));
									if (!knownPackages.contains(packageName)) {
										try {
											definePackage(packageName, null, null, null, null, null, null, null);
										} catch (IllegalArgumentException e) {
											// e.printStackTrace();
										}
										knownPackages.add(packageName);
									}
								}

								loaded = reloadingSupport.delegate(class_name, bytes);

								if (loaded == null) {

									try {
										loaded = defineClass(class_name, bytes, 0, bytes.length);
									} catch (LinkageError le) {
										le.printStackTrace();
										return null;
									}
									// ;//System.out.println("
									// >>
									// about
									// to
									// resolve
									// <"+class_name+">
									// <"+resolve+">");
									if (resolve)
										resolveClass(loaded);
									previous.put(class_name, loaded);
								} else {
									;// System.out.println(" loaded <"
										// +
										// class_name
										// +
										// "> in RS classloader");
								}
							}
							// ;//System.out.println("
							// >>
							// loaded
							// <"+class_name+">");
						}
					if (classNotFound == null)
						if (loaded == null) {
							try {
								loaded = Class.forName(class_name);

								// recent change
								previous.put(class_name, loaded);

							} catch (ClassNotFoundException ex) {
								classNotFound = ex;
								if (debug) {
									;// System.out.println(ANSIColorUtils.red("-- class not found <"
										// +
										// class_name
										// +
										// ">"));
									ex.printStackTrace();
								}
							}
						}
					if (debug) {
						indentation = indentation.substring(1);
						;// System.out.println(indentation
							// + "?" + class_name +
							// " complete");
							// assert
							// popped.equals(class_name);
					}
					if (classNotFound != null) {
						if (debug) {
							System.err.println("exception (" + classNotFound.getClass() + "): while trying to load <" + class_name + " / <" + loading + ">");
							new Exception().printStackTrace();
						}
						alreadyFailed.add(class_name);

						throw classNotFound;
					}

					already.put(class_name, loaded);

					if (loaded.isAnnotationPresent(Notable.class)) {

						;// System.out.println(" CLASS IS NOTABLE :"+loaded+" "+notifications);

						for (ClassLoadedNotification n : notifications) {
							n.notify(loaded);
						}
					}

					return loaded;
				} finally {
					String popped = loading.pop();
				}
			} catch (ClassNotFoundException e) {
				throw e;
			} catch (Throwable t) {
				t.printStackTrace();
				;// System.out.println(" unexpected trouble loading <"
					// + loading + ">");
				return null;
			}

		}
	}

	static public Trampoline2 trampoline = null;
	static public HashSet<String> plugins = new LinkedHashSet<String>();

	static public final boolean debug = false;

	static public List<String> extendedClassPaths = new ArrayList<String>();

	static String classToLaunch;

	static {
		// if (Platform.getOS() == Platform.OS.mac) {
		// ;//System.out.println("OPEN: ");
		// Application.getApplication().setOpenFileHandler(new
		// OpenFilesHandler() {
		//
		// @Override
		// public void openFiles(OpenFilesEvent arg0) {
		// ;//System.out.println("OPEN: open file event");
		// }
		// });
		// Application.getApplication().setAboutHandler(null);
		// Application.getApplication().setPreferencesHandler(null);
		// Application.getApplication().addAppEventListener(new
		// AppReOpenedListener() {
		//
		// @Override
		// public void appReOpened(AppReOpenedEvent arg0) {
		// ;//System.out.println("OPEN: reopen");
		// }
		// });
		//
		// }

		;// System.out.println("JLP: "+System.getProperty("java.library.path")+" "+new
			// File(".").getAbsolutePath());

		new MiscNative().splashUp_safe();

		// TODO: 64 \u2014 need new property mechanism
		// String c =
		// NSUserDefaults.standardUserDefaults().stringForKey("main.class");
		// if (c == null)
		// {
		String c = SystemProperties.getProperty("main.class");
		;// System.out.println(" class to launch :" + c +
			// " memory dimensions " +
			// Runtime.getRuntime().maxMemory());
			// }
		classToLaunch = c;
	}

	static public void handle(Throwable e) {
		e.printStackTrace();
		if (SystemProperties.getIntProperty("exitOnException", 0) == 1 || e instanceof Error || e.getCause() instanceof Error)
			System.exit(1);
	}

	private String[] ignored;

	protected MyClassLoader loader = null;

	protected ClassLoader deferTo;

	String indentation = "";

	Stack<String> loading = new Stack<String>();
	private String[] allowed;

	public ClassLoader getClassLoader() {
		return loader;
	}

	public void addJar(String n) {
		try {

			if (new File(n).exists()) {
				if (new File(n).isDirectory() && !n.endsWith("/"))
					loader.addURL(new URL("file://" + n + "/"));
				else
					loader.addURL(new URL("file://" + n));
				extendedClassPaths.add(n);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public void addExtensionsDirectory(File path) {
		if (path.getName().endsWith("**"))
			path = new File(path.getAbsolutePath().substring(0, path.getAbsolutePath().length() - 2));

		if (path.exists()) {

			// ;//System.out.println(" adding extenions dir <" +
			// path +
			// ">");

			try {

				loader.addURL(new URL("file://" + path.getCanonicalPath() + "/"));

			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			extendedClassPaths.add(path.getAbsolutePath());

			String[] jars = path.list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return (name.endsWith(".jar"));
				}
			});

			if (jars != null)
				for (String j : jars) {
					try {
						loader.addURL(new URL("file://" + path.getCanonicalPath() + "/" + j));

						extendedClassPaths.add(path.getCanonicalPath() + "/" + j);

						JarFile m = new JarFile(new File(path.getCanonicalFile() + "/" + j));
						Manifest manifest = m.getManifest();
						if (manifest != null) {
							String a = (String) manifest.getMainAttributes().get(new Attributes.Name("Field-PluginClass"));
							;// System.out.println(" jar <"
								// + path +
								// "> declares plugin <"
								// + a + ">");
							if (a != null) {
								plugins.add(a);
							}

							injectManifestProperties(manifest);
						}
					} catch (MalformedURLException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			// System.out.println(" checking <" + path +
			// "> for natives ");
			File[] natives = path.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.getPath().endsWith(".dylib") || file.getPath().endsWith(".jnilib");
				}
			});
			// System.out.println(" found <" + natives.length +
			// ">");
			for (File n : natives) {
				try {
					// System.load(n.getAbsolutePath());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}

			File[] dirs = path.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory() && !file.getName().endsWith("_");
				}
			});
			for (File j : dirs) {
				// ;//System.out.println(" adding next dir <" +
				// j +
				// ">");
				addExtensionsDirectory(j);
				// try {
				// loader.addURL(new URL("file://" +
				// j.getAbsolutePath()));
				// extendedClassPaths.add(j.getAbsolutePath());
				// } catch (MalformedURLException e) {
				// e.printStackTrace();
				// }
			}

			File[] rawManifests = path.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return (file.getAbsolutePath().endsWith(".mf") && !prohibitExtension(file.getName().substring(0, file.getName().length() - 3))) || ((file.getAbsolutePath().endsWith(".mf_")) && alsoAcceptExtension(file.getName().substring(0, file.getName().length() - 4)));
				}
			});
			for (File j : rawManifests) {
				// ;//System.out.println(" adding raw manifest <"
				// +
				// j + ">");
				try {
					Manifest m = new Manifest(new BufferedInputStream(new FileInputStream(j)));
					String aa = (String) m.getMainAttributes().get(new Attributes.Name("Field-RedirectionPath"));
					;// System.out.println(aa + " " + j);

					if (aa != null && aa.endsWith("**")) {

						addWildcardPath(aa);
					} else if (aa != null) {
						for (String a : aa.split(":")) {
							a = a.trim();
							String fp = (new File(a).isAbsolute() ? new File(a).getAbsolutePath() : new File(j.getParent(), a).getAbsolutePath());
							if (!extendedClassPaths.contains(fp)) {

								if (!new File(fp).exists()) {
									System.err.println(" warning, path <" + new File(fp).getAbsolutePath() + ">added to classpath through Field-RedirectionPath inside extension " + j + " doesn't exist");
								} else {

									URL url = new URL("file://" + fp + (fp.endsWith(".jar") ? "" : "/"));

									;// System.out.println(" adding url to main classloader <"
										// +
										// url
										// +
										// "> <"
										// +
										// new
										// File(url.getPath()).exists()
										// +
										// ">");

									loader.addURL(url);

									extendedClassPaths.add(fp);
								}
							}
						}
					} else {
					}
					String b = (String) m.getMainAttributes().get(new Attributes.Name("Field-PluginClass"));
					if (b != null) {
						plugins.add(b);
					}

					injectManifestProperties(m);

				} catch (FileNotFoundException e) {
					// TODO
					// Auto-generated
					// catch
					// block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO
					// Auto-generated
					// catch
					// block
					e.printStackTrace();
				}
			}
		}
	}

	public void addWildcardPath(String aa) throws MalformedURLException {

		File dir = new File(aa.replace("**", ""));
		if (dir.exists()) {

			loader.addURL(new URL("file://" + dir.getAbsolutePath() + "/"));

			extendedClassPaths.add(dir.getAbsolutePath());

			System.out.println(" extending library path by :" + dir.getAbsolutePath());
			extendLibraryPath(dir.getAbsolutePath());

			String[] ll = dir.list(new FilenameFilter() {

				public boolean accept(File dir, String name) {
					return name.endsWith(".jar");
				}
			});
			if (ll != null)
				for (String l : ll) {

					String fp = new File(dir.getAbsolutePath() + "/" + l).getAbsolutePath();

					URL url = new URL("file://" + fp + (fp.endsWith(".jar") ? "" : "/"));

					loader.addURL(url);

					extendedClassPaths.add(fp);
				}
			File[] f = dir.listFiles(new FileFilter() {

				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			if (f != null) {
				for (File ff : f) {
					addExtensionsDirectory(ff);
				}
			}

			// System.out.println(" checking <" + dir +
			// "> for natives ");
			File[] natives = dir.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.getPath().endsWith(".dylib") || file.getPath().endsWith(".jnilib");
				}
			});
			// System.out.println(" found <" + natives.length +
			// ">");
			for (File n : natives) {
				try {
					// System.load(n.getAbsolutePath());
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		} else {
			System.err.println(" warning: wildcard path <" + aa + "> is not a directory or does not exist ");
		}
	}

	public void addWildcardPathRecursively(String aa) throws MalformedURLException {

		if (aa.contains("examples"))
			return;

		File dir = new File(aa.replace("**", ""));
		if (dir.exists()) {

			extendLibraryPath(dir.getAbsolutePath());

			String[] ll = dir.list(new FilenameFilter() {

				public boolean accept(File dir, String name) {
					return name.endsWith(".jar");
				}
			});
			if (ll != null)
				for (String l : ll) {

					;// System.out.println(" l = " + l);

					String fp = new File(dir.getAbsolutePath() + "/" + l).getAbsolutePath();

					URL url = new URL("file://" + fp + (fp.endsWith(".jar") ? "" : "/"));

					loader.addURL(url);

					extendedClassPaths.add(fp);
				}
			File[] f = dir.listFiles(new FileFilter() {

				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			if (f != null) {
				for (File ff : f) {
					addWildcardPathRecursively(ff.getAbsolutePath());
				}
			}
		} else {
			System.err.println(" warning: wildcard path <" + aa + "> is not a directory or does not exist ");
		}
	}

	protected boolean prohibitExtension(String substring) {
		String p = SystemProperties.getProperty("withoutExtensions", null);
		if (p == null)
			return false;

		String[] parts = p.split(":");
		for (String pp : parts) {
			if (pp.toLowerCase().equals(substring.toLowerCase()))
				return true;
		}
		return false;
	}

	protected boolean alsoAcceptExtension(String substring) {
		String p = SystemProperties.getProperty("withExtensions", null);
		if (p == null)
			return false;

		String[] parts = p.split(":");
		for (String pp : parts) {
			if (pp.toLowerCase().equals(substring.toLowerCase()))
				return true;
		}
		return false;
	}

	static public List<String> extendedLibraryPaths = new ArrayList<String>();

	private void extendLibraryPath(String s) {
		extendedLibraryPaths.add(s);
		System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + s);
		System.setProperty("jna.library.path", System.getProperty("jna.library.path") + File.pathSeparator + s);

		// System.out.println(" library paths now "+System.getProperty("java.library.path")+" and "+System.getProperty("jna.library.path"));
		// This enables the java.library.path to be modified at runtime
		// From a Sun engineer at
		// http://forums.sun.com/thread.jspa?threadID=707176

		try {
			Field field = ClassLoader.class.getDeclaredField("usr_paths");
			field.setAccessible(true);
			String[] paths = (String[]) field.get(null);
			for (int i = 0; i < paths.length; i++) {
				if (s.equals(paths[i])) {
					return;
				}
			}
			String[] tmp = new String[paths.length + 1];
			System.arraycopy(paths, 0, tmp, 0, paths.length);
			tmp[paths.length] = s;
			field.set(null, tmp);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Failed to get permissions to set library path");
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("Failed to get field handle to set library path");
		}

		File[] f = new File(s).listFiles();
		if (f != null) {
			for (File ff : f) {
				if (ff.getName().endsWith(".dylib") | ff.getName().endsWith(".so")) {
					// System.out.println(" premptivly loading <"+ff+">");
					// try{
					// System.load(ff.getAbsolutePath());
					// }
					// catch(Throwable
					// t){System.out.println(" didn't load, continuing");}
				}
			}
		}

	}

	public byte[] bytesForClass(java.lang.ClassLoader deferTo, String class_name) {

		System.out.println(" bytes for class :" + class_name);

		InputStream s = deferTo.getResourceAsStream(resourceNameForClassName(class_name));
		if (s == null)
			return null;

		// try to load it
		// here we might cache modification dates

		BufferedInputStream stream = new BufferedInputStream(s, 80000);
		if (stream == null)
			return null;
		try {
			byte[] a = new byte[stream.available()];
			stream.read(a);
			return a;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public byte[] instrumentClass(java.lang.ClassLoader deferTo, String class_name) {
		if (debug)
			;// System.out.println(" getResource <" + class_name +
				// "> <" +
				// deferTo.getResource(resourceNameForClassName(class_name))
				// + ">");

		InputStream s = deferTo.getResourceAsStream(resourceNameForClassName(class_name));
		if (s == null)
			return null;

		// try to load it
		// here we might cache modification dates

		if (debug)
			;// System.out.println(indentation + "#" +
				// (class_name.replace('.', File.separatorChar))
				// + ">");
		BufferedInputStream stream = new BufferedInputStream(s, 80000);
		if (stream == null)
			return null;
		try {
			byte[] a = new byte[stream.available()];
			stream.read(a);
			if (debug)
				;// System.out.println(" about to instrument <"
					// + class_name + "> inside <" + this +
					// "> !! ");
			a = instrumentBytecodes(a, class_name, deferTo);
			return a;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	protected String resourceNameForClassName(String class_name) {
		return class_name.replace('.', File.separatorChar) + ".class";
	}

	public void launch() {
		System.out.println("## trampoline <" + this.getClass() + ":" + this.getClass().getClassLoader() + ">");
		trampoline = this;

		String exceptions = SystemProperties.getProperty("trampolineExceptions", null);
		ignored = new String[] { "apple.", "java.", "javax.", "sun.", "com.apple", "apple.", "field.namespace", "field.math", "field.launch.", "org.objectweb", "com.sun", "org.xml", "org.w3c", "$Prox", "org.eclipse", "main", "field.util.BetterWeak", "field.misc.ANSIColorUtils", "ch.rand", "org.python", "org.apache.batik", "org.antlr", "field.util.TaskQueue", "com.lowagie", "net.sf.cglib.proxy", "com.seaglasslookandfeel", "org.pushingpixels", "net.sourceforge.napkinlaf.", "com.kenai.jaffl" };
		allowed = new String[] { "phobos", "com.sun.script.", "com.sun.scenario", "com.sun.stylesheet", "com.sun.opengl", "com.sun.gluegen", "javax.media.opengl", "javax.media.nativewindow", "javax.jmdns" };

		if (exceptions != null) {

			ArrayList a = new ArrayList(Arrays.asList(ignored));
			a.addAll(Arrays.asList(exceptions.split(":")));
			ignored = (String[]) a.toArray(ignored);
		}

		loader = new MyClassLoader(((URLClassLoader) this.getClass().getClassLoader()).getURLs(), (this.getClass().getClassLoader()));
		System.setSecurityManager(new PermissiveSecurityManager());

		String extendedJars = SystemProperties.getProperty("extendedJars", null);
		if (extendedJars != null) {
			String[] ex = extendedJars.split(":");
			for (String e : ex) {
				addJar(e);
			}
		}

		System.out.println(" extended jars :" + extendedJars);

		Vector v = (Vector) ReflectionTools.illegalGetObject(this.getClass().getClassLoader(), "classes");
		if (debug)
			;// System.out.println(" already loaded all of <" + v +
				// ">");

		if (!System.getProperty("asserts", "none").equals("none"))
			loader.setDefaultAssertionStatus(true);

		Thread.currentThread().setContextClassLoader(loader);

		String extensionsDir = SystemProperties.getProperty("extensions.dir", "../../extensions/");
		Trampoline2.trampoline.addExtensionsDirectory(new File(extensionsDir));
		String extensionsDir2 = System.getProperty("user.home") + "/.field/extensions";

		if (!new File(extensionsDir2).exists())
			new File(extensionsDir2).mkdirs();

		if (new File(extensionsDir2).exists())
			Trampoline2.trampoline.addExtensionsDirectory(new File(extensionsDir2));

		try {
			;// System.out.println(Arrays.asList(loader.getURLs()));
			final Class c = (loader.loadClass(classToLaunch));
			;// System.out.println(" c = " + c + " " +
				// c.getClassLoader() + " " + loader);
			try {
				Method main = c.getDeclaredMethod("main", new Class[] { new String[0].getClass() });
				try {
					main.invoke(null, new Object[] { null });
					return;
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				}
				return;
			} catch (SecurityException e) {
				e.printStackTrace();
				return;
			} catch (NoSuchMethodException e) {
			}

			Launcher.getLauncher().mainThread = Thread.currentThread();
			printInfo();
			Launcher.mainInstance = (iLaunchable) c.newInstance();
			printInfo();
			Launcher.mainInstance.launch();
			printInfo();
			printInfo();

		} catch (Throwable e) {
			e.printStackTrace();
			if (SystemProperties.getIntProperty("exitOnException", 0) == 1 || e instanceof Error || e.getCause() instanceof Error)
				System.exit(1);
		}

		if (SystemProperties.getIntProperty("nosave", 0) == 1)
			System.setSecurityManager(new NoWriteSecurityManager());
		else if (SystemProperties.getIntProperty("collectResources", 0) == 1)
			System.setSecurityManager(new CollectResourcesSecurityManager());
		else
			System.setSecurityManager(new NoopSecurityManager());

	}

	public boolean shouldLoadLocal(String s) {

		s = s.replace('/', '.');
		boolean failed = false;
		for (String root : ignored) {
			if (s.startsWith(root))
				failed = true;
		}

		if (s.contains(".protect"))
			failed = true;

		if (failed)
			for (String root : allowed)
				if (s.contains(root))
					failed = false;

		return !failed;
	}

	private Set<Object> injectManifestProperties(Manifest manifest) {

		// System.out.println(" inject manifest properties ");

		Set<Object> ks = manifest.getMainAttributes().keySet();
		for (Object o : ks) {
			if (o instanceof Attributes.Name) {
				Attributes.Name an = (Attributes.Name) o;

				if (an.toString().startsWith("Field-Property-")) {
					String prop = an.toString().substring("Field-Property-".length());

					if (prop.startsWith("Append-")) {
						prop = prop.substring("Append-".length());
						prop = prop.replace("-", ".");

						String pp = SystemProperties.getProperty(prop, null);
						pp = (pp == null ? pathify(manifest.getMainAttributes().getValue(an)) : (pp + ":" + pathify(manifest.getMainAttributes().getValue(an))));
						SystemProperties.setProperty(prop, pp);

						// System.out.println(" property <"
						// + prop + "> now <" + pp +
						// ">");

					} else {
						SystemProperties.setProperty(prop, manifest.getMainAttributes().getValue(an));
					}
				}
			}
		}
		return ks;
	}

	private String pathify(String value) {
		try {
			if (new File(value).exists())
				return new File(value).getCanonicalPath();
			else
				return value;
		} catch (Throwable t) {
			return value;
		}
	}

	private void printInfo() {
		if (debug) {
			;// System.out.println("/n/n");

			Vector vThere = (Vector) ReflectionTools.illegalGetObject(deferTo, "classes");
			;// System.out.println("local: " + loader.already);

			for (int i = 0; i < vThere.size(); i++)
				;// System.out.println("global:" + vThere.get(i)
					// + " " +
					// loader.already.containsValue(vThere.get(i)));
		}
	}

	protected boolean check() {
		// if (!debug)
		if (true)
			return true;
		Vector vThere = (Vector) ReflectionTools.illegalGetObject(deferTo, "classes");
		for (int i = 0; i < vThere.size(); i++) {
			String s = ((Class) vThere.get(i)).getName();
			boolean failed = !shouldLoadLocal(s);
			if (!failed) {
				;// System.out.println("illegally loaded class <"
					// + s + "> <" + vThere + ">");
				System.exit(1);
			}
		}
		return true;
	}

	protected byte[] instrumentBytecodes(byte[] a, String class_name, java.lang.ClassLoader deferTo) {
		return a;
	}

	public Class<?> loadClass(String classname, byte[] b) {

		Class<?> c = loader._defineClass(classname, b, 0, b.length);
		return c;
	}

}
