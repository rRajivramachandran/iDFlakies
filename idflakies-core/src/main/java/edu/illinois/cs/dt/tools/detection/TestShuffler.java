package edu.illinois.cs.dt.tools.detection;

import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.google.gson.Gson;
import com.reedoei.eunomia.collections.ListUtil;
import com.reedoei.eunomia.io.files.FileUtil;
import edu.illinois.cs.dt.tools.runner.RunnerPathManager;
import edu.illinois.cs.dt.tools.utility.Level;
import edu.illinois.cs.dt.tools.utility.Logger;
import edu.illinois.cs.dt.tools.utility.MD5;
import edu.illinois.cs.dt.tools.utility.PathManager;
import edu.illinois.cs.dt.tools.utility.Tuscan;
import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.testrunner.data.results.TestRunResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class TestShuffler {
    public static String className(final String testName) {
        return testName.substring(0, testName.lastIndexOf('.'));
    }

    private final HashMap<String, List<String>> classToMethods;

    private final String type;
    private final List<String> tests;
    private final Set<String> alreadySeenOrders = new HashSet<>();
    private final File baseDir;

    private final Random random;

    public TestShuffler(final String type, final int rounds, final List<String> tests, final File baseDir) {
        this.type = type;
        this.tests = tests;
        this.baseDir = baseDir;

        classToMethods = new HashMap<>();

        for (final String test : tests) {
            final String className = className(test);

            if (!classToMethods.containsKey(className)) {
                classToMethods.put(className, new ArrayList<>());
            }

            classToMethods.get(className).add(test);
        }

        // Set up Random instance using passed in seed, if available
        int seed = 42;
        try {
            seed = Integer.parseInt(Configuration.config().getProperty("dt.seed", "42"));
        } catch (NumberFormatException nfe) {
            Logger.getGlobal().log(Level.INFO, "dt.seed needs to be an integer, using default seed " + seed);
        }
        this.random = new Random(seed);
    }

    private String historicalType() {
        if (type.equals("random")) {
            return Configuration.config().getProperty("detector.random.historical_type", "random-class");
        } else {
            return Configuration.config().getProperty("detector.random.historical_type", "random");
        }
    }

    public List<String> shuffledOrder(final int i,
                                      final TestRunResult lastRandomResult,
                                      final boolean useRevPassing) {
        if (useRevPassing) {
            return shuffledOrder(i);
        } else {
            Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 4 ");
            List<String> revPassingOrder = Lists.reverse(lastRandomResult.testOrder());
            String md5 = MD5.md5(String.join("", revPassingOrder));
            if (alreadySeenOrders.contains(md5)) {
                Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 5 ");
                return shuffledOrder(i);
            } else {
                Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 6 ");
                alreadySeenOrders.add(md5);
                return revPassingOrder;
            }
        }
    }

    public List<String> shuffledOrder(final int i) {
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 1 ");
        if (type.startsWith("reverse")) {
            Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 2 ");
            return reverseOrder();
        }

        final Path historicalRun = PathManager.detectionRoundPath(historicalType(), i);
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 12 ");

        try {
            // look up whether a previous execution of the plugin generated orders for this round already
            // if so, then run the same revealed order as before
            if (Files.exists(historicalRun)) {
                Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 12 completes return");
                return generateHistorical(readHistorical(historicalRun));
            }
        } catch (IOException ignored) {
            Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler shuffled order exception " + ignored);
        }

        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 12 call to gen shuffled");
        return generateShuffled();
    }

    private List<String> reverseOrder() {
        if ("reverse-class".equals(type)) {
            Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler rversing ");
            try{
            final List<String> reversedClassNames =
                    Lists.reverse(ListUtil.map(TestShuffler::className, tests).stream().distinct().collect(Collectors.toList()));

            Logger.getGlobal().log(Level.INFO, "rajivrr reverse shuffler returns");
            return reversedClassNames.stream().flatMap(c -> classToMethods.get(c).stream()).collect(Collectors.toList());
            
            }
            catch(Exception e){
                Logger.getGlobal().log(Level.INFO, "rajivrr exception in shuffler isssue"+e);
                return null;
            }
        } else {
            Logger.getGlobal().log(Level.INFO, "rajivrr returns shuffled list");
            return Lists.reverse(tests);
        }
    }

    private List<String> readHistorical(final Path historicalRun) throws IOException {
        final DetectionRound detectionRound = new Gson().fromJson(FileUtil.readFile(historicalRun), DetectionRound.class);

        return detectionRound.testRunIds().stream()
                .flatMap(n -> RunnerPathManager.resultFor(n))
                .findFirst()
                .map(TestRunResult::testOrder)
                .orElse(new ArrayList<>());
    }

    private List<String> generateHistorical(final List<String> historicalOrder) {
        if ("random-class".equals(type)) {
            return generateWithClassOrder(classOrder(historicalOrder));
        } else {
            alreadySeenOrders.add(MD5.md5(String.join("", historicalOrder)));
            return historicalOrder;
        }
    }

    private List<String> generateShuffled() {
        // sort the classes alphabetically, then shuffle as to ensure deterministic randomness
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case 12 call to gen shuffled again");
        List<String> classes = new ArrayList<>(classToMethods.keySet());
        Collections.sort(classes);
        Collections.shuffle(classes, random);
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler generateShuffled returns");
        return generateWithClassOrder(classes);
    }

    private List<String> generateWithClassOrder(final List<String> classOrder) {
        final List<String> fullTestOrder = new ArrayList<>();
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler case gen class order");
        for (final String className : classOrder) {
            Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler gen class order called "+ className);
            // random-class only shuffles classes, not methods
            if ("random-class".equals(type)) {
                fullTestOrder.addAll(classToMethods.get(className));
            } else {
                // the standard "random" type, will shuffle both
                // sort the methods alphabetically, then shuffle as to ensure deterministic randomness
                List<String> methods = classToMethods.get(className);
                Collections.sort(methods);
                Collections.shuffle(methods, random);
                fullTestOrder.addAll(methods);
            }
        }

        alreadySeenOrders.add(MD5.md5(String.join("", fullTestOrder)));
        Logger.getGlobal().log(Level.INFO, "rajivrr Test shuffler generateWithClassOrder returns");
        return fullTestOrder;
    }

    private List<String> classOrder(final List<String> historicalOrder) {
        return historicalOrder.stream().map(TestShuffler::className).distinct().collect(Collectors.toList());
    }

    @Deprecated
    private int permutations(final int rounds) {
        return permutations(IntMath.factorial(classToMethods.keySet().size()), classToMethods.values().iterator(), rounds);
    }

    @Deprecated
    private int permutations(final int accum, final Iterator<List<String>> iterator, final int rounds) {
        if (accum > rounds) {
            return accum;
        } else {
            if (iterator.hasNext()) {
                final List<String> testsInMethod = iterator.next();

                return permutations(accum * IntMath.factorial(testsInMethod.size()), iterator, rounds);
            } else {
                return accum;
            }
        }
    }

    public List<String> alphabeticalOrderSelector(int round) {
        if (this.type.equals("alphabetical-class-method")) {
            return alphabeticalClassMethodOrder();
        } else {
            return alphabeticalAndTuscanOrder(round, false);
        }
    }
    
    private List<String> alphabeticalClassMethodOrder() {
        final List<String> fullTestOrder = new ArrayList<>();
        for (String className : classToMethods.keySet()) {
            fullTestOrder.addAll(classToMethods.get(className));
        }
        Collections.sort(fullTestOrder);
        return fullTestOrder;
    }

    public List<String> alphabeticalAndTuscanOrder(int count, boolean isTuscan) {
        List<String> classes = new ArrayList<>(classToMethods.keySet());
        Collections.sort(classes);
        final List<String> fullTestOrder = new ArrayList<>();
        if (isTuscan) {
            int n = classes.size();
            int[][] res = Tuscan.generateTuscanPermutations(n);
            List<String> permClasses = new ArrayList<String>();
            for (int i = 0; i < res[count].length - 1; i++) {
                permClasses.add(classes.get(res[count][i]));
            }
            for (String className : permClasses) {
                fullTestOrder.addAll(classToMethods.get(className));
            }
        } else {
            for (String className : classes) {
                fullTestOrder.addAll(classToMethods.get(className));
            }
        }
        return fullTestOrder;
    }
}
