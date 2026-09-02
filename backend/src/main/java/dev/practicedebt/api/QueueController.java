package dev.practicedebt.api;

import dev.practicedebt.queue.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The unified debt queue. */
@RestController
@RequestMapping("/api/handles/{handle}")
public class QueueController {

    private final QueueService queue;

    public QueueController(QueueService queue) {
        this.queue = queue;
    }

    @GetMapping("/queue")
    public QueueService.Queue queue(@PathVariable String handle) {
        return queue.forHandle(handle);
    }
}
