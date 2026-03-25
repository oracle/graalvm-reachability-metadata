/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.graalvm.internal.tck.Utils.splitCoordinates;

/**
 * Prints a machine-readable dependency graph obtained from deps.dev for the resolved coordinates.
 *
 * Output:
 * - For each input coordinate (resolved via CoordinatesAwareTask -Pcoordinates), prints a single
 *   JSON object containing the root GAV and its transitively fetched Maven dependency graph as
 *   `nodes[*].id` and `nodes[*].dependencies`.
 *
 * API:
 * - Endpoint: https://api.deps.dev/v3/systems/maven/versions/{G}:{A}:{V}/dependencies
 * - We tolerate minor response shape variations by reading dynamically via Jackson.
 * - We only follow Maven nodes. If scope is present, "test" is skipped; if "optional" is present and true, skipped.
 */
@SuppressWarnings("unused")
public class GenerateDependencyGraphTask extends CoordinatesAwareTask {

    private static final String BASE_URL = "https://api.deps.dev/v3/systems/maven/versions/%s/dependencies";
    private static final String USER_AGENT = "graalvm-reachability-metadata/GenerateDependencyGraphTask (+https://github.com/oracle/graalvm-reachability-metadata)";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * Small value object describing a dependency version (Maven).
     *
     * @param scope    may be null
     * @param optional may be absent in payload - default false
     */
    private record Dep(String group, String artifact, String version, String scope, boolean optional) {
        private Dep(String group, String artifact, String version, String scope, boolean optional) {
            this.group = Objects.requireNonNull(group, "group");
            this.artifact = Objects.requireNonNull(artifact, "artifact");
            this.version = Objects.requireNonNull(version, "version");
            this.scope = scope;
            this.optional = optional;
        }

        String gav() {
            return group + ":" + artifact + ":" + version;
        }
    }

    /**
     * Fallback-aware coordinate resolution:
     * - First, use the default metadata-scoped resolution from CoordinatesAwareTask.
     * - If it yields no matches, but the user supplied an explicit G:A:V via -Pcoordinates,
     *   accept that raw coordinate even if it's not present in repository metadata.
     */
    @Override
    protected List<String> resolveCoordinates() {
        List<String> resolved = super.resolveCoordinates();
        if (resolved != null && !resolved.isEmpty()) {
            return resolved;
        }
        Object prop = getProject().findProperty("coordinates");
        String filter = prop == null ? "" : prop.toString().trim();
        if (!filter.isEmpty() && !filter.startsWith("samples:")) {
            String[] parts = filter.split(":", -1);
            if (parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank()) {
                return List.of(filter);
            }
        }
        return Collections.emptyList();
    }

    @TaskAction
    public void printGraphs() {
        List<String> coords = resolveCoordinates();
        if (coords.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found. Nothing to do.");
            return;
        }
        for (String coordinate : coords) {
            try {
                printSingleGraph(coordinate);
            } catch (Exception e) {
                throw new GradleException("Failed to print dependency graph for " + coordinate + ": " + e.getMessage(), e);
            }
        }
    }

    private void printSingleGraph(String coordinate) {
        // Header
        // Suppressed verbose header print

        // BFS over dependency graph; fetch each node's direct deps via deps.dev and print edges "parent -> child"
        Deque<String> queue = new ArrayDeque<>();
        Set<String> fetched = new HashSet<>(); // nodes already fetched (avoid repeated HTTP calls)
        Map<String, List<String>> edges = new LinkedHashMap<>(); // preserve discovery order
        Map<String, Integer> depth = new LinkedHashMap<>(); // BFS discovery depth (root=0)

        depth.put(coordinate, 0);
        queue.add(coordinate);

        while (!queue.isEmpty()) {
            String parent = queue.removeFirst();
            if (!fetched.add(parent)) {
                continue;
            }
            List<Dep> direct = fetchDirectDeps(parent);
            if (direct.isEmpty()) {
                continue;
            }
            // Keep children deterministic (sort by GAV)
            // Copy to a mutable list, because cached lists are unmodifiable (List.copyOf).
            direct = new ArrayList<>(direct);
            direct.sort(Comparator.comparing(Dep::gav));
            for (Dep d : direct) {
                String child = d.gav();
                edges.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
                if (!fetched.contains(child)) {
                    if (!depth.containsKey(child)) {
                        depth.put(child, depth.getOrDefault(parent, 0) + 1);
                    }
                    queue.addLast(child);
                }
            }
        }

        try {
            LinkedHashSet<String> nodes = new LinkedHashSet<>();
            nodes.add(coordinate);
            for (Map.Entry<String, List<String>> en : edges.entrySet()) {
                nodes.add(en.getKey());
                nodes.addAll(en.getValue());
            }

            List<Map<String, Object>> graphNodes = new ArrayList<>();
            List<String> sorted = new ArrayList<>(nodes);
            sorted.sort(Comparator.comparingInt((String n) -> depth.getOrDefault(n, 0)).thenComparing(n -> n));

            for (String n : sorted) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", n);
                entry.put("dependencies", new ArrayList<>(edges.getOrDefault(n, Collections.emptyList())));
                graphNodes.add(entry);
            }

            Map<String, Object> graph = new LinkedHashMap<>();
            graph.put("root", coordinate);
            graph.put("nodes", graphNodes);

            String json = MAPPER.writeValueAsString(graph);
            System.out.println(json);
            System.out.flush();
        } catch (Exception e) {
            throw new GradleException("Failed to serialize dependency graph for " + coordinate + ": " + e.getMessage(), e);
        }
    }

    /**
     * Fetches direct dependencies for a single Maven GAV via deps.dev, with simple retries.
     */
    private List<Dep> fetchDirectDeps(String coordinate) {
        List<String> parts = splitCoordinates(coordinate);
        if (parts.size() != 3 || parts.get(0) == null || parts.get(1) == null || parts.get(2) == null) {
            throw new GradleException("Invalid coordinates '" + coordinate + "'. Expected 'group:artifact:version'.");
        }
        String group = parts.get(0);
        String artifact = parts.get(1);
        String version = parts.get(2);

        String versionKey = group + ":" + artifact + ":" + version;
        String ga = group + ":" + artifact;
        // Preferred endpoints (note the colon suffix before 'dependencies')
        String versionsUrlEncoded = "https://api.deps.dev/v3/systems/maven/versions/"
                + java.net.URLEncoder.encode(versionKey, UTF_8) + ":dependencies";
        String versionsUrlPlain = "https://api.deps.dev/v3/systems/maven/versions/"
                + versionKey + ":dependencies";
        String packagesUrlEncoded = "https://api.deps.dev/v3/systems/maven/packages/"
                + java.net.URLEncoder.encode(ga, UTF_8) + "/versions/"
                + java.net.URLEncoder.encode(version, UTF_8) + ":dependencies";
        String packagesUrlPlain = "https://api.deps.dev/v3/systems/maven/packages/"
                + ga + "/versions/" + version + ":dependencies";

        // Basic at-most-once per run cache to reduce repeated calls when graph has shared nodes
        DepCache cache = DepCache.INSTANCE;
        List<Dep> cached = cache.get(versionKey);
        if (cached != null) {
            if (!cached.isEmpty()) {
                return cached;
            }
            // fall through to re-fetch if previously cached empty
        }

        List<String> urls = java.util.List.of(
                versionsUrlEncoded,
                versionsUrlPlain,
                packagesUrlEncoded,
                packagesUrlPlain
        );
        int maxAttempts = 4;

        for (String url : urls) {
            int attempts = 0;
            long backoffMillis = 750L;

            while (true) {
                attempts++;
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .header("Accept", "application/json")
                            .header("User-Agent", USER_AGENT)
                            .GET()
                            .build();
                    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(UTF_8));
                    int code = resp.statusCode();
                    if (code == 200) {
                        List<Dep> deps = parseDepsResponse(resp.body());
                        if (!deps.isEmpty()) {
                            cache.put(versionKey, deps);
                            return deps;
                        } else {
                            break;
                        }
                    } else if ((code == 429 || (code >= 500 && code < 600)) && attempts < maxAttempts) {
                        // Retry with exponential backoff
                        sleepQuiet(backoffMillis);
                        backoffMillis = Math.min(8000L, backoffMillis * 2);
                    } else if (code == 404) {
                        // Try next candidate URL shape
                        break;
                    } else {
                        // Non-retryable failure for this URL; try next if available
                        break;
                    }
                } catch (IOException | InterruptedException e) {
                    if (attempts < maxAttempts) {
                        sleepQuiet(backoffMillis);
                        backoffMillis = Math.min(8000L, backoffMillis * 2);
                    } else {
                        break;
                    }
                }
            }
        }

        // No results from any candidate endpoint
        return java.util.Collections.emptyList();
    }

    /**
     * Parses deps.dev dependency response, tolerating minor schema variations:
     * - dependencies[*].versionKey.{system,name,version}
     * - or dependencies[*].version.{system,name,version}
     * Optional fields we read if present:
     * - dependencies[*].scope
     * - dependencies[*].optional
     */
    @SuppressWarnings("unchecked")
    private List<Dep> parseDepsResponse(String json) {
        try {
            Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            // Shape A: dependencies array with versionKey objects per entry
            Object depsObj = root.get("dependencies");
            if (depsObj instanceof Collection) {
                List<Dep> out = new ArrayList<>();
                for (Object o : (Collection<?>) depsObj) {
                    if (!(o instanceof Map)) continue;
                    Map<String, Object> dep = (Map<String, Object>) o;

                    Map<String, Object> vk = asMapOrNull(dep.get("versionKey"));
                    if (vk == null) vk = asMapOrNull(dep.get("resolvedVersionKey"));
                    if (vk == null) vk = asMapOrNull(dep.get("version"));

                    Map<String, Object> pk = asMapOrNull(dep.get("packageKey"));

                    String system = stringOrNull(vk != null ? vk.get("system") : null);
                    if (system == null && pk != null) system = stringOrNull(pk.get("system"));
                    if (system == null || !system.equalsIgnoreCase("maven")) continue;

                    String name = stringOrNull(vk != null ? vk.get("name") : null);
                    if (name == null && pk != null) name = stringOrNull(pk.get("name"));

                    String ver = stringOrNull(vk != null ? vk.get("version") : null);
                    if (ver == null) ver = stringOrNull(dep.get("version"));
                    if (ver == null) ver = stringOrNull(dep.get("resolvedVersion"));

                    if (name == null || ver == null) continue;

                    int idx = name.indexOf(':');
                    if (idx <= 0 || idx == name.length() - 1) continue;
                    String group = name.substring(0, idx);
                    String artifact = name.substring(idx + 1);

                    String scope = stringOrNull(dep.get("scope"));
                    boolean optional = booleanOrFalse(dep.get("optional"));

                    String kind = stringOrNull(dep.get("kind"));
                    if (kind != null) {
                        String k = kind.toLowerCase(Locale.ROOT);
                        if (k.contains("test") || k.contains("dev") || k.contains("development")) {
                            continue;
                        }
                    }
                    if (scope != null && scope.equalsIgnoreCase("test")) continue;
                    if (optional) continue;

                    out.add(new Dep(group, artifact, ver, scope, optional));
                }
                LinkedHashMap<String, Dep> uniq = new LinkedHashMap<>();
                for (Dep d : out) uniq.putIfAbsent(d.gav(), d);
                return new ArrayList<>(uniq.values());
            }

            // Shape B: graph with nodes[] and edges[]; direct deps are edges from root (node index 0)
            Object nodesObj = root.get("nodes");
            Object edgesObj = root.get("edges");
            if (nodesObj instanceof List<?> nodes && edgesObj instanceof List<?> edges) {
                if (nodes.isEmpty()) return Collections.emptyList();

                // Build children list from edges where fromNode == 0
                List<Integer> directIdx = new ArrayList<>();
                for (Object eo : edges) {
                    if (!(eo instanceof Map)) continue;
                    Map<String, Object> e = (Map<String, Object>) eo;
                    Integer from = e.get("fromNode") instanceof Number n ? n.intValue() : null;
                    Integer to = e.get("toNode") instanceof Number n2 ? n2.intValue() : null;
                    if (from != null && from == 0 && to != null) {
                        directIdx.add(to);
                    }
                }
                // Deduplicate and preserve order
                LinkedHashSet<Integer> uniqTo = new LinkedHashSet<>(directIdx);
                List<Dep> out = new ArrayList<>();
                for (Integer idx : uniqTo) {
                    if (idx == null || idx < 0 || idx >= nodes.size()) continue;
                    Object no = nodes.get(idx);
                    if (!(no instanceof Map)) continue;
                    Map<String, Object> n = (Map<String, Object>) no;
                    Map<String, Object> vk = asMapOrNull(n.get("versionKey"));
                    if (vk == null) continue;

                    String system = stringOrNull(vk.get("system"));
                    if (system == null || !system.equalsIgnoreCase("maven")) continue;

                    String name = stringOrNull(vk.get("name"));
                    String ver = stringOrNull(vk.get("version"));
                    if (name == null || ver == null) continue;

                    int p = name.indexOf(':');
                    if (p <= 0 || p == name.length() - 1) continue;
                    String group = name.substring(0, p);
                    String artifact = name.substring(p + 1);

                    // Edges may carry kind (runtime/test); prefer edge info for this node if available
                    String scope = null;
                    boolean optional = false;
                    out.add(new Dep(group, artifact, ver, scope, optional));
                }
                return out;
            }

            return Collections.emptyList();
        } catch (IOException e) {
            throw new GradleException("Failed to parse deps.dev response: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> asMapOrNull(Object o) {
        if (o instanceof Map) {
            //noinspection unchecked
            return (Map<String, Object>) o;
        }
        return null;
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean booleanOrFalse(Object o) {
        if (o instanceof Boolean b) return b;
        if (o == null) return false;
        String s = String.valueOf(o).trim();
        return s.equalsIgnoreCase("true") || s.equals("1");
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Very small per-process cache to avoid refetching the same GAV multiple times during traversal.
     */
    private static final class DepCache {
        static final DepCache INSTANCE = new DepCache();
        private final Map<String, List<Dep>> map = new ConcurrentHashMap<>();

        List<Dep> get(String gav) {
            return map.get(gav);
        }

        void put(String gav, List<Dep> deps) {
            map.put(gav, deps == null ? Collections.emptyList() : List.copyOf(deps));
        }
    }
}
