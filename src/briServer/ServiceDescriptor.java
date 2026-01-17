package briServer;

import java.lang.reflect.Constructor;
import java.net.URLClassLoader;

public class ServiceDescriptor {
    public final String serviceName;
    public final String className;
    public final Constructor<?> socketCtor;
    public final URLClassLoader loader;

    public ServiceDescriptor(String serviceName, String className, Constructor<?> socketCtor, URLClassLoader loader) {
        this.serviceName = serviceName;
        this.className = className;
        this.socketCtor = socketCtor;
        this.loader = loader;
    }

    public void closeLoader() {
        try {
            loader.close();
        } catch (Exception ignored) {
        }
    }
}
