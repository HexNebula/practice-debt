package dev.practicedebt.api;

import java.util.List;

import dev.practicedebt.decay.DecayedDebtService;
import dev.practicedebt.decay.TechniqueSnapshotRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Decayed debt, snapshots, and the calibration evidence behind the decay guess. */
@RestController
@RequestMapping("/api/handles/{handle}")
public class DecayController {

    private final DecayedDebtService decay;

    public DecayController(DecayedDebtService decay) {
        this.decay = decay;
    }

    @GetMapping("/debt/decayed")
    public DecayedDebtService.Report decayed(@PathVariable String handle) {
        return decay.reportFor(handle);
    }

    /** Records where every technique stands now, and re-derives the calibration evidence. */
    @PostMapping("/decay/snapshot")
    public DecayedDebtService.SnapshotResult snapshot(@PathVariable String handle) {
        return decay.snapshot(handle);
    }

    @GetMapping("/decay/history/{techniqueId}")
    public List<TechniqueSnapshotRepository.Point> history(@PathVariable String handle,
            @PathVariable String techniqueId) {
        return decay.history(handle, techniqueId);
    }
}
