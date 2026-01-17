package briServer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {
    private final Map<String, ServiceDescriptor> services = new ConcurrentHashMap<>();

    public void put(ServiceDescriptor desc) {
        services.put(desc.serviceName, desc);
    }

    public ServiceDescriptor get(String serviceName) {
        return services.get(serviceName);
    }

    public boolean contains(String serviceName) {
        return services.containsKey(serviceName);
    }

    public Collection<ServiceDescriptor> all() {
        return services.values();
    }
}
