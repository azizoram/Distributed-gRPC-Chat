package cz.cvut.fel.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Slf4j(topic = "main_topic")
public class ElectionUntilDelayHandler implements DelayHandler {

    private LocalTime targetTime = null;

    @Override
    public void set(String param, String value) {
        if (!param.equals("date")) {
            return;
        }
        log.info("Setting election delay until " + value);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        this.targetTime = LocalTime.parse(value, formatter);
    }

    @Override
    public void handleRequestDelay(String method) {
        if (targetTime == null
                || !method.equals("START_ELECTION")
        ) {
            return;
        }

        String msg = "Suspending election until " + targetTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        log.info(msg);

        while (LocalTime.now().isBefore(targetTime)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Resuming election");
    }

    @Override
    public void handleResponseDelay(String method) {
        // You can implement response delay logic here if needed
    }
}
