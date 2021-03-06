package com.client.simulator.senders;

import com.ashish.marketdata.avro.Order;
import com.client.simulator.broker.KafkaBroker;
import com.client.simulator.service.PriceRange;
import com.client.simulator.util.Throughput;
import com.client.simulator.util.Utility;
import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class OrderSender implements Runnable, ExceptionListener {

    private volatile boolean running = true;
    final private String topic;
    final private String[] symbols;
    final private String exchange;
    final private String brokerName;
    final private String brokerId;
    final private String clientId;
    final private String clientName;
    private PriceRange priceRange;
    private KafkaProducer<String, String> kafkaProducer;
    private Throughput throughput;
    private boolean manualMode;
    private BlockingQueue<Order> inputQueue;


    private static final Logger LOGGER = LoggerFactory.getLogger(OrderSender.class);

    public OrderSender(String serverUrl, String topic, String[] symbols, String exchange,
                       String brokerName, String brokerId, String clientId, String clientName,
                       Throughput throughputWorker, boolean manualMode, BlockingQueue<Order> inputQueue) {
        this.topic = topic;
        this.symbols = symbols;
        this.exchange = exchange;
        this.brokerName = brokerName;
        this.brokerId = brokerId;
        this.clientId = clientId;
        this.clientName = clientName;
        Properties optionalProperties = new Properties();
        optionalProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        optionalProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        optionalProperties.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        optionalProperties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        // high throughput setting
        optionalProperties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        optionalProperties.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        optionalProperties.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024));
        kafkaProducer = new KafkaBroker(serverUrl).createProducer((optionalProperties)); // create producer
        this.throughput = throughputWorker;
        this.manualMode = manualMode;
        this.inputQueue = inputQueue;
        LOGGER.info("Order sending started by client {} ", clientName);
    }

    @Override
    public void run() {
        ThreadLocalRandom localRandom = ThreadLocalRandom.current();
        long start = System.currentTimeMillis();
        int msgCount = 0;
        while (isRunning()) {
            try {
                String randomStock = symbols[localRandom.nextInt(symbols.length)];
                Order newOrder = null;
                if (manualMode) {
                    // take it from Queue
                    newOrder = inputQueue.poll();
                } else {
                    newOrder = OrderCreator.createSingleOrder(randomStock, exchange, brokerName, brokerId, clientId, clientName);
                }
                if (newOrder == null) {
                    continue;
                }
                byte[] encoded = Utility.serealizeAvroHttpRequestJSON(newOrder);
                String encodedOrder = Base64.getEncoder().encodeToString(encoded);
                publishToKafka(newOrder, encodedOrder);
                Thread.sleep(5000);
                LOGGER.info("Order {} sent by {}", newOrder, newOrder.getClientName());
            } catch (Exception e) {
                LOGGER.error("Error occurred while sending order " + e.fillInStackTrace());
            }
            msgCount++;
        }
        LOGGER.warn("Thread {} received shutdown signal ", Thread.currentThread().getName());
        long end = System.currentTimeMillis();
        long timeT = (end - start) / 1000;
        long msgF = msgCount;
        LOGGER.info("Message rate/sec {}", msgF / timeT);
    }

    private void publishToKafka(Order newOrder, String encodedOrder) {
        String symbol = newOrder.getSymbol().toString();
        ProducerRecord<String, String> producerRecord = new ProducerRecord<String, String>(topic, symbol, encodedOrder);
        kafkaProducer.send(producerRecord, new Callback() {
            @Override
            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                if (e == null) {
                    LOGGER.info("Key {}", symbol);
                    LOGGER.info("Topic {} ", recordMetadata.topic());
                    LOGGER.info("Partition {}", recordMetadata.partition());
                    LOGGER.info("Offset {}", recordMetadata.offset());
                } else {
                    LOGGER.info("Exception Occurred while sending order through kafka... {}", e.getLocalizedMessage());
                }
            }
        });
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void onException(JMSException e) {

    }
}
