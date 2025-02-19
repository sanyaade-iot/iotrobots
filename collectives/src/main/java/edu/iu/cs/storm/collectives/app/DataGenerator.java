package edu.iu.cs.storm.collectives.app;

import cgl.iotcloud.core.transport.TransportConstants;
import cgl.iotrobots.utils.rabbitmq.*;
import com.esotericsoftware.kryo.Kryo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class DataGenerator {
    RabbitMQSender dataSender;
    RabbitMQSender controlSender;
    RabbitMQReceiver bestReceiver;
    FileIO resultBestIO;
    int dataSize;
    Kryo kryo = new Kryo();
    long sleepTime;
    int messages;

    public DataGenerator(String url, String test, long sleepTime, int size, int messages) {
        try {
            dataSender = new RabbitMQSender(url, "simbard_laser");
            controlSender = new RabbitMQSender(url, "simbard_control");
            bestReceiver = new RabbitMQReceiver(url, "simbard_best");

            dataSender.open();
            controlSender.open();
            bestReceiver.listen(new TraceReceiver());

            resultBestIO = new FileIO(test, true);
            this.sleepTime = sleepTime;
            this.dataSize = size;
            this.messages = messages;
            Utils.registerClasses(kryo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() throws InterruptedException {
        byte[] body = "start".getBytes();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("time", System.currentTimeMillis());
        props.put(TransportConstants.SENSOR_ID, "test");
        Message message = new Message(body, props);
        try {
            controlSender.send(message, "test.test.control");
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread t = new Thread(new SendWorker());
        t.start();
        t.join();
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 3) {
            System.out.println("Please specify amqp url, filename and test name as arguments");
        }

        DataGenerator fileBasedSimulator = new DataGenerator(args[0], args[1], Long.parseLong(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        fileBasedSimulator.start();
    }

    private class SendWorker implements Runnable {
        @Override
        public void run() {
            long t0 = System.currentTimeMillis();
            int count = 0;
            while (count < messages) {
                byte []body = Utils.generateData(dataSize);
                Map<String, Object> props = new HashMap<String, Object>();
                props.put("time", System.currentTimeMillis());
                props.put(TransportConstants.SENSOR_ID, "test");
                Message message = new Message(body, props);
                try {
                    dataSender.send(message, "test.test.laser_scan");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                count++;
            }
            System.exit(1);
        }
    }

    int count = 0;
    double sum = 0;

    private class TraceReceiver implements MessageHandler {
        @Override
        public Map<String, String> getProperties() {
            Map<String, String> props = new HashMap<String, String>();
            props.put(MessagingConstants.RABBIT_ROUTING_KEY, "test.test.best");
            props.put(MessagingConstants.RABBIT_QUEUE, "test.test.best");
            return props;
        }

        @Override
        public void onMessage(Message message) {
            Object time = message.getProperties().get("time");
            long receiveTime = System.currentTimeMillis();
            BTrace trace = (BTrace) Utils.deSerialize(kryo, message.getBody(), BTrace.class);
            Field[] fs = trace.getClass().getDeclaredFields();
            for (Field f : fs) {
                System.out.println(f.getName() + " " + f.getType().toString() + " " + f.getModifiers());
            }
            resultBestIO.writeResult((receiveTime - trace.getTime()) + "");
            long l = Long.parseLong(time.toString());
            sum += (receiveTime - l);
            count++;
            System.out.println((receiveTime - l));
            System.out.println("Average: " + (sum / count));
        }
    }
}
