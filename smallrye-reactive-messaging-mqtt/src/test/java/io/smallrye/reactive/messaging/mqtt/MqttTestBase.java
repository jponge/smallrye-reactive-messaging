package io.smallrye.reactive.messaging.mqtt;

import java.io.File;
import java.time.Duration;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.weld.environment.se.Weld;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.MountableFile;

import io.smallrye.config.SmallRyeConfigProviderResolver;
import io.smallrye.reactive.messaging.providers.MediatorFactory;
import io.smallrye.reactive.messaging.providers.connectors.ExecutionHolder;
import io.smallrye.reactive.messaging.providers.connectors.WorkerPoolRegistry;
import io.smallrye.reactive.messaging.providers.extension.EmitterFactoryImpl;
import io.smallrye.reactive.messaging.providers.extension.EmitterImpl;
import io.smallrye.reactive.messaging.providers.extension.LegacyEmitterFactoryImpl;
import io.smallrye.reactive.messaging.providers.extension.MediatorManager;
import io.smallrye.reactive.messaging.providers.extension.MutinyEmitterFactoryImpl;
import io.smallrye.reactive.messaging.providers.extension.ReactiveMessagingExtension;
import io.smallrye.reactive.messaging.providers.impl.ConfiguredChannelFactory;
import io.smallrye.reactive.messaging.providers.impl.ConnectorFactories;
import io.smallrye.reactive.messaging.providers.impl.InternalChannelRegistry;
import io.smallrye.reactive.messaging.providers.wiring.Wiring;
import io.smallrye.reactive.messaging.test.common.config.MapBasedConfig;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.mqtt.MqttClient;

public class MqttTestBase {

    public static GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2.0")
            .withExposedPorts(1883)
            .withCopyFileToContainer(MountableFile.forClasspathResource("mosquitto.conf"), "/mosquitto/config/mosquitto.conf")
            .waitingFor(Wait.forListeningPort())
            .waitingFor(Wait.forLogMessage(".*mosquitto .* running.*", 1));

    Vertx vertx;
    String address;
    Integer port;
    MqttUsage usage;

    @BeforeAll
    public static void startBroker() {
        mosquitto.start();
        awaitForMosquittoToBeReady(mosquitto);
    }

    public static void awaitForMosquittoToBeReady(GenericContainer<?> mosquitto) {
        // Unfortunately, the latest mosquitto are taking a lot of time to accept connection, without any way to find out
        // need a retry loop
        Vertx v = Vertx.vertx();
        MqttClient client = MqttClient.create(v, new MqttClientOptions()
                .setAutoGeneratedClientId(true));
        Awaitility
                .await().atMost(Duration.ofSeconds(30))
                .pollDelay(Duration.ofMillis(500))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    try {
                        client
                                .connect(mosquitto.getFirstMappedPort(), mosquitto.getHost())
                                .await().indefinitely();
                    } catch (Exception e) {
                        // Connection rejected not because it's not ready, but because we didn't authenticate.
                        return e.getMessage().contains("Closed") // TLS
                                || e.getMessage().contains("NOT_AUTHORIZED"); // Auth
                    }
                    return true;
                });

        client.disconnectAndForget();
        v.closeAndAwait();
    }

    @AfterAll
    public static void stopBroker() {
        mosquitto.stop();
    }

    @BeforeEach
    public void setup() {
        System.clearProperty("mqtt-host");
        System.clearProperty("mqtt-port");
        System.clearProperty("mqtt-user");
        System.clearProperty("mqtt-pwd");
        vertx = Vertx.vertx();
        address = mosquitto.getContainerIpAddress();
        port = mosquitto.getMappedPort(1883);
        System.setProperty("mqtt-host", address);
        System.setProperty("mqtt-port", Integer.toString(port));
        usage = new MqttUsage(address, port);
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("mqtt-host");
        System.clearProperty("mqtt-port");
        System.clearProperty("mqtt-user");
        System.clearProperty("mqtt-pwd");

        vertx.closeAndAwait();
        usage.close();

        SmallRyeConfigProviderResolver.instance()
                .releaseConfig(ConfigProvider.getConfig(this.getClass().getClassLoader()));
    }

    static Weld baseWeld(MapBasedConfig config) {
        addConfig(config);
        Weld weld = new Weld();
        weld.disableDiscovery();
        weld.addBeanClass(MediatorFactory.class);
        weld.addBeanClass(MediatorManager.class);
        weld.addBeanClass(InternalChannelRegistry.class);
        weld.addBeanClass(ConnectorFactories.class);
        weld.addBeanClass(ConfiguredChannelFactory.class);
        weld.addBeanClass(WorkerPoolRegistry.class);
        weld.addBeanClass(ExecutionHolder.class);
        weld.addBeanClass(Wiring.class);
        weld.addPackages(EmitterImpl.class.getPackage());
        weld.addExtension(new ReactiveMessagingExtension());
        weld.addBeanClass(MqttConnector.class);
        weld.addBeanClass(EmitterFactoryImpl.class);
        weld.addBeanClass(MutinyEmitterFactoryImpl.class);
        weld.addBeanClass(LegacyEmitterFactoryImpl.class);

        // Add SmallRye Config
        weld.addExtension(new io.smallrye.config.inject.ConfigExtension());

        return weld;
    }

    static void addConfig(MapBasedConfig config) {
        if (config != null) {
            config.write();
        } else {
            clear();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void clear() {
        File out = new File("target/test-classes/META-INF/microprofile-config.properties");
        if (out.isFile()) {
            out.delete();
        }
    }

}
