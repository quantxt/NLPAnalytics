package com.quantxt.nlp.comp.ter;

public class TERcost {
  /* For all of these functions, the score should be between 0 and 1
   * (inclusive).  If it isn't, then it will break TERcalc! */

  /* The cost of matching ref word for hyp word. (They are equal) */
  public double match_cost(Comparable hyp, Comparable ref) { 
      return _match_cost; 
  }

  /* The cost of substituting ref word for hyp word. (They are not equal) */
  public double substitute_cost(Comparable hyp, Comparable ref) {
	return _substitute_cost;
  }

  /* The cost of inserting the hyp word */
  public double insert_cost(Comparable hyp) {
	return _insert_cost;
  }

  /* The cost of deleting the ref word */
  public double delete_cost(Comparable ref) {
	return _delete_cost;
  }

  /* The cost of making a shift */
  public double shift_cost(TERshift shift) {
      return _shift_cost;
  }

    public double _shift_cost = 1.0;
    public double _insert_cost = 1.0;
    public double _delete_cost = 1.0;
    public double _substitute_cost = 1.0;
    public double _match_cost = 0.0;

}
