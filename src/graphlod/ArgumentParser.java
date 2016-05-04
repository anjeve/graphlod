package graphlod;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class ArgumentParser {
    private static Logger logger = LoggerFactory.getLogger(ArgumentParser.class);
    private final boolean skipChromatic;
    private final boolean skipGraphviz;
    private final boolean apiOnly;
    private final boolean numbersOnly;
    private final boolean exportJson;
    private final boolean exportGrami;
    private final List<String> excludedNamespaces;
    private final Boolean runGrami;
    private final Boolean runGspan;
    private String ontns;
    private final String namespace;
    private final int minImportantSubgraphSize;
    private final int importantDegreeCount;
    private final int threadcount;
    private final boolean debugMode;
    private final String output;
    private final int bigComponentSize;
    private String name;
    private final List<String> dataset;

    public ArgumentParser(String[] args, Integer MAX_SIZE_FOR_PROLOD) {
        net.sourceforge.argparse4j.inf.ArgumentParser parser = ArgumentParsers.newArgumentParser("GraphLOD")
                .defaultHelp(true).description("calculates graph features.");
        parser.addArgument("dataset").nargs("+").setDefault(Collections.emptyList());
        parser.addArgument("--name").type(String.class).setDefault("");
        parser.addArgument("--namespace").type(String.class).setDefault("");
        parser.addArgument("--ontns").type(String.class).setDefault("");
        parser.addArgument("--excludedNamespaces").nargs("*").setDefault(Collections.emptyList());
        parser.addArgument("--skipChromatic").action(Arguments.storeTrue());
        parser.addArgument("--apiOnly").action(Arguments.storeTrue());
        parser.addArgument("--numbersOnly").action(Arguments.storeTrue());
        parser.addArgument("--skipGraphviz").action(Arguments.storeTrue());
        parser.addArgument("--minImportantSubgraphSize").type(Integer.class).action(Arguments.store()).setDefault(1);
        parser.addArgument("--importantDegreeCount").type(Integer.class).action(Arguments.store()).setDefault(5);
        parser.addArgument("--threadcount").type(Integer.class).action(Arguments.store()).setDefault(4);
        parser.addArgument("--debug").action(Arguments.storeTrue());
        parser.addArgument("--output").type(String.class).setDefault("");
        parser.addArgument("--exportJson").action(Arguments.storeTrue());
        parser.addArgument("--runGrami").action(Arguments.storeTrue());
        parser.addArgument("--runGspan").action(Arguments.storeTrue());
        parser.addArgument("--exportGrami").action(Arguments.storeTrue());
        parser.addArgument("--maxSize").type(Integer.class).action(Arguments.store()).setDefault(MAX_SIZE_FOR_PROLOD);
        Namespace result = null;
        try {
            result = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        dataset = result.getList("dataset");
        name = result.getString("name");
        if (name.isEmpty()) {
            name = dataset.get(0);
            File file = new File(name);
            name = file.getName().replaceAll("\\..*", "");
        }


        namespace = result.getString("namespace");
        ontns = result.getString("ontns");
        if (ontns.isEmpty() && !namespace.isEmpty()) {
            ontns = namespace;
        }
        excludedNamespaces = result.getList("excludedNamespaces");
        skipChromatic = result.getBoolean("skipChromatic");
        skipGraphviz = result.getBoolean("skipGraphviz");
        apiOnly = result.getBoolean("apiOnly");
        numbersOnly = result.getBoolean("numbersOnly");
        exportJson = result.getBoolean("exportJson");
        exportGrami = result.getBoolean("exportGrami");
        minImportantSubgraphSize = result.getInt("minImportantSubgraphSize");
        importantDegreeCount = result.getInt("importantDegreeCount");
        threadcount = result.getInt("threadcount");
        debugMode = result.getBoolean("debug");
        output = result.getString("output");
        bigComponentSize = result.getInt("maxSize");
        runGrami = result.getBoolean("runGrami");
        runGspan = result.getBoolean("runGspan");

        logger.info("reading: " + dataset);
        logger.info("name: " + name);
        logger.info("namespace: " + namespace);
        logger.info("ontology namespace: " + ontns);
        logger.info("skip chromatic: " + skipChromatic);
        logger.info("skip graphviz: " + skipGraphviz);
        logger.info("excluded namespaces: " + excludedNamespaces);
        logger.info("min important subgraph size: " + minImportantSubgraphSize);
        logger.info("number of important degrees: " + importantDegreeCount);
        logger.info("threadcount: " + threadcount);
        logger.info("bigComponentSize: " + bigComponentSize);
        logger.info("output: " + output);
    }

    public boolean isSkipChromatic() {
        return skipChromatic;
    }

    public boolean isSkipGraphviz() {
        return skipGraphviz;
    }

    public boolean isApiOnly() {
        return apiOnly;
    }

    public boolean isNumbersOnly() {
        return numbersOnly;
    }

    public boolean isExportJson() {
        return exportJson;
    }

    public boolean isExportGrami() {
        return exportGrami;
    }

    public List<String> getExcludedNamespaces() {
        return excludedNamespaces;
    }

    public String getOntns() {
        return ontns;
    }

    public String getNamespace() {
        return namespace;
    }

    public int getMinImportantSubgraphSize() {
        return minImportantSubgraphSize;
    }

    public int getImportantDegreeCount() {
        return importantDegreeCount;
    }

    public int getThreadcount() {
        return threadcount;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getOutput() {
        return output;
    }

    public Integer getBigComponentSize() {
        return bigComponentSize;
    }

    public String getName() {
        return name;
    }

    public List<String> getDataset() {
        return dataset;
    }

    public boolean isRunGrami() {
        return runGrami;
    }

    public boolean isRunGspan() {
        return runGspan;
    }
}
