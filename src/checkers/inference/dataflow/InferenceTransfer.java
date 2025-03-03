package checkers.inference.dataflow;

import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.StringConcatenateAssignmentNode;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFTransfer;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.lang.model.type.TypeKind;

import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import checkers.inference.InferenceAnnotatedTypeFactory;
import checkers.inference.InferenceMain;
import checkers.inference.SlotManager;
import checkers.inference.VariableAnnotator;
import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.util.InferenceUtil;

/**
 *
 * InferenceTransfer extends CFTransfer for inference.
 *
 * InferenceTransfer overrides CFTransfer methods to create refinement variables
 * and to maintain the refinement variables for the analysis.
 *
 * See InferenceAnalysis for an overview of dataflow for inference.
 *
 * Note: RefinementVariables correctly appear in constraints because we always visit the AST for a class at least
 * twice. The first time we generate variables/refinementVariables and the second time we generate constraints.
 */
public class InferenceTransfer extends CFTransfer {

    private static final Logger logger = Logger.getLogger(InferenceTransfer.class.getName());

    // Keep a cache of tree's that we have created refinement variables so that we do
    // not create multiple. A tree can be evaluated multiple times due to loops.
    private final Map<Tree, RefinementVariableSlot> createdRefinementVariables = new HashMap<>();

    // Type variables will have two refinement variables (one for each bound).  This covers the
    // case where the correct, inferred RHS has no primary annotation
    private final Map<Tree, Pair<RefinementVariableSlot, RefinementVariableSlot>> createdTypeVarRefinementVariables = new HashMap<>();

    private final InferenceAnnotatedTypeFactory typeFactory;

    public InferenceTransfer(InferenceAnalysis analysis) {
        super(analysis);
        typeFactory = (InferenceAnnotatedTypeFactory) analysis.getTypeFactory();
    }

    private InferenceAnalysis getInferenceAnalysis() {
        return (InferenceAnalysis) analysis;
    }

    /**
     * A CombVariable from the results of ternary will be created already by the visiting stage.
     * For RefinementVariables, we don't want to try to get the result value nor LUB between sides.
     *
     */
    @Override
    public RegularTransferResult<CFValue, CFStore> visitTernaryExpression(TernaryExpressionNode n,
            TransferInput<CFValue, CFStore> p) {

        CFStore store = p.getRegularStore();
        return new RegularTransferResult<CFValue, CFStore>(finishValue(null, store), store);
    }

    /**
     * Create refinement variables on assignments.
     */
    @Override
    public TransferResult<CFValue, CFStore> visitAssignment(AssignmentNode assignmentNode, TransferInput<CFValue, CFStore> transferInput) {

        Node lhs = assignmentNode.getTarget();
        CFStore store = transferInput.getRegularStore();

        // Target tree is null for field access's
        Tree targetTree = assignmentNode.getTarget().getTree();

        AnnotatedTypeMirror atm;
        if (targetTree != null) {
            // Try to use the target tree if possible.
            // Getting the Type of a tree for a desugared compound assignment returns a comb variable
            // which is not what we want to make a refinement variable of.
            atm = typeFactory.getAnnotatedType(targetTree);
        } else {
            // Target trees can be null for refining library fields.
            atm = typeFactory.getAnnotatedType(assignmentNode.getTree());
        }

        if (targetTree != null && targetTree.getKind() == Tree.Kind.ARRAY_ACCESS) {
            // Don't create refinement variables on array assignments.

            CFValue result = analysis.createAbstractValue(atm);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);

        } else if (targetTree != null && InferenceUtil.isDetachedVariable(targetTree)) {
            // Don't create refinement variables for detached.

            CFValue result = analysis.createAbstractValue(atm);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);

        } else if (isDeclarationWithInitializer(assignmentNode)) {
            // Add declarations with initializers to the store.

            // This is needed to trigger a merge refinement variable creation when two stores are merged:
            // String a = null; // Add VarAnno to store
            // if ( ? ) {
            //   a = ""; // Add RefVar to store
            // }
            // // merge RefVar will be created only if VarAnno and RefVar are both in store
            // a.toString()

            // TODO: Remove after establishing there are no other cases.
            if (! (assignmentNode.getTarget() instanceof LocalVariableNode
                    || assignmentNode.getTarget() instanceof FieldAccessNode)) {
                assert false;
            }

            if (assignmentNode.getTarget() instanceof LocalVariableNode
                    && atm.getKind() != TypeKind.TYPEVAR) {
                // Get the rhs value and pass it to slot manager to generate the equality constraint
                // as "refinement variable == rhs value"
                Tree valueTree = assignmentNode.getExpression().getTree();
                AnnotatedTypeMirror valueType = typeFactory.getAnnotatedType(valueTree);
                return createRefinementVar(assignmentNode.getTarget(), assignmentNode.getTree(), store, atm, valueType);
            }

            return storeDeclaration(lhs, (VariableTree) assignmentNode.getTree(), store);

        } else if (lhs.getTree().getKind() == Tree.Kind.IDENTIFIER
                || lhs.getTree().getKind() == Tree.Kind.MEMBER_SELECT) {
            // Create Refinement Variable
            final TransferResult<CFValue, CFStore> result;
            if (atm.getKind() == TypeKind.TYPEVAR) {
                result = createTypeVarRefinementVars(assignmentNode.getTarget(), assignmentNode.getTree(),
                                                     store, (AnnotatedTypeVariable) atm);
            } else {
                // Get the rhs value and pass it to slot manager to generate the equality constraint
                // as "refinement variable == rhs value"
                Tree valueTree = assignmentNode.getExpression().getTree();
                AnnotatedTypeMirror valueType = typeFactory.getAnnotatedType(valueTree);

                // If the rhs is a type variable, the refinement value is the upper bound of it,
                // because this is the most precise type we can use
                if (valueType.getKind() == TypeKind.TYPEVAR) {
                    valueType = InferenceUtil.findUpperBoundType((AnnotatedTypeVariable) valueType);
                }
                result = createRefinementVar(assignmentNode.getTarget(), assignmentNode.getTree(), store, atm, valueType);
            }

            return result;

        } else {
            throw new BugInCF("Unexpected tree kind in visit assignment:" + assignmentNode.getTree());
        }
    }

    @Override
    public TransferResult<CFValue, CFStore> visitStringConcatenateAssignment(StringConcatenateAssignmentNode assignmentNode, TransferInput<CFValue, CFStore> transferInput) {
        // TODO: CompoundAssigment trees are not refined, see Issue 9
        CFStore store = transferInput.getRegularStore();

        Tree targetTree = assignmentNode.getLeftOperand().getTree();

        // Code for geting the ATM is copied from visitCompoundAssigment.
        AnnotatedTypeMirror atm;
        if (targetTree != null) {
            // Try to use the target tree if possible.
            // Getting the Type of a tree for a desugared compound assignment returns a comb variable
            // which is not what we want to make a refinement variable of.
            atm = typeFactory.getAnnotatedType(targetTree);
        } else {
            // Target trees can be null for refining library fields.
            atm = typeFactory.getAnnotatedType(assignmentNode.getTree());
        }

        CFValue result = analysis.createAbstractValue(atm);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);

    }

    /**
     * Create a refinement variable for atm type. This inserts the refinement variable
     * into the store as the value for the lhs cfg node.
     *
     * @param lhs The node being assigned
     * @param assignmentTree The tree for the assignment
     * @param store The store to update
     * @param atm The type of the variable being refined
     * @param valueAtm The type that the variable is refined to
     * @return
     */
    private TransferResult<CFValue, CFStore> createRefinementVar(Node lhs,
            Tree assignmentTree, CFStore store,
            AnnotatedTypeMirror atm, AnnotatedTypeMirror valueAtm) {

        SlotManager slotManager = getInferenceAnalysis().getSlotManager();
        Slot slotToRefine = slotManager.getSlot(atm);

        // Make sure the refinement slot is created on the declared type
        if (slotToRefine instanceof RefinementVariableSlot) {
            // Getting the declared type of a RefinementVariableSlot
            // getRefined() always returns the slot of the declared type value
            slotToRefine = ((RefinementVariableSlot)slotToRefine).getRefined();
        }

        Slot refineTo = slotManager.getSlot(valueAtm);

        logger.fine("Creating refinement variable for tree: " + assignmentTree);
        RefinementVariableSlot refVar;
        if (createdRefinementVariables.containsKey(assignmentTree)) {
            refVar = createdRefinementVariables.get(assignmentTree);
        } else {
            AnnotationLocation location = VariableAnnotator.treeToLocation(analysis.getTypeFactory(), assignmentTree);
            refVar = slotManager.createRefinementVariableSlot(location, slotToRefine, refineTo);

            // Fields from library methods can be refined, but the slotToRefine is a ConstantSlot
            // which does not have a refined slots field.
            if (slotToRefine instanceof VariableSlot) {
                ((VariableSlot) slotToRefine).getRefinedToSlots().add(refVar);
            }

            createdRefinementVariables.put(assignmentTree, refVar);
        }

        atm.replaceAnnotation(slotManager.getAnnotation(refVar));

        // add refinement variable value to output
        CFValue result = analysis.createAbstractValue(atm);

        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }


    /**
     * {@code
     * Let an existential variable be defined as follows:
     *
     * (potentialId | alternativeId)
     * Where the above line means:
     * if ( potentialId exists ) use potential id
     *                     else  use alternativeId
     *
     * Let's assume we have a definition of a type parameter and two variables:
     * <@0 T extends @1 Object>
     * T t2;
     * T t3;
     *
     * After being annotated these two variables would have types:
     * typeof(t2)   ==   (@2 | 0) T extends (@2 | 1) Object
     * typeof(t3)   ==   (@3 | 0) T extends (@3 | 1) Object
     *
     * Conceptually, these types have bounds that say:
     * if my variable declaration has a primary annotation use that
     * otherwise, use the annotations from the type parameter declaration
     *
     * Given an assignment:
     * t1 = t2;
     *
     * Let t1r be the refined type of t1:
     * typeof(t1r)   ==   @R0 T extends @R1 Object
     *
     * And:
     *  (@2 | @0) <: @R0
     *  (@2 | @1) <: @R1
     *  @R0 <: (@3 | @0)
     *  @R1 <: (@3 | @1)
     * }
     *
     * This method creates @R0 and @R1 above and adds them to type var and stores them
     * as the result of this assignment.  Note the second set of constraints @R0 <: (@3 | @0)
     * and @R1 <: (@3 | @1) will be created from the subtyping check between the lhs/rhs.
     */
    private TransferResult<CFValue, CFStore> createTypeVarRefinementVars(Node lhs, Tree assignmentTree, CFStore store,
                                                                         AnnotatedTypeVariable typeVar) {

        AnnotatedTypeMirror upperBoundType = InferenceUtil.findUpperBoundType(typeVar, InferenceMain.isHackMode());
        AnnotatedTypeMirror lowerBoundType = InferenceUtil.findLowerBoundType(typeVar, InferenceMain.isHackMode());

        SlotManager slotManager = getInferenceAnalysis().getSlotManager();

        final Slot upperBoundBaseSlot = slotManager.getSlot(upperBoundType);
        final Slot lowerBoundBaseSlot = slotManager.getSlot(lowerBoundType);

        if (upperBoundBaseSlot == null || lowerBoundBaseSlot == null) {
            if (!InferenceMain.isHackMode()) {
                throw new BugInCF("Unexpected empty bound types:\n" +
                        "upperBoundType=" + upperBoundType + "\n"
                      + "lowerBoundType=" + lowerBoundType);
            }
            CFValue result = analysis.createAbstractValue(typeVar);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
        }

        if ( !upperBoundBaseSlot.getClass().equals(ExistentialVariableSlot.class)) {

            if (!InferenceMain.isHackMode()) {
                throw new BugInCF("Expecting existential slot on type variable upper bound:\n"
                        + "typeVar=" + typeVar + "\n"
                        + "assignmentTree=" + assignmentTree + "\n"
                        + "lhs=" + lhs);
            }

            CFValue result = analysis.createAbstractValue(typeVar);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
        }

        if (!lowerBoundBaseSlot.getClass().equals(ExistentialVariableSlot.class)) {
            if (!InferenceMain.isHackMode()) {
                throw new BugInCF("Expecting existential slot on type variable lower bound:\n"
                        + "typeVar=" + typeVar + "\n"
                        + "assignmentTree=" + assignmentTree + "\n"
                        + "lhs=" + lhs);
            }
            CFValue result = analysis.createAbstractValue(typeVar);
            return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
        }

        final ExistentialVariableSlot upperBoundSlot = (ExistentialVariableSlot) upperBoundBaseSlot;
        final ExistentialVariableSlot lowerBoundSlot = (ExistentialVariableSlot) lowerBoundBaseSlot;

        logger.fine("Creating type variable refinement variable for tree: " + assignmentTree);
        final RefinementVariableSlot upperBoundRefVar;
        final RefinementVariableSlot lowerBoundRefVar;
        if (createdTypeVarRefinementVariables.containsKey(assignmentTree)) {
            Pair<RefinementVariableSlot, RefinementVariableSlot> ubToLb = createdTypeVarRefinementVariables.get(assignmentTree);
            upperBoundRefVar = ubToLb.first;
            lowerBoundRefVar = ubToLb.second;

        } else {
            AnnotationLocation location = VariableAnnotator.treeToLocation(analysis.getTypeFactory(), assignmentTree);
            // Create a refinement variable for each of the upper bound and the lower bound. But unlike the case
            // in the declared type refinement, here we pass null as the rhs value slot so no refinement constraint
            // is created. Refinement constraints for type variable will be created in InferenceVisitor
            // TODO: we will finally pass non-null value slot to create refinement constraint here, rather than in
            // InferenceVisitor, by resolving Issue: https://github.com/opprop/checker-framework-inference/issues/316
            upperBoundRefVar = slotManager.createRefinementVariableSlot(location, upperBoundSlot, null);
            lowerBoundRefVar = slotManager.createRefinementVariableSlot(location, lowerBoundSlot, null);

            upperBoundSlot.getRefinedToSlots().add(upperBoundRefVar);
            lowerBoundSlot.getRefinedToSlots().add(lowerBoundRefVar);

            createdTypeVarRefinementVariables.put(assignmentTree, Pair.of(upperBoundRefVar, lowerBoundRefVar));
        }

        upperBoundType.replaceAnnotation(slotManager.getAnnotation(upperBoundRefVar));
        lowerBoundType.replaceAnnotation(slotManager.getAnnotation(lowerBoundRefVar));

        // add refinement variable value to output
        CFValue result = analysis.createAbstractValue(typeVar);

        // This is a bit of a hack, but we want the LHS to now get the refinement annotation.
        // So change the value for LHS that is already in the store.
        // TODO: We should finally remove this hack as what we've done for the declared type refinement
        getInferenceAnalysis().getNodeValues().put(lhs, result);

        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    /**
     * Put the VarAnnot for the LHS into the store.
     * This is needed to trigger merges between stores.
     *
     * @param lhs
     * @param assignmentTree
     * @param store
     * @return
     */
    private TransferResult<CFValue, CFStore> storeDeclaration(Node lhs,
            VariableTree assignmentTree, CFStore store) {

        AnnotatedTypeMirror atm = typeFactory.getAnnotatedType(assignmentTree);
        CFValue result = analysis.createAbstractValue(atm);
        store.updateForAssignment(lhs, result);
        return new RegularTransferResult<CFValue, CFStore>(finishValue(result, store), store);
    }

    private boolean isDeclarationWithInitializer(AssignmentNode assignmentNode) {
        return (assignmentNode.getTree().getKind() == Tree.Kind.VARIABLE);
    }
}
