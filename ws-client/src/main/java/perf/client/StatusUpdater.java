package perf.client;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Nitesh Kant
 */
public class StatusUpdater {

    private LinkedBlockingQueue<Long> items = new LinkedBlockingQueue<Long>();

    private int wrapAt = 80;

    private volatile boolean keepRunning = true;

    private Thread updater = new Thread(new Runnable() {
        @Override
        public void run() {
            while (keepRunning) {
                Long item;
                try {
                    item = items.take();
                    if(item % wrapAt == 0) {
                        System.out.println(".");
                    } else {
                        System.out.print(".");
                    }
                } catch (InterruptedException e) {
                    // exit
                    if (keepRunning) {
                        System.out.println("Status updater interrupted, no more status will be shown.");
                    }
                    break;
                }
            }
        }
    });

    public StatusUpdater(int wrapAt) {
        this.wrapAt = wrapAt;
    }

    public void start() {
        updater.start();
    }

    public void stop() {
        keepRunning = false;
        updater.interrupt();
    }

    public void onNewItem(Long count) {
        items.add(count);
    }
}
