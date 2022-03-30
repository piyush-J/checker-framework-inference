package checkers.inference.solver.backend.encoder;

import checkers.inference.model.ComparisonConstraint.ComparisonOperationKind;
import checkers.inference.model.ComparisonVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.VariableSlot;

/**
 * A marker interface that all constraint encoders that support encoding {@link checkers.inference.model.ComparisonConstraint}
 * should implement. Otherwise, the encoder will be considered not supporting encoding comparable
 * constraint and rejected by the {@link AbstractConstraintEncoderFactory#createComparisonConstraintEncoder()}
 *
 * @see checkers.inference.model.ComparisonConstraint
 * @see AbstractConstraintEncoderFactory#createComparisonConstraintEncoder()
 */
public interface ComparisonConstraintEncoder<ConstraintEncodingT> {
    ConstraintEncodingT encodeVariable_Variable(ComparisonOperationKind operation,
            VariableSlot left, VariableSlot right, ComparisonVariableSlot result);

    ConstraintEncodingT encodeVariable_Constant(ComparisonOperationKind operation,
            VariableSlot left, ConstantSlot right, ComparisonVariableSlot result);

    ConstraintEncodingT encodeConstant_Variable(ComparisonOperationKind operation,
            ConstantSlot left, VariableSlot right, ComparisonVariableSlot result);

    ConstraintEncodingT encodeConstant_Constant(ComparisonOperationKind operation,
            ConstantSlot left, ConstantSlot right, ComparisonVariableSlot result);
}
