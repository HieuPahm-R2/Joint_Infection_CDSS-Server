package com.vietnam.pji.controller.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Decision 0010 runs the {@code /episodes} to {@code /admissions} rename as an
 * additive deprecation window: both paths must resolve to the same handler until
 * the frontend has migrated and the legacy path can be retired.
 *
 * <p>
 * This asserts that invariant against the registered handler mappings rather
 * than against source text, so an episode-scoped route added without its
 * alias fails here instead of in production. When the window closes, this test
 * is the deliberate place to flip: it will fail until the legacy paths are
 * removed and the expectations are inverted.
 */
@SpringBootTest
class AdmissionRouteAliasTest {

    @Value("${api.prefix}")
    private String apiPrefix;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyLegacyEpisodeRouteHasItsAdmissionsAlias() {
        Set<String> patterns = registeredPatterns();
        String legacyPrefix = apiPrefix + "/episodes";

        Set<String> legacy = patterns.stream()
                .filter(pattern -> pattern.startsWith(legacyPrefix))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(legacy)
                .as("legacy /episodes routes are still served during the deprecation window")
                .isNotEmpty();

        String targetPrefix = apiPrefix + "/admissions";
        Set<String> missing = legacy.stream()
                .map(pattern -> targetPrefix + pattern.substring(legacyPrefix.length()))
                .filter(alias -> !patterns.contains(alias))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(missing)
                .as("episode-scoped routes that do not also answer on /admissions")
                .isEmpty();
    }

    @Test
    void aliasCountMatchesLegacyRouteCount() {
        Set<String> patterns = registeredPatterns();
        long legacy = patterns.stream().filter(pattern -> pattern.startsWith(apiPrefix + "/episodes")).count();
        long aliases = patterns.stream().filter(pattern -> pattern.startsWith(apiPrefix + "/admissions")).count();

        assertThat(aliases)
                .as("number of /admissions routes must equal the number of /episodes routes")
                .isEqualTo(legacy);
    }

    private Set<String> registeredPatterns() {
        Set<String> patterns = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() != null) {
                info.getPathPatternsCondition().getPatterns()
                        .forEach(pattern -> patterns.add(pattern.getPatternString()));
            }
            else if (info.getPatternsCondition() != null) {
                patterns.addAll(info.getPatternsCondition().getPatterns());
            }
        }
        return patterns;
    }
}
