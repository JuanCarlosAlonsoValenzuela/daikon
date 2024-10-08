package daikon.derive.binary;

import daikon.Quantify;
import daikon.VarInfo;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.dataflow.qual.SideEffectFree;

/** Derivations of the form A[0..i] or A[i..<em>end</em>], derived from A and i. */
public abstract class SequenceSubsequence extends BinaryDerivation {
  static final long serialVersionUID = 20020801L;

  // Variables starting with dkconfig_ should only be set via the
  // daikon.config.Configuration interface.

  // base1 is the sequence
  // base2 is the scalar
  public VarInfo seqvar(@GuardSatisfied SequenceSubsequence this) {
    return base1;
  }

  public VarInfo sclvar(@GuardSatisfied SequenceSubsequence this) {
    return base2;
  }

  // Indicates whether the subscript is an index of valid data or a limit
  // (one element beyond the data of interest).  The first (or last)
  // element of the derived variable is at index seqvar()+index_shift.
  public final int index_shift;

  // True for deriving from the start of the sequence to the scalar: B[0..I]
  // False for deriving from the scalar to the end of the sequence: B[I..]
  public final boolean from_start;

  /**
   * @param from_start true means the range goes 0..n; false means the range goes n..end. (n might
   *     be fudged through off_by_one.)
   * @param off_by_one true means we should exclude the scalar from the range; false means we should
   *     include it
   */
  protected SequenceSubsequence(VarInfo vi1, VarInfo vi2, boolean from_start, boolean off_by_one) {
    super(vi1, vi2);
    this.from_start = from_start;
    if (off_by_one) {
      index_shift = from_start ? -1 : +1;
    } else {
      index_shift = 0;
    }
  }

  @Override
  protected VarInfo makeVarInfo() {
    VarInfo seqvar = seqvar();
    VarInfo sclvar = sclvar();

    VarInfo vi;
    if (from_start) {
      vi = VarInfo.make_subsequence(seqvar, null, 0, sclvar, index_shift);
    } else {
      vi = VarInfo.make_subsequence(seqvar, sclvar, index_shift, null, 0);
    }

    return vi;
  }

  @Override
  public Quantify.Term get_lower_bound() {
    if (from_start) {
      return new Quantify.Constant(0);
    } else {
      return new Quantify.VarPlusOffset(sclvar(), index_shift);
    }
  }

  @Override
  public Quantify.Term get_upper_bound() {
    if (from_start) {
      return new Quantify.VarPlusOffset(sclvar(), index_shift);
    } else {
      return new Quantify.Length(seqvar(), -1);
    }
  }

  @Override
  public VarInfo get_array_var() {
    return seqvar();
  }

  @SideEffectFree
  @Override
  public String csharp_name(String index) {
    // String lower = get_lower_bound().csharp_name();
    // String upper = get_upper_bound().csharp_name();
    // We do not need to check if seqvar().isPrestate() because it is redundant.
    // return seqvar().csharp_name() + ".Slice(" + lower + ", " + upper + ")";
    return "\"SequenceSubsequence.java.jpp unimplemented\" != null"; // "interned"
  }

  @SideEffectFree
  @Override
  public String esc_name(String index) {
    return String.format(
        "%s[%s..%s]",
        seqvar().esc_name(), get_lower_bound().esc_name(), get_upper_bound().esc_name());
  }

  @Override
  @SuppressWarnings("nullness")
  public String jml_name(String index) {

    // The slice routine needs the actual length as opposed to the
    // highest legal index.
    Quantify.Term upper = get_upper_bound();
    if (upper instanceof Quantify.Length) {
      ((Quantify.Length) upper).set_offset(0);
    }

    if (seqvar().isPrestate()) {
      return String.format(
          "\\old(daikon.Quant.slice(%s, %s, %s))",
          seqvar().enclosing_var.postState.jml_name(),
          get_lower_bound().jml_name(true),
          upper.jml_name(true));
    } else {
      return String.format(
          "daikon.Quant.slice(%s, %s, %s)",
          seqvar().enclosing_var.jml_name(), get_lower_bound().jml_name(), upper.jml_name());
    }
  }

  /** Adds one to the default complexity if index_shift is not 0. */
  @Override
  public int complexity() {
    return super.complexity() + ((index_shift != 0) ? 1 : 0);
  }
}
