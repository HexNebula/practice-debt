package dev.practicedebt.api;

import dev.practicedebt.taxonomy.TaxonomyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The technique taxonomy and its coverage of the mirrored problemset. */
@RestController
@RequestMapping("/api/taxonomy")
public class TaxonomyController {

    private final TaxonomyService taxonomy;

    public TaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping
    public TaxonomyService.Result current() {
        return taxonomy.current();
    }

    /** Re-reads the taxonomy file and remaps every mirrored problem onto it. */
    @PostMapping("/apply")
    public TaxonomyService.Result apply() {
        return taxonomy.apply();
    }
}
