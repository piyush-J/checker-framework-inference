package checkers.inference.model;

import java.util.Arrays;
import java.util.Objects;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.BugInCF;

import com.sun.source.tree.Tree.Kind;

/**
 * Represents constraints created for:
 *  (1) numerical comparison ("==", "!=", ">", ">=", "<", "<=")
 *  (2) comparison between references/null ("==", "!=")
 *
 * In contrast, a {@link ComparableConstraint} describes two types are compatible,
 * i.e. one type can be cast to the other.
 */
public class ComparisonConstraint extends Constraint {

    private final ComparisonOperationKind operation;
    private final Slot left;
    private final Slot right;
    private final ComparisonVariableSlot result;
    
    public enum ComparisonOperationKind {
        EQUAL_TO("=="),
        NOT_EQUAL_TO("!="),
        GREATER_THAN(">"),
        GREATER_THAN_EQUAL(">="),
        LESS_THAN("<"),
        LESS_THAN_EQUAL("<=");

        // stores the symbol of the operation
        private final String opSymbol;

        private ComparisonOperationKind(String opSymbol) {
            this.opSymbol = opSymbol;
        }

        public static ComparisonOperationKind fromTreeKind(Kind kind) {
            switch (kind) {
                case EQUAL_TO:
                	return EQUAL_TO;
                case NOT_EQUAL_TO:
                	return NOT_EQUAL_TO;
                case GREATER_THAN:
                	return GREATER_THAN;
                case GREATER_THAN_EQUAL:
                	return GREATER_THAN_EQUAL;
                case LESS_THAN:
                	return LESS_THAN;
                case LESS_THAN_EQUAL:
                	return LESS_THAN_EQUAL;
                default:
                	throw new BugInCF("There are no defined ComparableOperationKind "
                            + "for the given com.sun.source.tree.Tree.Kind: " + kind);
            }
        }

        public String getSymbol() {
            return opSymbol;
        }
    }

    private ComparisonConstraint(ComparisonOperationKind operation, Slot left, Slot right,
            ComparisonVariableSlot result, AnnotationLocation location) {
        super(Arrays.asList(left, right, result), location);
        this.left = left;
        this.right = right;
        this.operation = operation;
        this.result = result;
    }

    protected static Constraint create(ComparisonOperationKind operation, Slot left, Slot right,
    		ComparisonVariableSlot result, AnnotationLocation location, QualifierHierarchy realQualHierarchy) {
        if (operation == null || left == null || right == null) {
            throw new BugInCF("Create comparable constraint with null argument. "
                    + "Operation: " + operation + " Subtype: "
                    + left + " Supertype: " + right);
        }
        if (location == null || location.getKind() == AnnotationLocation.Kind.MISSING) {
            throw new BugInCF(
                    "Cannot create an ComparisonConstraint with a missing annotation location.");
        }

        return new ComparisonConstraint(operation, left, right, result, location);
    }

    @Override
    public <S, T> T serialize(Serializer<S, T> serializer) {
        return serializer.serialize(this);
    }

    public ComparisonOperationKind getOperation() {
        return operation;
    }

    public Slot getLeft() {
        return left;
    }

    public Slot getRight() {
        return right;
    }
    
    public ComparisonVariableSlot getResult() {
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, operation);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ComparisonConstraint other = (ComparisonConstraint) obj;
        return left.equals(other.left) && right.equals(other.right)
        		&& operation.equals(other.operation);
    }
}
