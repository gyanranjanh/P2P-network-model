package p2putil;

public class P2pUtilWaitNotify {
    private final Object p2pMonitorObject = new Object();;

    public P2pUtilWaitNotify() {}

    public void doWait() {
        synchronized(p2pMonitorObject) {
            try {
                p2pMonitorObject.wait();
            } catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    public void doNotify() {
        synchronized(p2pMonitorObject) {
            p2pMonitorObject.notify();
        }
    }

    public void doNotifyAll() {
        synchronized(p2pMonitorObject) {
            p2pMonitorObject.notifyAll();
        }
    }

}

