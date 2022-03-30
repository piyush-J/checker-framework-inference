package checkers.inference.solver.backend.maxsat.encoder;

import checkers.inference.solver.backend.encoder.AbstractConstraintEncoderFactory;
import checkers.inference.solver.backend.encoder.ArithmeticConstraintEncoder;
import checkers.inference.solver.backend.encoder.ComparisonConstraintEncoder;
import checkers.inference.solver.backend.encoder.combine.CombineConstraintEncoder;
import checkers.inference.solver.backend.encoder.existential.ExistentialConstraintEncoder;
import checkers.inference.solver.backend.encoder.implication.ImplicationConstraintEncoder;
import checkers.inference.solver.backend.maxsat.MaxSatFormatTranslator;
import checkers.inference.solver.frontend.Lattice;
import org.sat4j.core.VecInt;

import javax.lang.model.element.AnnotationMirror;
import java.util.Map;

/**
 * MaxSAT implementation of {@link checkers.inference.solver.backend.encoder.ConstraintEncoderFactory}.
 *
 * @see checkers.inference.solver.backend.encoder.ConstraintEncoderFactory
 */
public class MaxSATConstraintEncoderFactory extends AbstractConstraintEncoderFactory<VecInt[], MaxSatFormatTranslator> {

    private final Map<AnnotationMirror, Integer> typeToInt;

    public MaxSATConstraintEncoderFactory(Lattice lattice, Map<AnnotationMirror, Integer> typeToInt,
                                          MaxSatFormatTranslator formatTranslator) {
        super(lattice, formatTranslator);
        this.typeToInt = typeToInt;
    }

    @Override
    public MaxSATSubtypeConstraintEncoder createSubtypeConstraintEncoder() {
        return new MaxSATSubtypeConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATEqualityConstraintEncoder createEqualityConstraintEncoder() {
        return new MaxSATEqualityConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATInequalityConstraintEncoder createInequalityConstraintEncoder() {
        return new MaxSATInequalityConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATComparableConstraintEncoder createComparableConstraintEncoder() {
        return new MaxSATComparableConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public ComparisonConstraintEncoder<VecInt[]> createComparisonConstraintEncoder() {
        return null;
    }

    @Override
    public MaxSATPreferenceConstraintEncoder createPreferenceConstraintEncoder() {
        return new MaxSATPreferenceConstraintEncoder(lattice, typeToInt);
    }

    @Override
    public MaxSATImplicationConstraintEncoder createImplicationConstraintEncoder() {
        return new MaxSATImplicationConstraintEncoder(lattice, typeToInt, formatTranslator);
    }

    @Override
    public CombineConstraintEncoder<VecInt[]> createCombineConstraintEncoder() {
        return null;
    }

    @Override
    public ExistentialConstraintEncoder<VecInt[]> createExistentialConstraintEncoder() {
        return null;
    }

    @Override
    public ArithmeticConstraintEncoder<VecInt[]> createArithmeticConstraintEncoder() {
        return null;
    }
}
