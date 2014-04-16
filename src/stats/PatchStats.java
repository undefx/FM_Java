package stats;

public class PatchStats {

   public PatchStats(int naive, int exposed, int infectious, int recovered, int incidence) {
      this.naive = naive;
      this.exposed = exposed;
      this.infectious = infectious;
      this.recovered = recovered;
      this.incidence = incidence;
   }
   public int naive;
   public int exposed;
   public int infectious;
   public int recovered;
   public int incidence;
}
