package stats;

import fergusonmodel.Main;
import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/*
 * Instead of having a bunch of parameters duplicated between StatsWriter and
 * StatsReader, all parameters are stored in this class. Simulation information
 * (input) is stored in this class.
 */
public class SimulationInfo {

   //The enumeration of all algorithm choices
   public static enum Algorithm {

      //Select the number of hosts to infect from a binomial, with replacement
      //This was David's first implementation
      Infection_Approximate1(1),
      //Select the number of hosts to infect from the expected value, with replacement
      //This is a copy of Anuroop's first implementation
      Infection_Approximate2(2),
      //Select the number of hosts to infect from the expected value, without replacement
      //This is a copy of Anuroop's latest implementation (and this is how FRED works)
      Infection_Approximate3(3),
      //Select the hosts to infect individually (no replacement because it's not sampling)
      //This is David's latest implementation
      Infection_Exact(4),
      //Select at most one nucleic acid mutation per replication
      //This shouldn't be used because it's a loss of accuracy for very little performance gain
      Mutation_Approximate(5),
      //Select the number of nucleic acids to mutate from a binomial, without replacement
      //This should pretty much always be used because it's more correct,
      //and performance is about the same
      Mutation_Exact(6),
      //Never allow the virus to mutate
      Mutation_None(7),
      //Only track incidence for each patch
      //Very fast, simply incrementing a counter whenever there is an infection
      PatchStats_Incidence(8),
      //Track all stats (naive, exposed, infectious, recovered, incidence) for each patch
      //Can be slow, requires looping over the entire population every day
      PatchStats_All(9),
      //Patches are split into nothern and southern hemispheres (1 row each)
      //Every patch has some nonzero contact rate with every other patch
      Connectivity_Flat(10),
      //Patches are split into nothern and southern hemispheres (2 rows each)
      //Patches have a nonzero contact rate only with immediate neighbors (3 or 4)
      Connectivity_Cylindrical(11),
      //Patches are split into 3 demes: north, tropics, south (4:5:1 ratio)
      //Patches have the Connectivity_Flat contact rate
      Connectivity_Tropics(12);

      Algorithm(int id) {
         //Could just use the ordinal value, but file compatibality would be broken if items were rearranged
         this.id = id;
      }

      public static Algorithm Decode(int id) {
         for (Algorithm algorithm : values()) {
            if (algorithm.id == id) {
               return algorithm;
            }
         }
         throw new RuntimeException("Invalid algorithm id: " + id);
      }
      public int id;
   }

   public SimulationInfo() {
   }

   public SimulationInfo(double theta0, double theta1, double nt, double omega, double tau, long randomSeed, double seasonalityMultiplier, double neighborhoodRadius, double mutationProbability, int numEpitopes, int codonsPerEpitope, int numPatches, int hostsPerPatch, int hostLifespan, double R0_local, double R0_patch, double R0_global, int numDays, int minCarriers, Algorithm infectionAlgorithm, Algorithm mutationAlgorithm, Algorithm patchStatsAlgorithm, Algorithm connectivityAlgorithm, boolean saveState) {
      this.theta0 = theta0;
      this.theta1 = theta1;
      this.nt = nt;
      this.omega = omega;
      this.tau = tau;
      this.randomSeed = randomSeed;
      this.seasonalityMultiplier = seasonalityMultiplier;
      this.neighborhoodRadius = neighborhoodRadius;
      this.mutationProbability = mutationProbability;
      this.numEpitopes = numEpitopes;
      this.codonsPerEpitope = codonsPerEpitope;
      this.numPatches = numPatches;
      this.hostsPerPatch = hostsPerPatch;
      this.hostLifespan = hostLifespan;
      this.R0_local = R0_local;
      this.R0_patch = R0_patch;
      this.R0_global = R0_global;
      this.numDays = numDays;
      this.minCarriers = minCarriers;
      this.infectionAlgorithm = infectionAlgorithm;
      this.mutationAlgorithm = mutationAlgorithm;
      this.patchStatsAlgorithm = patchStatsAlgorithm;
      this.connectivityAlgorithm = connectivityAlgorithm;
      this.saveState = saveState;
   }

   public void Write(DataOutput output) throws IOException {
      output.writeDouble(theta0);
      output.writeDouble(theta1);
      output.writeDouble(nt);
      output.writeDouble(omega);
      output.writeDouble(tau);
      output.writeDouble(seasonalityMultiplier);
      output.writeDouble(neighborhoodRadius);
      output.writeDouble(mutationProbability);
      output.writeInt(numEpitopes);
      output.writeInt(codonsPerEpitope);
      output.writeInt(numPatches);
      output.writeInt(hostsPerPatch);
      output.writeInt(hostLifespan);
      output.writeDouble(R0_local);
      output.writeDouble(R0_patch);
      output.writeDouble(R0_global);
      output.writeLong(randomSeed);
      output.writeInt(numDays);
      output.writeInt(minCarriers);
      output.writeByte(infectionAlgorithm.id);
      output.writeByte(mutationAlgorithm.id);
      output.writeByte(patchStatsAlgorithm.id);
      output.writeByte(connectivityAlgorithm.id);
      output.writeBoolean(saveState);
   }

   public static SimulationInfo Read(DataInput input, int majorVersion) throws IOException {
      double theta0 = input.readDouble();
      double theta1 = input.readDouble();
      double nt = input.readDouble();
      double omega = input.readDouble();
      double tau = input.readDouble();
      double seasonalityMultiplier = input.readDouble();
      double neighborhoodRadius = input.readDouble();
      double mutationProbability = input.readDouble();
      int numEpitopes = input.readInt();
      int codonsPerEpitope = input.readInt();
      int numPatches = input.readInt();
      int hostsPerPatch = input.readInt();
      int hostLifespan = input.readInt();
      double R0_local = input.readDouble();
      double R0_patch = input.readDouble();
      double R0_global = input.readDouble();
      long randomSeed = input.readLong();
      int numDays = input.readInt();
      int minCarriers = input.readInt();
      Algorithm infectionAlgorithm = Algorithm.Decode(input.readByte() & 0xFF);
      Algorithm mutationAlgorithm = Algorithm.Decode(input.readByte() & 0xFF);
      Algorithm patchStatsAlgorithm = Algorithm.Decode(input.readByte() & 0xFF);
      Algorithm connectivityAlgorithm = Algorithm.Decode(input.readByte() & 0xFF);
      boolean saveState = input.readBoolean();
      return new SimulationInfo(theta0, theta1, nt, omega, tau, randomSeed, seasonalityMultiplier, neighborhoodRadius, mutationProbability, numEpitopes, codonsPerEpitope, numPatches, hostsPerPatch, hostLifespan, R0_local, R0_patch, R0_global, numDays, minCarriers, infectionAlgorithm, mutationAlgorithm, patchStatsAlgorithm, connectivityAlgorithm, saveState);
   }

   public void Print() {
      Main.Print("===== Simulation Parameters =====");
      Main.Print("theta0=" + theta0);
      Main.Print("theta1=" + theta1);
      Main.Print("nt=" + nt);
      Main.Print("omega=" + omega);
      Main.Print("tau=" + tau);
      Main.Print("seasonalityMultiplier=" + seasonalityMultiplier);
      Main.Print("mutationProbability=" + mutationProbability);
      Main.Print("numEpitopes=" + numEpitopes);
      Main.Print("codonsPerEpitope=" + codonsPerEpitope);
      Main.Print("numPatches=" + numPatches);
      Main.Print("hostsPerPatch=" + hostsPerPatch);
      Main.Print("neighborhoodRadius=" + neighborhoodRadius);
      Main.Print("hostLifespan=" + hostLifespan);
      Main.Print("R0_local=" + R0_local);
      Main.Print("R0_patch=" + R0_patch);
      Main.Print("R0_global=" + R0_global);
      Main.Print("randomSeed=%016x", randomSeed);
      Main.Print("numDays=" + numDays);
      Main.Print("minCarriers=" + minCarriers);
      Main.Print("infectionAlgorithm=" + infectionAlgorithm);
      Main.Print("mutationAlgorithm=" + mutationAlgorithm);
      Main.Print("patchStatsAlgorithm=" + patchStatsAlgorithm);
      Main.Print("connectivityAlgorithm=" + connectivityAlgorithm);
      Main.Print("saveState=" + saveState);
      Main.Print("=====-----------------------=====");
   }

   public String ExportBase64String() {
      try {
         //Write the raw data
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos);
         Write(dos);
         dos.flush();
         //XOR with an expected set of simulation parameters
         byte[] bytes = baos.toByteArray();
         baos = new ByteArrayOutputStream();
         dos = new DataOutputStream(baos);
         GetDefault().Write(dos);
         dos.flush();
         byte[] reference = baos.toByteArray();
         for (int i = 0; i < reference.length; i++) {
            bytes[i] = (byte) ((reference[i] & 0xFF) ^ (bytes[i] & 0xFF));
         }
         //Compress the data
         baos = new ByteArrayOutputStream();
         DeflaterOutputStream deflater = new DeflaterOutputStream(baos);
         dos = new DataOutputStream(deflater);
         //Prepend the simulator's major version number as a data format specifier
         dos.writeByte(Main.VERSION_MAJOR);
         dos.write(bytes);
         dos.flush();
         deflater.finish();
         return new BASE64Encoder().encode(baos.toByteArray()).replace("\r", "").replace("\n", "");
      } catch (Exception ex) {
         ex.printStackTrace();
      }
      return null;
   }

   public static SimulationInfo ImportBase64String(String str) {
      try {
         //Decompress the data
         ByteArrayInputStream bais = new ByteArrayInputStream(new BASE64Decoder().decodeBuffer(str));
         InflaterInputStream inflater = new InflaterInputStream(bais);
         DataInputStream dis = new DataInputStream(inflater);
         //Get the data format version
         int majorVersion = dis.read();
         //Undo the XOR operation (with another XOR)
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos);
         GetDefault().Write(dos);
         dos.flush();
         byte[] reference = baos.toByteArray();
         byte[] bytes = new byte[reference.length];
         dis.readFully(bytes);
         for (int i = 0; i < reference.length; i++) {
            bytes[i] = (byte) ((reference[i] & 0xFF) ^ (bytes[i] & 0xFF));
         }
         //Read the raw data
         return Read(new DataInputStream(new ByteArrayInputStream(bytes)), majorVersion);
      } catch (Exception ex) {
         ex.printStackTrace();
         return null;
      }
   }

   public static SimulationInfo GetDefault() {
      return new SimulationInfo(.25, .99, 2, 1, 270, 0, .25, 4, 1e-6, 4, 3, 20, 5000000, 60 * 365, 5, .4, .02, 100 * 365, 1, Algorithm.Infection_Exact, Algorithm.Mutation_Exact, Algorithm.PatchStats_Incidence, Algorithm.Connectivity_Flat, false);
   }
   //========== Ferguson Parameters ==========
   //Long-term, specific immunity
   public double theta0;
   public double theta1;
   public double nt;
   //Short-term, general immunity
   public double omega;
   public double tau;
   //The seasonality multiplier (called "ep" in the paper)
   public double seasonalityMultiplier;
   //The radius that defines a neighborood (called "R" in the paper)
   public double neighborhoodRadius;
   //Probability of mutation per nucleotide (called "delta" in the paper)
   public double mutationProbability;
   //Numer of epitopes on the viral protein(s) (called "A" in the paper)
   public int numEpitopes;
   //Number of codons making up a single epitope (called "C" in the paper)
   public int codonsPerEpitope;
   //Number of patches (called "M" in the paper)
   public int numPatches;
   //Number of hosts per patch (in the paper, "N" = [num patches] * [hosts per patch])
   public int hostsPerPatch;
   //How long each host lives in days (called "L" in the paper)
   public int hostLifespan;
   //R0 of the virus
   public double R0_local;
   public double R0_patch;
   public double R0_global;
   //========== My Nontrivial Parameters ==========
   //Seed of the RNG
   public long randomSeed;
   //Number of days simulated
   public int numDays;
   //The number of hosts to keep infected to prevent extinction
   public int minCarriers;
   //The algorithm to use when choosing local infections
   public Algorithm infectionAlgorithm;
   //The algorithm to use when mutating the virus
   public Algorithm mutationAlgorithm;
   //The connectivity of the patches
   public Algorithm connectivityAlgorithm;
   //========== My Trivial Parameters ==========
   //The algorithm to use when calculating daily patch stats
   public Algorithm patchStatsAlgorithm;
   //Whether or not to save the simulator state at the end of the simulation
   public boolean saveState;
}
