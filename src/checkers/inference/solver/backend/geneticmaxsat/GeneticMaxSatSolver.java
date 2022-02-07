package checkers.inference.solver.backend.geneticmaxsat;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.backend.maxsat.MaxSatSolver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.FileUtils;
import checkers.inference.solver.util.SolverEnvironment;
import org.checkerframework.javacutil.BugInCF;
import org.plumelib.util.Pair;

import javax.lang.model.element.AnnotationMirror;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeneticMaxSatSolver extends MaxSatSolver {

    public int allSoftWeightsCount = 0;
    public long allSoftWeightsSum = 0;
    public List<Integer> oldSoftWeights = new LinkedList<>();
    public List<Pair<Integer, Integer>> softWeightsCounter = new LinkedList<>();
    public int uniqueSoftWeightsCount = 0;
    public String wcnfFileContent;

    public GeneticMaxSatSolver(SolverEnvironment solverEnvironment, Collection<Slot> slots,
                               Collection<Constraint> constraints, MaxSatFormatTranslator formatTranslator,
                               Lattice lattice) {
        super(solverEnvironment, slots, constraints, formatTranslator, lattice);
    }

    @Override
    public Map<Integer, AnnotationMirror> solve() {
        Map<Integer, AnnotationMirror> superSolutions = super.solve(); // to create initial set of constraints

        List<String> allLines = null;

        try {
            allLines = Files.readAllLines(Paths.get("cnfData/wcnfdata.wcnf")); // read from the wcnf file created by super
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        assert allLines != null;

        wcnfFileContent = String.join("\n", allLines.toArray(new String[0]));

        softWeightCounter();
        fit();

        return superSolutions;
    }

    public String changeSoftWeights(int[] newSoftWeights, List<Pair<Integer, Integer>> softWeightsCounter, String wcnfFileContent, boolean writeToFile){
        int oldTop = 0;
        int currentSoftWeightsSum = 0;
        HashMap<Integer, Integer> oldNewWeights = new HashMap<>();

        for (int i=0; i < softWeightsCounter.size(); i++) {
            Pair<Integer, Integer> softWeightPair = softWeightsCounter.get(i);
            oldNewWeights.put(softWeightPair.a, newSoftWeights[i]);
            softWeightsCounter.remove(softWeightPair);
            softWeightsCounter.add(new Pair<>(newSoftWeights[i], softWeightPair.b)); // replacing the old weights with new one (count remains the same)
            currentSoftWeightsSum += newSoftWeights[i]*softWeightPair.b;
        }

        String[] wcnfContentSplit = wcnfFileContent.split("\n");

        StringBuilder WCNFModInput = new StringBuilder();

        for (String line : wcnfContentSplit) {

            String[] trimAndSplit = line.trim().split(" ");

            if (trimAndSplit[0].equals("p")) {
                oldTop = Integer.parseInt(trimAndSplit[4]);
                trimAndSplit[4] = String.valueOf(currentSoftWeightsSum); // replacing the top value with current sum of soft weights
            } else if (oldTop != 0 && Integer.parseInt(trimAndSplit[0]) < oldTop) {
                trimAndSplit[0] = String.valueOf(oldNewWeights.get(Integer.parseInt(trimAndSplit[0])));
            }
            WCNFModInput.append(String.join(" ", trimAndSplit));
            WCNFModInput.append("\n");
        }

        WCNFModInput.setLength(WCNFModInput.length() - 1); // to prevent unwanted character at the end of file

        if (writeToFile){
            File WCNFData = new File(new File("").getAbsolutePath() + "/cnfData");
            FileUtils.writeFile(new File(WCNFData.getAbsolutePath() + "/" + "wcnfdata_modified.wcnf"), WCNFModInput.toString());
        }

        return WCNFModInput.toString();
    }

    public void softWeightCounter(){

        int top = 0;
        List<Integer> allSoftWeights = new ArrayList<>();
        String[] wcnfContentSplit = this.wcnfFileContent.split("\n");

        for (String line : wcnfContentSplit) {

            String[] trimAndSplit = line.trim().split(" ");

            if (trimAndSplit[0].equals("p")) {
                top = Integer.parseInt(trimAndSplit[4]);
            } else if (top != 0 && Integer.parseInt(trimAndSplit[0]) < top) {
                allSoftWeights.add(Integer.parseInt(trimAndSplit[0]));
            }
        }

        allSoftWeightsSum = allSoftWeights.stream().mapToLong(Integer::longValue).sum();
        allSoftWeightsCount = allSoftWeights.size();
        uniqueSoftWeightsCount = (int) allSoftWeights.stream().distinct().count();
        oldSoftWeights = allSoftWeights.stream().distinct().collect(Collectors.toList());

        for (int weight: oldSoftWeights) {
            softWeightsCounter.add(new Pair<>(weight, Collections.frequency(allSoftWeights, weight)));
        }

    }

    public void fit() {
        throw new BugInCF("Method needs to be overridden");
    }

}
