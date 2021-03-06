package com.client.simulator;

import com.ashish.marketdata.avro.Order;
import com.client.simulator.service.OrderSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class StartOrderSimulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartOrderSimulator.class);

    public static void main(String[] args) throws IOException, JMSException, InterruptedException {
        String configPath;
        if (args.length == 0) {
            LOGGER.warn("Config file not provided, loading file from default directory");
            configPath = "/order-sim.properties";
        } else {
            configPath = args[0];
        }

        Properties properties = new Properties();
        InputStream inputStream = StartOrderSimulator.class.getResourceAsStream(configPath);
        properties.load(inputStream);
        String serverUrl = properties.getProperty("exsim.kafka.bootstrap.servers");
        String orderTopic = properties.getProperty("exsim.tibcoems.ordertopic");
        String[] clients = properties.getProperty("exsim.order.sim.clients").split(",");
        String[] stocks = properties.getProperty("exsim.order.sim.symbols").split(",");
        String exchange = properties.getProperty("exsim.order.sim.exchange");
        String brokerName = properties.getProperty("exsim.order.sim.broker");
        String brokerId = properties.getProperty("exsim.order.sim.brokerId");
        String[] clientDetails = clients[0].split("-");
        int workers = Integer.parseInt(properties.getProperty("exsim.order.sim.workers"));
        String runningMode = properties.getProperty("exsim.running.mode");

        OrderSimulator orderSimulator = new OrderSimulator(serverUrl, orderTopic);
        runManualMode(stocks, exchange, brokerName, brokerId, clientDetails, workers, orderSimulator);
        //runAutomationMode(stocks, exchange, brokerName, brokerId, clientDetails, workers, orderSimulator);

    }

    private static void runManualMode(String[] stocks, String exchange, String brokerName, String brokerId, String[] clientDetails, int workers, OrderSimulator orderSimulator) throws JMSException, InterruptedException {
        BlockingQueue<Order> orderQueueForManual = new ArrayBlockingQueue<>(1024);
        orderSimulator.startSimulatorInManualMode(stocks, exchange, brokerName, brokerId, clientDetails[0], clientDetails[1], workers, true, orderQueueForManual);
        LOGGER.info("Order Simulator has been started in manual mode {}", Calendar.getInstance().getTime());
        String data;
        Scanner scanner = new Scanner(System.in);
        boolean stopManual = true;
        LOGGER.warn("Enter 2 times to stop...");
        while (stopManual) {
            Order order = new Order();
            LOGGER.info("Enter symbol, quantity,price,side");
            data = scanner.nextLine();
            if (!data.isEmpty()) {
                String [] d = data.split(",");
                order.setSymbol(d[0].toUpperCase(Locale.ROOT));
                order.setQuantity(Long.parseLong(d[1]));
                order.setLimitPrice(Double.parseDouble(d[2]));
                order.setSide(Integer.parseInt(d[3]));
            }
            /*if (data.isEmpty()) {
                stopManual = false;
                break;
            }
            LOGGER.info("Enter qunantity ");
            data = scanner.nextLine();
            if (!data.isEmpty() && Long.parseLong(data) > 0) {
                order.setQuantity(Long.parseLong(data));
            }
            if (data.isEmpty()) {
                stopManual = false;
                break;
            }
            LOGGER.info("Enter price ");
            data = scanner.nextLine();
            if (!data.isEmpty() && Double.parseDouble(data) > 0) {
                order.setLimitPrice(Double.parseDouble(data));
            }
            if (data.isEmpty()) {
                stopManual = false;
                break;
            }
            LOGGER.info("Enter side (Buy 1, Sell 2 ) ");
            data = scanner.nextLine();
            if (!data.isEmpty() && Integer.parseInt(data) > 0) {
                order.setSide(Integer.parseInt(data));
            }*/
            order.setExchange("NSE");
            order.setBrokerId("Zero001");
            order.setBrokerName("Zerodha");
            order.setClientId("Black001");
            order.setClientName("Black");
            order.setOrderStatus("open");
            order.setFilledQuantity(0l);
            order.setRemainingQuantity(order.getQuantity());
            order.setOrdertime(Calendar.getInstance().getTimeInMillis());
            order.setOrderId(UUID.randomUUID().toString());
            orderQueueForManual.put(order);
        }
        orderSimulator.shutDown();
    }

    private static void runAutomationMode(String[] stocks, String exchange, String brokerName, String brokerId, String[] clientDetails, int workers, OrderSimulator orderSimulator) throws JMSException {
        orderSimulator.startSimulatorInAutomaticMode(stocks, exchange, brokerName, brokerId, clientDetails[0], clientDetails[1], workers, false, null);
        LOGGER.info("Order Simulator has been started in automatic mode {}", Calendar.getInstance().getTime());
        Scanner scanner = new Scanner(System.in);
        LOGGER.warn("Enter to stop automatic mode...");
        String run = scanner.nextLine();
        while (run.isEmpty()) {
            LOGGER.warn("Turning down application...");
            orderSimulator.shutDown();
        }
    }

}
