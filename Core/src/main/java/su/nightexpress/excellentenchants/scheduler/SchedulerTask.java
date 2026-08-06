package su.nightexpress.excellentenchants.scheduler;

public interface SchedulerTask {

    void cancel();

    boolean isCancelled();
}
