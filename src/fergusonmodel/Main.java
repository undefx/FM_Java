package fergusonmodel;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Calendar;
import stats.*;
import util.LinkedHashSet;
import util.MersenneTwister;
import util.PatchGenerator;

public class Main {

   //Simulator version
   public static final short VERSION_MAJOR = 1;
   public static final short VERSION_MINOR = 1;
   public static final int VERSION = (VERSION_MAJOR << 16) | VERSION_MINOR;
   //The random number generator
   public static MersenneTwister RANDOM;
   //Simulation parameters are loaded at runtime
   public static SimulationInfo simulationInfo;
   //Intrinsic parameters
   public static final double AVERAGE_NEIGHBORS = 12.554;
   public static double LOCAL_INFECTION_PROBABILITY;
   //Precalculated tables
   public static double[][] POISSON_GLOBAL;
   public static double[][] POISSON_PATCH;
   public static double[][] POISSON_LOCAL;
   public static double[] BINOMIAL_STRAIN;
   public static double[] INFECTION_LOCAL;
   //Record keeping
   public static String statsFile;

   public static void main(String[] args) throws Exception {
      String datetime = /* datetime( */ ("2013-02-26 20:39:45") /* ) */;
      long buildNumber = /* increment( */ (1137L) /* ) */;
      System.out.format("Version: %d.%d [Build #%d, %s]\n", VERSION_MAJOR, VERSION_MINOR, buildNumber, datetime);
      if (args.length < 2) {
         System.out.println("Usage:");
         System.out.println("  java -jar FergusonModel.jar -new <base64 settings string>");
         System.out.println("  java -jar FergusonModel.jar -resume <filename> <settings>");
         System.out.println("  java -jar FergusonModel.jar -patch <random seed> <num hosts>");
         return;
      }
      if (args[0].equalsIgnoreCase("-new")) {
         Run(SimulationInfo.ImportBase64String(args[1]), null);
      } else if (args[0].equalsIgnoreCase("-resume")) {
         String filename = args[1];
         StatsReader sr = new StatsReader(filename);
         sr.Initialize();
         SimulationInfo oldInfo = sr.simulationInfo;
         sr.Close();
         SimulationInfo newInfo = SimulationInfo.ImportBase64String(args[2]);
         //Make sure the new parameter set is compatible with the previous simulation
         if (newInfo.codonsPerEpitope != oldInfo.codonsPerEpitope) {
            throw new Exception("codonsPerEpitope mismatch");
         }
         if (newInfo.numEpitopes != oldInfo.numEpitopes) {
            throw new Exception("numEpitopes mismatch");
         }
         if (newInfo.hostsPerPatch != oldInfo.hostsPerPatch) {
            throw new Exception("hostsPerPatch mismatch");
         }
         if (newInfo.numPatches != oldInfo.numPatches) {
            throw new Exception("numPatches mismatch");
         }
         if (newInfo.neighborhoodRadius != oldInfo.neighborhoodRadius) {
            throw new Exception("neighborhoodRadius mismatch");
         }
         //Start a new simulation where the previous one left off
         Run(newInfo, filename);
      } else if (args[0].equalsIgnoreCase("-patch")) {
         PatchGenerator.Run(Long.parseLong(args[1]), Integer.parseInt(args[2]));
      } else {
         throw new Exception("Unknown flag [" + args[0] + "]. Run with no arguments to see usage.");
      }
   }

   public static RuntimeInfo Run(SimulationInfo simulationInfo, String oldFilename) throws Exception {
      //Assign the static simulation info
      Main.simulationInfo = simulationInfo;
      //Setup the environment
      Setup();
      RuntimeInfo runtimeInfo = RuntimeInfo.GenerateRuntimeInfo();
      //Calculate infection probabilities
      InitializeProbabilities();
      //Initialize the scenario
      Print("Creating World. %d patches, %d hosts per patch", simulationInfo.numPatches, simulationInfo.hostsPerPatch);
      World world = new World();
      //Either start a new simulation or resume a previous one
      if (oldFilename != null) {
         //At this point everything is initialized, and there is no infection - try to load the previous state here
         Print("Loading state...");
         StatsReader statsReader = new StatsReader(oldFilename);
         statsReader.Initialize();
         //Check to see if save-state data is present in the file
         if (!statsReader.simulationInfo.saveState) {
            throw new Exception("Can't resume the simulation because the state wasn't saved.");
         }
         //Load the saved state
         DataInput in = statsReader.GetSaveStateInput();
         world.LoadState(in);
         RANDOM.LoadState(in);
         StrainStats.nextStrainID = in.readInt();
         //Reinitialize the RNG if the new seed is different than the previous seed
         if (statsReader.simulationInfo.randomSeed != simulationInfo.randomSeed) {
            RANDOM = new MersenneTwister(simulationInfo.randomSeed);
         }
         runtimeInfo.SetInitialState(statsReader.runtimeInfo.finalState);
         statsReader.Close();
         //Update the world here because an initial stats snapshot is saved before entering the main loop
         Print("Updating world...");
         world.Update();
         Print("Done");
      } else {
         //Make a strain
         Strain strain = new Strain(world.date, Strain.DecodeGenotype("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
         //Infect someone
         Host indexCase = world.patches[RANDOM.nextInt(world.patches.length)].hosts[RANDOM.nextInt(world.patches[0].hosts.length)];
         indexCase.Infect(world.GetDate(), strain);
         //Housekeeping for this infection
         world.knownStrains.Put(strain, new LinkedHashSet<Host>());
         world.knownStrains.Get(strain).Add(indexCase);
         world.patches[indexCase.patchID].stats.exposed++;
         world.patches[indexCase.patchID].stats.naive--;
      }
      //Create and initialize the stats file
      statsFile = "stats-" + System.currentTimeMillis() + ".bin";
      Print("Saving stats to file [%s]", statsFile);
      StatsWriter stats = new StatsWriter(statsFile, simulationInfo, runtimeInfo);
      stats.Initialize();
      //Note the first day of the simulation
      int worldStartDate = world.date;
      //Save the first day before calling World.Update
      world.UpdateStats(stats, worldStartDate);
      //Run the simulation
      long lastOutputTime = 0;
      int lastWorldDate = world.date;
      while (world.date - worldStartDate < simulationInfo.numDays - 1) {
         //Sanity check
         if (simulationInfo.minCarriers > 0 && world.knownStrains.GetSize() == 0) {
            throw new Exception("The virus has gone extinct! Date: " + world.GetDateString() + " (" + world.GetDate() + ")");
         }
         //Run this day
         world.Update();
         //Record the result
         world.UpdateStats(stats, worldStartDate);
         //Periodically print to screen
         long time = System.currentTimeMillis();
         if (time >= lastOutputTime + 10000) {
            int date = world.GetDate();
            Print("Date: %s | Days/Sec: %.1f", world.GetDateString(), (date - lastWorldDate) / Math.max(1.0, (time - lastOutputTime) / 1000.0));
            lastWorldDate = date;
            lastOutputTime = time;
         }
      }
      //Save the internal state of the simulator so the simulation can be resumed later
      if (simulationInfo.saveState) {
         Print("Saving state...");
         DataOutput out = stats.GetSaveStateOutput();
         world.SaveState(out);
         RANDOM.SaveState(out);
         out.writeInt(StrainStats.nextStrainID);
         Print("Done");
      }
      //Create a "hash" to uniquely identify this trajectory
      long random = RANDOM.nextLong();
      long date = ((long) world.GetDate()) << 32;
      long sick = world.GetTotalSick();
      long finalStateHash = random ^ (date | sick);
      Print("Final state: %016x", finalStateHash);
      //Save the final state hash
      runtimeInfo.SetFinalState(finalStateHash);
      //Close the stats file
      stats.Close();
      return runtimeInfo;
   }

   public static void Setup() throws Exception {
      //Sanity checking / parameter validation
      switch (simulationInfo.infectionAlgorithm) {
         case Infection_Approximate1:
         case Infection_Approximate2:
         case Infection_Approximate3:
         case Infection_Exact:
            break;
         default:
            throw new Exception("Invalid infection algorithm: " + simulationInfo.infectionAlgorithm);
      }
      switch (simulationInfo.mutationAlgorithm) {
         case Mutation_Approximate:
         case Mutation_Exact:
         case Mutation_None:
            break;
         default:
            throw new Exception("Invalid mutation algorithm: " + simulationInfo.mutationAlgorithm);
      }
      switch (simulationInfo.connectivityAlgorithm) {
         case Connectivity_Flat:
            if (simulationInfo.numPatches % 2 != 0) {
               throw new Exception("With Connectivity_Flat, numPatches must be a multiple of 2: " + simulationInfo.numPatches);
            }
            break;
         case Connectivity_Cylindrical:
            if (simulationInfo.numPatches % 4 != 0) {
               throw new Exception("With Connectivity_Cylindrical, numPatches must be a multiple of 4: " + simulationInfo.numPatches);
            }
            break;
         case Connectivity_Tropics:
            if (simulationInfo.numPatches % 10 != 0) {
               throw new Exception("With Connectivity_Tropics, numPatches must be a multiple of 10: " + simulationInfo.numPatches);
            }
            break;
         default:
            throw new Exception("Invalid connectivity algorithm: " + simulationInfo.connectivityAlgorithm);
      }
      //Make sure the neighborhood radius is set to 4 (there are hardcoded values that depend on this assumption)
      if (simulationInfo.neighborhoodRadius != 4.0) {
         throw new Exception("neighborhoodRadius must be set to 4, but it is currently set to [" + simulationInfo.neighborhoodRadius + "].");
      }
      //Make sure the number of epitopes is set to 4 (there are hardcoded values that depend on this assumption)
      if (simulationInfo.numEpitopes != 4) {
         throw new Exception("numEpitopes must be set to 4, but it is currently set to [" + simulationInfo.numEpitopes + "].");
      }
      //Make sure the number of codons per epitope is set to 3 (there are hardcoded values that depend on this assumption)
      if (simulationInfo.codonsPerEpitope != 3) {
         throw new Exception("codonsPerEpitope must be set to 3, but it is currently set to [" + simulationInfo.codonsPerEpitope + "].");
      }
      //Make sure the other parameters are in their acceptable ranges
      if (simulationInfo.numPatches < 0) {
         throw new Exception("numPatches can't be negative: " + simulationInfo.numPatches);
      }
      if (simulationInfo.hostsPerPatch < 0) {
         throw new Exception("hostsPerPatch can't be negative: " + simulationInfo.hostsPerPatch);
      }
      if (simulationInfo.hostLifespan <= 0) {
         throw new Exception("hostLifespan must be positive: " + simulationInfo.hostLifespan);
      }
      if (simulationInfo.tau < 0) {
         throw new Exception("tau can't be negative: " + simulationInfo.tau);
      }
      if (simulationInfo.nt < 0) {
         throw new Exception("nt can't be negative: " + simulationInfo.nt);
      }
      if (simulationInfo.R0_local < 0) {
         throw new Exception("R0_local can't be negative: " + simulationInfo.R0_local);
      }
      if (simulationInfo.R0_patch < 0) {
         throw new Exception("R0_patch can't be negative: " + simulationInfo.R0_patch);
      }
      if (simulationInfo.R0_global < 0) {
         throw new Exception("R0_global can't be negative: " + simulationInfo.R0_global);
      }
      if (simulationInfo.minCarriers < 0) {
         throw new Exception("minCarriers can't be negative: " + simulationInfo.minCarriers);
      }
      if (simulationInfo.seasonalityMultiplier < 0) {
         throw new Exception("seasonalityMultiplier can't be negative: " + simulationInfo.seasonalityMultiplier);
      }
      if (simulationInfo.mutationProbability < 0) {
         throw new Exception("mutationProbability can't be negative: " + simulationInfo.mutationProbability);
      }
      if (simulationInfo.omega < 0 || simulationInfo.omega > 1) {
         throw new Exception("omega must be between 0 and 1: " + simulationInfo.omega);
      }
      if (simulationInfo.theta0 < 0 || simulationInfo.theta0 > 1) {
         throw new Exception("theta0 must be between 0 and 1: " + simulationInfo.theta0);
      }
      if (simulationInfo.theta1 < 0 || simulationInfo.theta1 > 1) {
         throw new Exception("theta1 must be between 0 and 1: " + simulationInfo.theta1);
      }
      if (simulationInfo.numDays <= 0) {
         throw new Exception("numDays must be at least 1: " + simulationInfo.numDays);
      }
      //Randomize the seed (unless otherwise specified)
      if (simulationInfo.randomSeed == 0) {
         simulationInfo.randomSeed = System.currentTimeMillis();
      }
      //Initialize the RNG
      RANDOM = new MersenneTwister(simulationInfo.randomSeed);
      //Calculate the infection probability
      LOCAL_INFECTION_PROBABILITY = simulationInfo.R0_local / AVERAGE_NEIGHBORS / 4.0; //R0 * 1/num_neighbors * 1/infection_duration
      //Reset the Strain ID counter
      StrainStats.nextStrainID = 0;
      //Print the current set of parameters just to be thorough
      simulationInfo.Print();
   }

   public static int DrawFromDistribution(double[] cdf, double rand) {
      for (int i = 0; i < cdf.length; i++) {
         if (cdf[i] >= rand) {
            return i;
         }
      }
      Print("Warning: DrawFromDistribution cutoff reached.");
      Print("d=" + rand);
      Print("cdf[%d]=" + cdf[cdf.length - 1], cdf.length - 1);
      return cdf.length;
   }

   public static void InitializeProbabilities() {
      //Poisson probability for number of hosts to infect
      double infectionDays = 4;
      //Adjusted because the paper says "R0 of 0.02 between any two patches"
      double R0_global = simulationInfo.R0_global * (simulationInfo.numPatches - 1);
      double lambda1 = simulationInfo.R0_local / infectionDays, lambda2 = simulationInfo.R0_patch / infectionDays, lambda3 = R0_global / infectionDays;
      POISSON_GLOBAL = new double[365][20];
      POISSON_PATCH = new double[365][20];
      POISSON_LOCAL = new double[365][20];
      for (int day = 0; day < 365; day++) {
         //Using cosine so the peak is January 1
         double l3_seasonality = lambda3 * (1 + simulationInfo.seasonalityMultiplier * Math.cos((double) day / 365.0 * Math.PI * 2.0));
         double l2_seasonality = lambda2 * (1 + simulationInfo.seasonalityMultiplier * Math.cos((double) day / 365.0 * Math.PI * 2.0));
         double l1_seasonality = lambda1 * (1 + simulationInfo.seasonalityMultiplier * Math.cos((double) day / 365.0 * Math.PI * 2.0));
         POISSON_GLOBAL[day][0] = Math.pow(Math.E, -l3_seasonality);
         POISSON_PATCH[day][0] = Math.pow(Math.E, -l2_seasonality);
         POISSON_LOCAL[day][0] = Math.pow(Math.E, -l1_seasonality);
         double factorial = 1;
         for (int k = 1; k < POISSON_GLOBAL[day].length; k++) {
            factorial *= k;
            POISSON_GLOBAL[day][k] = POISSON_GLOBAL[day][k - 1] + Math.pow(l3_seasonality, k) * Math.pow(Math.E, -l3_seasonality) / factorial;
            POISSON_PATCH[day][k] = POISSON_PATCH[day][k - 1] + Math.pow(l2_seasonality, k) * Math.pow(Math.E, -l2_seasonality) / factorial;
            POISSON_LOCAL[day][k] = POISSON_LOCAL[day][k - 1] + Math.pow(l1_seasonality, k) * Math.pow(Math.E, -l1_seasonality) / factorial;
         }
      }
      //Binomial probability for mutations in the nucleic acid sequence of the virus
      BINOMIAL_STRAIN = new double[simulationInfo.numEpitopes * simulationInfo.codonsPerEpitope * 3 + 1];
      double p = simulationInfo.mutationProbability;
      int n = BINOMIAL_STRAIN.length - 1;
      for (int k = 0; k <= n; k++) {
         double previous = (k == 0 ? 0 : BINOMIAL_STRAIN[k - 1]);
         double d = Choose(n, k) * Math.pow(p, k) * Math.pow(1 - p, n - k);
         BINOMIAL_STRAIN[k] = Math.min(1, previous + d);
      }
      //Probability of infecting people in a neighborhood, mirroring Anuroop's implementation
      //Using cosine so the peak is January 1
      //Only used by algorithms Infection_Approximate2 and Infection_Approximate3
      INFECTION_LOCAL = new double[365];
      double beta = -Math.log(1 - LOCAL_INFECTION_PROBABILITY);
      for (int day = 0; day < 365; day++) {
         INFECTION_LOCAL[day] = 1 - Math.exp(-(1 + simulationInfo.seasonalityMultiplier * Math.cos((double) day / 365.0 * Math.PI * 2.0)) * beta);
      }
   }

   private static double Choose(int n, int k) {
      return Factorial(n) / (Factorial(k) * Factorial(n - k));
   }

   private static double Factorial(int a) {
      double b = 1;
      while (a > 0) {
         b *= a--;
      }
      return b;
   }

   public static void Print(String format, Object... args) {
      Calendar cal = Calendar.getInstance();
      String time = String.format("%04d/%02d/%02d %02d:%02d:%02d:%03d",
              cal.get(Calendar.YEAR),
              cal.get(Calendar.MONTH) + 1,
              cal.get(Calendar.DAY_OF_MONTH),
              cal.get(Calendar.HOUR_OF_DAY),
              cal.get(Calendar.MINUTE),
              cal.get(Calendar.SECOND),
              cal.get(Calendar.MILLISECOND));
      System.out.printf("[%s] %s\n", time, String.format(format, args));
   }
}
