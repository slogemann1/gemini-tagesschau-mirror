import java.lang.reflect.Method;
import java.lang.invoke.MethodHandles;
import java.util.jar.JarInputStream;
import java.util.jar.JarEntry;
import java.io.InputStream;
import java.security.ProtectionDomain;
import java.security.Permissions;
import java.security.AllPermission;
import java.io.File;

class Bootstrap {
    private static final String mainClassName = "Program";

    public static void main(String[] args) {
        try {
            ClassLoader bootstrapClassLoader = Bootstrap.class.getClassLoader(); // Der Classloader am Anfang
            ClassLoader classLoader = new JarClassLoader(bootstrapClassLoader); // Erstelle neuer Classloader
            Class mainClass = classLoader.loadClass(mainClassName);
            
            Method main = mainClass.getMethod("main", String[].class); // Finde und führe die main() Methode aus
            main.setAccessible(true); // Mache main() ausführbar
            main.invoke(null, new Object[] { args });
        }
        catch(Exception e) {
            System.out.println(e.getCause());
        }
    }
}

class JarClassLoader extends ClassLoader {
    private static final String JARFILE_NAME = "jsonJava.jar";

    public JarClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class loadClass(String classname) throws ClassNotFoundException {
        try {
            if(classname.startsWith("java")) { // Eigene ClassLoader darf java.* Klassen nicht laden
                return super.loadClass(classname);
            }
            
            String filename = classname.replace(".", "/") + ".class"; // Öffne Datei
            InputStream filestream = this.getClass().getClassLoader().getResourceAsStream(filename);

            byte[] bytes = null; // Wenn es gefunden wird, lesen, sonst in jar-Datei suchen
            if(filestream != null) {
                bytes = filestream.readAllBytes();
            }
            else {
                InputStream jarFilestream = this.getClass().getClassLoader().getResourceAsStream(JARFILE_NAME); // Öffne jar-Datei
                JarInputStream jarstream = new JarInputStream(jarFilestream);
                JarEntry nextEntry = jarstream.getNextJarEntry();
                while(nextEntry != null) { // Lese alle Dateien, bis es gefunden wird
                    if(nextEntry.getName().equals(filename)) {
                        bytes = jarstream.readAllBytes();
                        break;
                    }

                    nextEntry = jarstream.getNextJarEntry();
                }
            }
            
            Class cls = this.defineClass(classname, bytes, 0, bytes.length); // Definiere die Klasse, damit es verwendet werden kann
            this.resolveClass(cls);

            return cls;
            
        }
        catch (Exception e) { // Wenn die Klasse nicht geladen werden kann, soll die Superklasse versuchen es zu laden
            return super.loadClass(classname);
        }
    }
}