package checkers.inference.solver.backend.geneticmaxsat;

import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.backend.maxsat.MaxSatSolver;
import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.FileUtils;
import checkers.inference.solver.util.SolverEnvironment;
import io.jenetics.IntegerGene;
import io.jenetics.MeanAlterer;
import io.jenetics.Mutator;
import io.jenetics.Optimize;
import io.jenetics.RouletteWheelSelector;
import io.jenetics.TournamentSelector;
import io.jenetics.engine.Codecs;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.util.IntRange;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.maxsat.reader.WDimacsReader;
import org.sat4j.pb.IPBSolver;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;

import javax.lang.model.element.AnnotationMirror;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.jenetics.engine.EvolutionResult.toBestEvolutionResult;
import static io.jenetics.engine.Limits.bySteadyFitness;

public class GeneticMaxSatSolver extends MaxSatSolver {

    private String wcnfFileContent;

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

        fit();

        return superSolutions;
    }

    private String changeSoftWeights(int newSoftWeights, String wcnfContent, boolean writeToFile){
        int top = 0;
        String[] wcnfContentSplit = wcnfContent.split("\n");

        StringBuilder WCNFModInput = new StringBuilder();

        for (String line : wcnfContentSplit) {

            String[] trimAndSplit = line.trim().split(" ");

            if (trimAndSplit[0].equals("p")) {
                top = Integer.parseInt(trimAndSplit[4]);
            } else if (top != 0 && Integer.parseInt(trimAndSplit[0]) < top) {
                trimAndSplit[0] = String.valueOf(newSoftWeights);
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

    private int fitness(final int[] x) {
        IPBSolver solver = org.sat4j.maxsat.SolverFactory.newDefault();;
        WDimacsReader reader = new WDimacsReader(new WeightedMaxSatDecorator(solver));
        Map<Integer, AnnotationMirror> solutions;
        int fitness_count = 0;

        String WCNFModInput = changeSoftWeights(x[0], wcnfFileContent, false);

        InputStream stream = new ByteArrayInputStream(WCNFModInput.getBytes(StandardCharsets.UTF_8));

        try {
            solver = (IPBSolver) reader.parseInstance(stream);
        } catch (ContradictionException | IOException | ParseFormatException e) {
            System.out.println(e);
        }

        try {
            if (solver.isSatisfiable()){
                solutions = decode(solver.model());

                List<AnnotationMirror> sol = new ArrayList<>(solutions.values());

                for (AnnotationMirror sol_0 : sol){
                    if (sol_0.toString().equals("@universe.qual.Rep"))
                    {
                        fitness_count += 1;
                    }
                }
            }
            else {
                System.out.println("UNSAT at " + x[0]);
            }
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        System.out.println("Rep count: " + fitness_count);

        return fitness_count;
    }

    private void fit() {
        final Engine<IntegerGene, Integer> engine = Engine
                .builder(
                        this::fitness,
                        Codecs.ofVector(IntRange.of(0, 700), 1))
                .populationSize(500)
                .offspringFraction(0.7)
                .survivorsSelector(new RouletteWheelSelector<>())
                .offspringSelector(new TournamentSelector<>())
                .optimize(Optimize.MAXIMUM)
                .alterers(
                        new Mutator<>(0.03),
                        new MeanAlterer<>(0.6))
                .build();

        final EvolutionStatistics<Integer, ?>
                statistics = EvolutionStatistics.ofNumber();

        final EvolutionResult<IntegerGene, Integer> best_res = engine.stream()
                .limit(bySteadyFitness(7))
                .limit(100)
                .peek(statistics)
                .collect(toBestEvolutionResult());

        System.out.println(statistics);
        System.out.println(best_res.genotypes().length());
        System.out.println(best_res.bestPhenotype());
        System.out.println(best_res.worstPhenotype());
    }

}
