package stats;

public class StrainStats {

   public static final int NULL_PARENT = -1;

   public StrainStats() {
      rna = "";
      protein = "";
      id = nextStrainID++;
      parentID = NULL_PARENT;
      infected = 0;
      incidence = 0;
      mutations = 0;
      age = 0;
   }

   public StrainStats(String rna, String protein, int id, int parentID, int infected, int incidence, int mutations, int age) {
      this.rna = rna;
      this.protein = protein;
      this.id = id;
      this.parentID = parentID;
      this.infected = infected;
      this.incidence = incidence;
      this.mutations = mutations;
      this.age = age;
   }
   public String rna;
   public String protein;
   public int id;
   public int parentID;
   public int infected;
   public int incidence;
   public int mutations;
   public int age;
   public static int nextStrainID;
}
