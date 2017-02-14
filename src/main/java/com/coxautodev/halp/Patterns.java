package com.coxautodev.halp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class Patterns {

    private static String escapeDots(String s) { return s.replace(".", "\\."); }

    private static String getPlaceHolder(String pattern) {
        for (int i=0; i<200; i++) {
            String s = ""+i;
            if (!pattern.contains(s)) {
                return s;
            }
        }
        throw new RuntimeException("oops");
    }

     private static String escapeStars(String p) {
        if (p.contains("***")) throw new IllegalArgumentException("More than two '*'s in a row is not a supported pattern.");
        String doubleStarPlaceHolder = getPlaceHolder(p);
        String singleStarPlaceHolder = getPlaceHolder(p + doubleStarPlaceHolder);
        return p.
            replace("**", doubleStarPlaceHolder).
            replace("*", singleStarPlaceHolder).
            replace(doubleStarPlaceHolder, ".*").
            replace(singleStarPlaceHolder, "[^\\.]*");
    }

    private static final Pattern parensPattern = Pattern.compile(".*\\(.*\\).*");

    private static String ensureParens(String p) {
        Matcher m = parensPattern.matcher(p);
        if (m.find()) {
            return p;
        }
        else {
            return "(" + p + ")";
        }
    }

    public interface PatternMatcher {
        boolean matches(String s);
        Map<String, Integer> usage();
    }

    private static class PatternInfo {
        final String raw;
        final Pattern pattern;
        final AtomicInteger matches = new AtomicInteger();
        PatternInfo(String raw, Pattern pattern) {
            this.raw = raw;
            this.pattern = pattern;
        }
    }

    public static PatternMatcher pattern(String...includes) {
        return pattern(asList(includes));
    }

    public static PatternMatcher pattern(Iterable<String> includes) {

        final List<PatternInfo> patterns = new ArrayList();

        for (String include : includes) {
            String escaped = ensureParens(escapeStars(escapeDots(include)));
            Pattern p = Pattern.compile(escaped);
            patterns.add(new PatternInfo(include, p));
        }

        return new PatternMatcher() {
            @Override
            public boolean matches(String s) {
                for (PatternInfo p : patterns) {
                    Matcher m = p.pattern.matcher(s);
                    if (m.matches()) {
                        p.matches.incrementAndGet();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Map<String, Integer> usage() {
                Map<String, Integer> usage = new HashMap();
                for (PatternInfo i : patterns) {
                    usage.put(i.raw, i.matches.get());
                }
                return usage;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("[");

                List<String> lines = patterns.stream()
                    .map(i -> i.pattern.pattern())
                    .collect(toList());

                sb.append(String.join(", ", lines));
                sb.append("]");
                return sb.toString();
            }
        };
    }
}
