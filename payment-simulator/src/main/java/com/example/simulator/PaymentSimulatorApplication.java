package com.example.simulator;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;

public final class PaymentSimulatorApplication {
    private PaymentSimulatorApplication() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        SimulatorConfig config = SimulatorConfig.fromEnv();
        SimulatorServer server = new SimulatorServer(config, Clock.systemUTC());
        server.start();
        System.out.println("Payment Simulator started on port " + server.port()
                + " (publicOrigin=" + config.publicOrigin()
                + ", webhookUrl=" + config.webhookUrl()
                + ", stateFile=" + config.stateFile() + ")");
        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            shutdown.countDown();
        }));
        shutdown.await();
    }
}
