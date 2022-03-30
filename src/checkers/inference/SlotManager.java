package checkers.inference;

import checkers.inference.model.LubVariableSlot;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import org.checkerframework.framework.type.AnnotatedTypeMirror;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

import checkers.inference.model.AnnotationLocation;
import checkers.inference.model.ArithmeticVariableSlot;
import checkers.inference.model.CombVariableSlot;
import checkers.inference.model.ComparisonVariableSlot;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.ExistentialVariableSlot;
import checkers.inference.model.RefinementVariableSlot;
import checkers.inference.model.Slot;
import checkers.inference.model.VariableSlot;
import checkers.inference.model.SourceVariableSlot;

/**
 * SlotManager stores variables for later access, provides ids for creating variables and
 * provides helper method for converting back and forth between Slots and the AnnotationMirrors
 * that represent them.
 */
public interface SlotManager {

    /**
     * Return number of slots collected by this SlotManager
     *
     * @return number of slots collected by this SlotManager
     */
    int getNumberOfSlots();

    /**
     * Create new SourceVariableSlot and return the reference to it if no SourceVariableSlot
     * on this location exists. Otherwise return the reference to existing SourceVariableSlot
     * on this location. Each location uniquely identifies a SourceVariableSlot
     *
     * @param location
     *            used to locate this variable in code
     * @return SourceVariableSlot that corresponds to this location
     */
    SourceVariableSlot createSourceVariableSlot(AnnotationLocation location, TypeMirror type);

    /**
     * Create new VariableSlot and return the reference to it if no VariableSlot
     * on this location exists. Otherwise return the reference to existing VariableSlot
     * on this location. Each location uniquely identifies a polymorphic instance.
     * For now, there's no dedicated slot for polymorphic instance, but we may add one
     * in the future.
     *
     * @param location
     *            used to locate this variable in code
     * @return VariableSlot that corresponds to this location
     */
    VariableSlot createPolymorphicInstanceSlot(AnnotationLocation location, TypeMirror type);

    /**
     * Create new RefinementVariableSlot (as well as the refinement constraint if
     * possible) and return the reference to it if no RefinementVariableSlot on this
     * location exists. Otherwise return the reference to existing RefinementVariableSlot
     * on this location. Each location uniquely identifies a RefinementVariableSlot
     *
     * @param location
     *            used to locate this refinement variable in code
     * @param declarationSlot
     *            the VariableSlot for the lhs that gets refined
     * @param valueSlot
     *            the value that the given lhs VariableSlot is refined to. If it is
     *            non-null, an equality constraint "declarationSlot == valueSlot" is
     *            created. Otherwise such constraint is created in
     *            {@link InferenceVisitor#maybeAddRefinementVariableConstraints}
     *            Currently we pass in non-null valueSlot only when lhs is a declared type.
     *            TODO: handle wildcards/type variables in the same way as declared
     *            type, so that this parameter is always non-null
     *
     * @return RefinementVariableSlot that corresponds to this refinement
     */
    RefinementVariableSlot createRefinementVariableSlot(AnnotationLocation location, Slot declarationSlot, Slot valueSlot);

    /**
     * Create new ConstrantSlot and returns the reference to it if no
     * ConstantSlot representing this AnnotationMirror exists. Otherwise, return
     * the reference to existing ConstantSlot. An AnnotationMirror uniquely
     * identifies a ConstantSlot
     *
     * @param value
     *            The actual AnnotationMirror that this ConstantSlot represents.
     *            This AnnotationMirror should be valid within the type system
     *            for which we are inferring values.
     * @return the ConstantSlot that represents this AnnotationMirror
     */
    ConstantSlot createConstantSlot(AnnotationMirror value);

    /**
     * Create new CombVariableSlot using receiver slot and declared slot, and
     * return reference to it if no CombVariableSlot representing result of
     * adapting declared slot to receiver slot exists. Otherwise, returns the
     * existing CombVariableSlot. Receiver slot and declared slot can uniquely
     * identify a CombVariableSlot
     *
     * @param receiver
     *            receiver slot
     * @param declared
     *            declared slot
     * @return CombVariableSlot that represents the viewpoint adaptation result
     *         of adapting declared slot to receiver slot
     */
    CombVariableSlot createCombVariableSlot(Slot receiver, Slot declared);

    // TODO(Zhiping): will rename LubVariableSlot to MergeVariableSlot
    /**
     * Creates new LubVariableSlot using left slot and right slot, and returns
     * reference to it if no LubVariableSlot representing least upper bound of
     * left slot and right slot exists. Otherwise, returns the existing LubVariableSlot.
     * Left slot and right slot can uniquely identify a slot that stores their
     * least upper bound.
     *
     * @param left left side of merge operation
     * @param right right side of merge operation
     * @return LubVariableSlot that represents the least upper bound result
     *         of left slot and right slot
     */
    LubVariableSlot createLubMergeVariableSlot(Slot left, Slot right);

    /**
     * Creates new LubVariableSlot using left slot and right slot, and returns
     * reference to it if no LubVariableSlot representing greatest lower bound of
     * left slot and right slot exists. Otherwise, returns the existing LubVariableSlot.
     * Left slot and right slot can uniquely identify a slot that stores their
     * greatest lower bound.
     *
     * @param left left side of merge operation
     * @param right right side of merge operation
     * @return LubVariableSlot that represents the greatest lower bound result
     *         of left slot and right slot
     */
    LubVariableSlot createGlbMergeVariableSlot(Slot left, Slot right);

    /**
     * Create new ExistentialVariableSlot using potential slot and alternative
     * slot, and return reference to it if no ExistentialVariableSlot that wraps
     * this potentialSlot and alternativeSlot exists. Otherwise, returns the
     * existing ExistentialVariableSlot. Potential slot and alternative slot can
     * uniquely identify an ExistentialVariableSlot
     *
     * @param potentialSlot
     *            a slot whose annotation may or may not exist in source
     * @param alternativeSlot
     *            the slot which would take part in a constraint if
     *            {@code potentialSlot} does not exist
     * @return the ExistentialVariableSlot that wraps this potentialSlot and
     *         alternativeSlot
     */
    ExistentialVariableSlot createExistentialVariableSlot(Slot potentialSlot, Slot alternativeSlot);

    /**
     * Create new ArithmeticVariableSlot at the given location and return a reference to it if no
     * ArithmeticVariableSlots exists for the location. Otherwise, returns the existing
     * ArithmeticVariableSlot.
     *
     * @param location an AnnotationLocation used to locate this variable in code
     * @param lhsAtm atm of the left operand
     * @param rhsAtm atm of the right operand
     * @return the ArithmeticVariableSlot for the given location
     */
    ArithmeticVariableSlot createArithmeticVariableSlot(
            AnnotationLocation location, AnnotatedTypeMirror lhsAtm, AnnotatedTypeMirror rhsAtm);

    /**
     * Create new ComparisonVariableSlot at the given location and return a reference to it if no
     * ComparisonVariableSlot exists for the location. Otherwise, returns the existing
     * ComparisonVariableSlot.
     *
     * @param location an AnnotationLocation used to locate this variable in code
     * @param thenBranch true if is for the then store, false if is for the else store
     * @return the ComparisonVariableSlot for the given location
     */
    ComparisonVariableSlot createComparisonVariableSlot(AnnotationLocation location, Slot refined, boolean thenBranch);

    /**
     * Create a VarAnnot equivalent to the given realQualifier.
     *
     * @return a VarAnnot equivalent to the given realQualifier.
     *
     */
     AnnotationMirror createEquivalentVarAnno(final AnnotationMirror realQualifier);

    /** Return the slot identified by the given id or null if no such slot has been added */
    Slot getSlot( int id );

    /**
     * Given a slot return an annotation that represents the slot when added to an AnnotatedTypeMirror.
     * If A is the annotation returned by getAnnotation( S ) where is a slot.  Then getSlot( A ) will
     * return S (or an equivalent Slot in case of Constants ).
     * For {@code ConstantSlot}, this method should return the {@code VariableAnnotation} that represents
     * the underlying constant, and one should use {@link ConstantSlot#getValue()} to get the real annotation.
     * @param slot A slot to convert to an annotation
     * @return An annotation representing the slot
     */
    AnnotationMirror getAnnotation( Slot slot );

    /**
     * Return the Slot (or an equivalent Slot) that is represented by the given AnnotationMirror.  A RuntimeException
     * is thrown if the annotation isn't a VarAnnot, RefVarAnnot, CombVarAnnot or a member of one of the
     * REAL_QUALIFIER set provided by InferenceChecker.
     * @param am The annotationMirror representing a Slot
     * @return The Slot (on an equivalent Slot) represented by annotationMirror
     */
    Slot getSlot( AnnotationMirror am );

    /**
     * Return the Slot in the primary annotation location of annotated type mirror.  If
     * there is no Slot this method throws an exception
     * @param atm An annotated type mirror with a VarAnnot in its primary annotations list
     */
    Slot getSlot(AnnotatedTypeMirror atm);

    /**
     * Return all slots collected by this SlotManager
     * @return a list of slots
     */
    List<Slot> getSlots();

    /**
     * Return all VariableSlots collected by this SlotManager
     * @return a lit of VariableSlots
     */
    List<VariableSlot> getVariableSlots();

    List<ConstantSlot> getConstantSlots();

    /**
     * This method informs slot manager of the current top level class tree that's being type processed.
     * Slot manager can then preprocess this information by clearing caches, resolving slot default
     * types, etc.
     *
     * Note that trees that are not within this tree may be missing some information
     * (in the JCTree implementation), and this is because they are either not fully
     * initialized or being garbage-recycled.
     */
    void setTopLevelClass(ClassTree classTree);
}
