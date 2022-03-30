package checkers.inference.model;

import java.util.Arrays;
import java.util.Objects;

import org.checkerframework.javacutil.BugInCF;
import com.sun.source.tree.Tree;

/**
 * Represents a constraint between the result of an arithmetic operation and its two operands.
 * Subclasses of this constraint class denote each specific kind of arithmetic operation, such as
 * addition, subtraction, multiplication, division, and modulus.
 */
public class ArithmeticConstraint extends Constraint {

    public enum ArithmeticOperationKind {
        PLUS("+"),
        MINUS("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        REMAINDER("%"),
        LEFT_SHIFT("<<"),
        RIGHT_SHIFT(">>"),
        UNSIGNED_RIGHT_SHIFT(">>>"),
        AND("&"),
        OR("|"),
        XOR("^");

        // stores the symbol of the operation
        private final String opSymbol;

        private ArithmeticOperationKind(String opSymbol) {
            this.opSymbol = opSymbol;
        }

        /**
         * Get the {@link ArithmeticOperationKind} corresponding to a {@link Tree.Kind}. For a compound
         * assignment tree, get the {@link ArithmeticOperationKind} for the arithmetic operation of the RHS.
         * @param kind a {@link Tree.Kind} for an arithmetic operation
         * @return the corresponding {@link ArithmeticOperationKind} for the given arithmetic operation
         */
        public static ArithmeticOperationKind fromTreeKind(Tree.Kind kind) {
            switch (kind) {
                case PLUS:
                case PLUS_ASSIGNMENT:
                    return PLUS;
                case MINUS:
                case MINUS_ASSIGNMENT:
                    return MINUS;
                case MULTIPLY:
                case MULTIPLY_ASSIGNMENT:
                    return MULTIPLY;
                case DIVIDE:
                case DIVIDE_ASSIGNMENT:
                    return DIVIDE;
                case REMAINDER:
                case REMAINDER_ASSIGNMENT:
                    return REMAINDER;
                case LEFT_SHIFT:
                case LEFT_SHIFT_ASSIGNMENT:
                    return LEFT_SHIFT;
                case RIGHT_SHIFT:
                case RIGHT_SHIFT_ASSIGNMENT:
                    return RIGHT_SHIFT;
                case UNSIGNED_RIGHT_SHIFT:
                case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT:
                    return UNSIGNED_RIGHT_SHIFT;
                case AND:
                case AND_ASSIGNMENT:
                    return AND;
                case OR:
                case OR_ASSIGNMENT:
                    return OR;
                case XOR:
                case XOR_ASSIGNMENT:
                    return XOR;
                default:
                    throw new BugInCF("There are no defined ArithmeticOperationKinds "
                            + "for the given com.sun.source.tree.Tree.Kind: " + kind);
            }
        }

        public String getSymbol() {
            return opSymbol;
        }
    }

    private final ArithmeticOperationKind operation;
    private final Slot leftOperand; // either a {@link ConstantSlot} or a {@link VariableSlot}
    private final Slot rightOperand; // either a {@link ConstantSlot} or a {@link VariableSlot}
    private final ArithmeticVariableSlot result;

    private ArithmeticConstraint(ArithmeticOperationKind operation, Slot leftOperand,
            Slot rightOperand, ArithmeticVariableSlot result, AnnotationLocation location) {
        super(Arrays.asList(leftOperand, rightOperand, result), location);
        this.operation = operation;
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.result = result;
    }

    protected static ArithmeticConstraint create(ArithmeticOperationKind operation,
            Slot leftOperand, Slot rightOperand, ArithmeticVariableSlot result,
            AnnotationLocation location) {
        if (operation == null || leftOperand == null || rightOperand == null || result == null) {
            throw new BugInCF("Create arithmetic constraint with null argument. "
                    + "Operation: " + operation + " LeftOperand: " + leftOperand + " RightOperand: "
                    + rightOperand + " Result: " + result);
        }
        if (location == null || location.getKind() == AnnotationLocation.Kind.MISSING) {
            throw new BugInCF(
                    "Cannot create an ArithmeticConstraint with a missing annotation location.");
        }

        return new ArithmeticConstraint(operation, leftOperand, rightOperand, result, location);
    }

    public ArithmeticOperationKind getOperation() {
        return operation;
    }

    public Slot getLeftOperand() {
        return leftOperand;
    }

    public Slot getRightOperand() {
        return rightOperand;
    }

    public ArithmeticVariableSlot getResult() {
        return result;
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    @Override
    public int hashCode() {
        // We do not hash on annotation location as the result slot is unique for each annotation
        // location
        return Objects.hash(operation, leftOperand, rightOperand, result);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ArithmeticConstraint other = (ArithmeticConstraint) obj;
        return operation.equals(other.operation) && leftOperand.equals(other.leftOperand)
                && rightOperand.equals(other.rightOperand) && result.equals(other.result);
    }
}
