package dev.practicedebt.taxonomy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads the taxonomy file.
 *
 * <p>Parsing is strict on purpose. A typo in a technique id or a duplicated entry would quietly
 * change which problems map where, and the mapping is the whole point of the file.
 */
@Component
public class TaxonomyLoader {

    private final Resource file;

    public TaxonomyLoader(
            @Value("${practicedebt.taxonomy.file:classpath:taxonomy/techniques-v1.yaml}")
            Resource file) {
        this.file = file;
    }

    public Taxonomy load() {
        try (InputStream in = file.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            if (root == null) {
                throw new IllegalStateException("Taxonomy file " + file + " is empty");
            }
            return parse(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read taxonomy file " + file, e);
        }
    }

    private static Taxonomy parse(Map<String, Object> root) {
        int version = requireInt(root, "version");
        String revised = String.valueOf(root.getOrDefault("revised", ""));
        int maxPerProblem = root.containsKey("maxTechniquesPerProblem")
                ? requireInt(root, "maxTechniquesPerProblem")
                : 3;

        List<Map<String, Object>> raw = asList(root.get("techniques"));
        if (raw.isEmpty()) {
            throw new IllegalStateException("Taxonomy defines no techniques");
        }

        List<Taxonomy.Technique> techniques = new ArrayList<>(raw.size());
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> entry : raw) {
            Taxonomy.Technique technique = parseTechnique(entry);
            if (!seen.add(technique.id())) {
                throw new IllegalStateException("Duplicate technique id: " + technique.id());
            }
            techniques.add(technique);
        }
        return new Taxonomy(version, revised, maxPerProblem, List.copyOf(techniques));
    }

    private static Taxonomy.Technique parseTechnique(Map<String, Object> entry) {
        String id = requireString(entry, "id");
        List<Taxonomy.Rule> rules = asList(entry.get("rules")).stream()
                .map(TaxonomyLoader::parseRule)
                .toList();
        Set<String> pinned = stringSet(entry.get("pinned"));
        Set<String> excluded = stringSet(entry.get("excluded"));

        if (rules.isEmpty() && pinned.isEmpty()) {
            throw new IllegalStateException(
                    "Technique " + id + " can never match: it has no rules and no pinned problems");
        }
        return new Taxonomy.Technique(
                id,
                requireString(entry, "name"),
                requireString(entry, "family"),
                requireString(entry, "summary"),
                Boolean.TRUE.equals(entry.get("whenUnclaimed")),
                rules,
                pinned,
                excluded);
    }

    private static Taxonomy.Rule parseRule(Map<String, Object> entry) {
        Taxonomy.Rule rule = new Taxonomy.Rule(
                stringList(entry.get("allTags")),
                stringList(entry.get("anyTags")),
                stringList(entry.get("noneTags")),
                (Integer) entry.get("minRating"),
                (Integer) entry.get("maxRating"));

        if (rule.allTags().isEmpty() && rule.anyTags().isEmpty()) {
            // A rule with only rating bounds would claim thousands of unrelated problems.
            throw new IllegalStateException("Rule must constrain tags: " + entry);
        }
        return rule;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return value == null ? List.of() : List.copyOf((List<String>) value);
    }

    private static Set<String> stringSet(Object value) {
        return value == null ? Set.of() : new LinkedHashSet<>(stringList(value));
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("Missing '" + key + "' in " + map.get("id"));
        }
        return String.valueOf(value).strip();
    }

    private static int requireInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Integer number)) {
            throw new IllegalStateException("Missing or non-numeric '" + key + "'");
        }
        return number;
    }
}
